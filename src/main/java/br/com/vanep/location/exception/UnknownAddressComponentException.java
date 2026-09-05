package br.com.vanep.location.exception;

import java.util.List;

public class UnknownAddressComponentException extends RuntimeException {
  private final String componentName;
  private final List<String> types;

  public UnknownAddressComponentException(String componentName, List<String> types) {
    super("Componente de endereço com types não mapeados: " + componentName + " " + types);
    this.componentName = componentName;
    this.types = List.copyOf(types);
  }

  public String getComponentName() {
    return componentName;
  }

  public List<String> getTypes() {
    return types;
  }
}
