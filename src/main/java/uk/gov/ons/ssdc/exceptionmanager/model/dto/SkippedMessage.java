package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import java.time.Instant;
import java.util.Map;
import lombok.Data;

@Data
public class SkippedMessage {
  private Instant skippedTimestamp = Instant.now();
  private String messageHash;
  private byte[] messagePayload;
  private String service;
  private String subscription;
  private String routingKey;
  private String contentType;
  private Map headers;
}
