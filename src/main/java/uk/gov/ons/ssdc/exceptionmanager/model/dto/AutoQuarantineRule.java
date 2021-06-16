package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class AutoQuarantineRule {
  private String expression;
  private boolean suppressLogging;
  private boolean quarantine;
  private boolean throwAway;
  private OffsetDateTime ruleExpiryDateTime;
}
