package br.com.vanep.cep.exception;

public class ViaCepNotFoundException extends RuntimeException {
  private final String cep;

  public ViaCepNotFoundException(String cep) {
    super("CEP not found in ViaCEP: " + cep);
    this.cep = cep;
  }

  public String getCep() {
    return cep;
  }
}
