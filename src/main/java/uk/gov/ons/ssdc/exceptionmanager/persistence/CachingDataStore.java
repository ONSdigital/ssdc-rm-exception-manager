package uk.gov.ons.ssdc.exceptionmanager.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.BadMessageReport;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.ExceptionStats;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.Peek;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.ssdc.exceptionmanager.model.entity.AutoQuarantineRule;
import uk.gov.ons.ssdc.exceptionmanager.model.repository.AutoQuarantineRuleRepository;

@Component
public class CachingDataStore {
  private static final Logger log = LoggerFactory.getLogger(CachingDataStore.class);
  private Map<ExceptionReport, ExceptionStats> seenExceptions = new ConcurrentHashMap<>();
  private Map<String, List<ExceptionReport>> messageExceptionReports = new ConcurrentHashMap<>();
  private Map<String, String> messagesToSkipAndSkippingUser = new ConcurrentHashMap<>();
  private Set<String> messagesToPeek = ConcurrentHashMap.newKeySet();
  private Map<String, byte[]> peekedMessages = new ConcurrentHashMap<>();
  private Map<String, List<SkippedMessage>> skippedMessages = new ConcurrentHashMap<>();
  private Map<AutoQuarantineRule, Expression> autoQuarantineExpressions = new ConcurrentHashMap<>();
  private final AutoQuarantineRuleRepository quarantineRuleRepository;
  private final int numberOfRetriesBeforeLogging;

  public CachingDataStore(
      AutoQuarantineRuleRepository quarantineRuleRepository,
      @Value("${general-config.number-of-retries-before-logging}")
          int numberOfRetriesBeforeLogging) {
    this.quarantineRuleRepository = quarantineRuleRepository;
    this.numberOfRetriesBeforeLogging = numberOfRetriesBeforeLogging;

    List<AutoQuarantineRule> autoQuarantineRules = quarantineRuleRepository.findAll();

    for (AutoQuarantineRule rule : autoQuarantineRules) {
      ExpressionParser expressionParser = new SpelExpressionParser();
      Expression spelExpression = expressionParser.parseExpression(rule.getExpression());
      autoQuarantineExpressions.put(rule, spelExpression);
    }
  }

  public synchronized void updateStats(ExceptionReport exceptionReport) {
    String messageHash = exceptionReport.getMessageHash();

    ExceptionStats exceptionStats = seenExceptions.get(exceptionReport);

    if (exceptionStats != null) {
      exceptionStats.getSeenCount().incrementAndGet();
      exceptionStats.setLastSeen(Instant.now());
      return;
    }

    seenExceptions.put(exceptionReport, new ExceptionStats());

    List<ExceptionReport> exceptionReportList =
        messageExceptionReports.computeIfAbsent(messageHash, key -> new LinkedList<>());
    exceptionReportList.add(exceptionReport);
  }

  public boolean shouldWeLogThisMessage(ExceptionReport exceptionReport) {
    ExceptionStats exceptionStats = seenExceptions.get(exceptionReport);

    if (numberOfRetriesBeforeLogging > 0) {
      // Don't log until we've seen the exception a [configurable] number of times
      if (exceptionStats != null
          && !exceptionStats.isLoggedAtLeastOnce()
          && exceptionStats.getSeenCount().get() > numberOfRetriesBeforeLogging - 1) {
        exceptionStats.setLoggedAtLeastOnce(true);
        return true;
      }

      return false;
    } else {
      return exceptionStats == null;
    }
  }

  public boolean shouldWeSkipThisMessage(ExceptionReport exceptionReport) {
    return messagesToSkipAndSkippingUser.containsKey(exceptionReport.getMessageHash());
  }

  public List<AutoQuarantineRule> findMatchingRules(ExceptionReport exceptionReport) {
    List<AutoQuarantineRule> matchingRules = new LinkedList<>();

    EvaluationContext context = new StandardEvaluationContext(exceptionReport);
    for (Entry<AutoQuarantineRule, Expression> ruleExpressionEntry :
        autoQuarantineExpressions.entrySet()) {
      Expression expression = ruleExpressionEntry.getValue();
      AutoQuarantineRule autoQuarantineRule = ruleExpressionEntry.getKey();

      if (OffsetDateTime.now().isAfter(autoQuarantineRule.getRuleExpiryDateTime())) {
        continue; // This rule has expired, so it can not match
      }

      try {
        Boolean expressionResult = expression.getValue(context, Boolean.class);
        if (expressionResult != null && expressionResult) {

          if (!autoQuarantineRule.isSuppressLogging() && !autoQuarantineRule.isThrowAway()) {
            log.atWarn()
                .setMessage("Auto-quarantine message rule matched")
                .addKeyValue("expression", expression.getExpressionString())
                .addKeyValue("exception_report", exceptionReport)
                .log();
          }

          matchingRules.add(autoQuarantineRule);
        }
      } catch (Exception e) {
        log.atWarn()
            .setMessage("Auto-quarantine rule is causing errors")
            .setCause(e)
            .addKeyValue("expression", expression.getExpressionString())
            .addKeyValue("exception_report", exceptionReport)
            .log();
      }
    }

    return matchingRules;
  }

