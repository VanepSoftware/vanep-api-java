package br.com.vanep.location.exception;

import java.util.List;

/**
 * Um componente do Google trouxe {@code types} que não estão na tabela D11 nem na lista de
 * ignorados.
 *
 * <p>Falhar aqui é deliberado. Ignorar o componente deixaria a árvore torta e a busca sem
 * resultado, sem erro nenhum — o silêncio é o que torna o R1 um risco alto. Um erro visível é a
 * única mitigação que ataca isso. Quando aparecer em produção, significa que a praça nova precisa
 * de fixtures próprias e de uma revisão da D11.
 */
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
