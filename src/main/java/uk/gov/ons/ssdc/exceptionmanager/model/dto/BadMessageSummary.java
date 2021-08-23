package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import java.time.Instant;
import java.util.Set;
import lombok.Data;

@Data
public class BadMessageSummary {
  private String messageHash;
  private Instant firstSeen;
  private Instant lastSeen;
  private int seenCount;
  private Set<String> affectedServices;
  private Set<String> affectedSubscriptions;
  private boolean quarantined;
}
