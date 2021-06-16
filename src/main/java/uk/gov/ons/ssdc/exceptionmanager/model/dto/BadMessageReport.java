package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import lombok.Data;

@Data
public class BadMessageReport {
  private ExceptionReport exceptionReport;
  private ExceptionStats stats;
}
