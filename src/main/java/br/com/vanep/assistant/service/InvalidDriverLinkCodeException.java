package br.com.vanep.assistant.service;

public class InvalidDriverLinkCodeException extends RuntimeException {

  public InvalidDriverLinkCodeException(String message) {
    super(message);
  }
}
