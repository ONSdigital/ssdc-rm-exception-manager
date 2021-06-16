package uk.gov.ons.ssdc.exceptionmanager.model.dto;

import lombok.Data;

@Data
public class Response {
  private boolean peek = false;
  private boolean logIt = false;
  private boolean skipIt = false;
  private boolean throwAway = false; // Don't log, don't quarantine
}
