package br.com.vanep.cep.exception;

import java.io.Serial;

public class ViaCepNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String cep;

  public ViaCepNotFoundException(String cep) {
    super("CEP not found in ViaCEP: " + cep);
    this.cep = cep;
  }

  public String getCep() {
    return cep;
  }
}
