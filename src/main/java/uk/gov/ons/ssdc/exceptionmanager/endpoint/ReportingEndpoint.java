package uk.gov.ons.ssdc.exceptionmanager.endpoint;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.ons.ssdc.exceptionmanager.helper.JsonHelper;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.ExceptionReport;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.Peek;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.Response;
import uk.gov.ons.ssdc.exceptionmanager.model.dto.SkippedMessage;
import uk.gov.ons.ssdc.exceptionmanager.model.entity.AutoQuarantineRule;
import uk.gov.ons.ssdc.exceptionmanager.model.entity.QuarantinedMessage;
import uk.gov.ons.ssdc.exceptionmanager.model.repository.QuarantinedMessageRepository;
import uk.gov.ons.ssdc.exceptionmanager.persistence.CachingDataStore;

@RestController
public class ReportingEndpoint {
  private final CachingDataStore cachingDataStore;
  private final QuarantinedMessageRepository quarantinedMessageRepository;

  public ReportingEndpoint(
      CachingDataStore cachingDataStore,
      QuarantinedMessageRepository quarantinedMessageRepository) {
    this.cachingDataStore = cachingDataStore;
    this.quarantinedMessageRepository = quarantinedMessageRepository;
  }

  @PostMapping(path = "/reportexception")
  public ResponseEntity<Response> reportError(@RequestBody ExceptionReport exceptionReport) {
    Response result = new Response();
    String messageHash = exceptionReport.getMessageHash();

    boolean forceSkip = false;
    boolean shouldLog = true;
    List<AutoQuarantineRule> matchingRules = cachingDataStore.findMatchingRules(exceptionReport);
    for (AutoQuarantineRule rule : matchingRules) {
      if (rule.isThrowAway()) {
        forceSkip = true;
        shouldLog = false;
        result.setThrowAway(true); // Don't log, don't quarantine... completely silent
      }

      if (rule.isSuppressLogging()) {
        shouldLog = false;
      }

      if (rule.isQuarantine()) {
        forceSkip = true;
      }
    }

    result.setSkipIt(forceSkip || cachingDataStore.shouldWeSkipThisMessage(exceptionReport));
    result.setPeek(cachingDataStore.shouldWePeekThisMessage(messageHash));
    result.setLogIt(shouldLog && cachingDataStore.shouldWeLogThisMessage(exceptionReport));

    cachingDataStore.updateStats(exceptionReport);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @PostMapping(path = "/peekreply")
  public void peekReply(@RequestBody Peek peekReply) {
    cachingDataStore.storePeekMessageReply(peekReply);
  }

  @Transactional
  @PostMapping(path = "/storeskippedmessage")
  public void storeSkippedMessage(@RequestBody SkippedMessage skippedMessage) {
    cachingDataStore.storeSkippedMessage(skippedMessage);
    String errorReports =
        JsonHelper.convertObjectToJson(
            cachingDataStore.getBadMessageReports(skippedMessage.getMessageHash()));

    String originatingUserOfSkipRequest =
        cachingDataStore.getOriginatingUserOfSkipRequest(skippedMessage.getMessageHash());

    QuarantinedMessage quarantinedMessage = new QuarantinedMessage();
    quarantinedMessage.setId(UUID.randomUUID());
    quarantinedMessage.setContentType(skippedMessage.getContentType());
    quarantinedMessage.setHeaders(skippedMessage.getHeaders());
    quarantinedMessage.setMessageHash(skippedMessage.getMessageHash());
    quarantinedMessage.setMessagePayload(skippedMessage.getMessagePayload());
    quarantinedMessage.setSubscription(skippedMessage.getSubscription());
    quarantinedMessage.setRoutingKey(skippedMessage.getRoutingKey());
    quarantinedMessage.setService(skippedMessage.getService());
    quarantinedMessage.setErrorReports(errorReports);
    quarantinedMessage.setSkippingUser(originatingUserOfSkipRequest);

    quarantinedMessageRepository.save(quarantinedMessage);
  }
}
