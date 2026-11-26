package br.com.vanep.job.handler;

public class PermanentJobFailureException extends RuntimeException {

  public PermanentJobFailureException(String message) {
    super(message);
  }

  public PermanentJobFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
