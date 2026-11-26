package br.com.vanep.job.handler;

import java.util.Optional;

public class JobRetryException extends RuntimeException {

  private final transient Object replacementPayload;

  public JobRetryException(String message, Object replacementPayload) {
    super(message);
    this.replacementPayload = replacementPayload;
  }

  public Optional<Object> replacementPayload() {
    return Optional.ofNullable(replacementPayload);
  }
}
