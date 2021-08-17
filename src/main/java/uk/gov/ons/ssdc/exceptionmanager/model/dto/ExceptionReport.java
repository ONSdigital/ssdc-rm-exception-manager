package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class ExceptionReport {
  private String messageHash;
  private String service;
  private String subscription;
  private String exceptionClass;
  private String exceptionMessage;
  private String exceptionRootCause;
}