  public boolean isQuarantined(String messageHash) {
    return messagesToSkipAndSkippingUser.containsKey(messageHash);
  }

  public boolean shouldWePeekThisMessage(String messageHash) {
    return messagesToPeek.contains(messageHash);
  }

  public Set<String> getSeenMessageHashes() {
    return messageExceptionReports.keySet();
  }

  public int getSeenMessageCount() {
    return messageExceptionReports.keySet().size();
  }

  public Set<String> getSeenMessageHashes(int minimumSeenCount) {
    Set<String> result = new HashSet<>();

    for (Entry<ExceptionReport, ExceptionStats> item : seenExceptions.entrySet()) {
      if (item.getValue().getSeenCount().get() >= minimumSeenCount) {
        result.add(item.getKey().getMessageHash());
      }
    }

    return result;
  }

  public void skipMessage(String messageHash, String originatingUser) {
    messagesToSkipAndSkippingUser.put(messageHash, originatingUser);
  }

  public void peekMessage(String messageHash) {
    messagesToPeek.add(messageHash);
  }

  public synchronized void storePeekMessageReply(Peek peekReply) {
    peekedMessages.put(peekReply.getMessageHash(), peekReply.getMessagePayload());

    // We don't want services to keep sending us the 'peek'ed message now we've got it
    messagesToPeek.remove(peekReply.getMessageHash());
  }

  public synchronized void storeSkippedMessage(SkippedMessage skippedMessage) {
    // Make damn certain this is thread safe so we don't lose anything
    List<SkippedMessage> skippedMessageList =
        skippedMessages.computeIfAbsent(skippedMessage.getMessageHash(), key -> new LinkedList<>());
    skippedMessageList.add(skippedMessage);
  }

  public byte[] getPeekedMessage(String messageHash) {
    return peekedMessages.get(messageHash);
  }

  public List<BadMessageReport> getBadMessageReports(String messageHash) {
    List<BadMessageReport> results = new LinkedList<>();
    List<ExceptionReport> exceptionReportList = messageExceptionReports.get(messageHash);

    if (exceptionReportList == null) {
      return Collections.emptyList();
    }

    for (ExceptionReport exceptionReport : exceptionReportList) {
      BadMessageReport badMessageReport = new BadMessageReport();
      badMessageReport.setExceptionReport(exceptionReport);
      badMessageReport.setStats(seenExceptions.get(exceptionReport));
      results.add(badMessageReport);
    }

    return results;
  }

  public Map<String, List<SkippedMessage>> getAllSkippedMessages() {
    return skippedMessages;
  }

  public List<SkippedMessage> getSkippedMessages(String messageHash) {
    return skippedMessages.get(messageHash);
  }

  public void reset(Optional<Integer> resetOldMessages) {

    if (resetOldMessages.isPresent()) {
      for (Entry<ExceptionReport, ExceptionStats> item : seenExceptions.entrySet()) {
        Instant timeCutoff = Instant.now().minusSeconds(resetOldMessages.get());

        if (timeCutoff.isAfter(item.getValue().getLastSeen())) {
          seenExceptions.remove(item.getKey());
          messageExceptionReports.remove(item.getKey().getMessageHash());
        }
      }
    } else {
      seenExceptions.clear();
      messageExceptionReports.clear();
      messagesToSkipAndSkippingUser.clear();
      messagesToPeek.clear();
      peekedMessages.clear();
    }
  }

  public void addQuarantineRuleExpression(
      String expression,
      boolean doNotLog,
      boolean quarantine,
      boolean throwAway,
      OffsetDateTime ruleExpiryDateTime) {
    ExpressionParser expressionParser = new SpelExpressionParser();
    Expression spelExpression = expressionParser.parseExpression(expression);

    AutoQuarantineRule autoQuarantineRule = new AutoQuarantineRule();
    autoQuarantineRule.setId(UUID.randomUUID());
    autoQuarantineRule.setExpression(expression);
    autoQuarantineRule.setSuppressLogging(doNotLog);
    autoQuarantineRule.setQuarantine(quarantine);
    autoQuarantineRule.setThrowAway(throwAway);
    autoQuarantineRule.setRuleExpiryDateTime(ruleExpiryDateTime);

    quarantineRuleRepository.saveAndFlush(autoQuarantineRule);

    autoQuarantineExpressions.put(autoQuarantineRule, spelExpression);
  }

  public List<AutoQuarantineRule> getQuarantineRules() {
    return quarantineRuleRepository.findAll();
  }

  public void deleteQuarantineRule(String id) {
    quarantineRuleRepository.deleteById(UUID.fromString(id));
    List<AutoQuarantineRule> rules = quarantineRuleRepository.findAll();
    autoQuarantineExpressions.clear();
    for (AutoQuarantineRule rule : rules) {
      ExpressionParser expressionParser = new SpelExpressionParser();
      Expression spelExpression = expressionParser.parseExpression(rule.getExpression());
      autoQuarantineExpressions.put(rule, spelExpression);
    }
  }

  public String getOriginatingUserOfSkipRequest(String messageHash) {
    // Default skipping user to "null" if we don't know who skipped because of race conditions
    return messagesToSkipAndSkippingUser.getOrDefault(messageHash, null);
  }
}
