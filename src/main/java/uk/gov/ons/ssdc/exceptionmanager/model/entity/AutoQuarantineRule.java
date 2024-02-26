package uk.gov.ons.ssdc.exceptionmanager.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
public class AutoQuarantineRule {
  @Id private UUID id;

  @Column private String expression;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean suppressLogging;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean quarantine;

  @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
  private boolean throwAway;

  @Column(columnDefinition = "timestamp with time zone")
  private OffsetDateTime ruleExpiryDateTime;
}
