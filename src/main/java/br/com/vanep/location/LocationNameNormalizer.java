package br.com.vanep.location;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza nomes de lugares para a forma usada em comparações e índices únicos da árvore
 * geográfica.
 *
 * <p>É o que faz o nó escolhido pelo motorista e o nó derivado do endereço do cliente caírem na
 * mesma linha: os dois lados atravessam esta normalização antes de qualquer busca ou gravação.
 * Divergência de grafia vinda do Google ("Águas Claras" x "AGUAS CLARAS") criaria nós irmãos
 * duplicados sem ela.
 */
public final class LocationNameNormalizer {

  private static final java.util.regex.Pattern COMBINING_MARKS =
      java.util.regex.Pattern.compile("\\p{M}+");

  private static final java.util.regex.Pattern REPEATED_WHITESPACE =
      java.util.regex.Pattern.compile("\\s+");

  private LocationNameNormalizer() {}

  public static String normalize(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Nome de lugar não pode ser nulo ou vazio.");
    }
    String decomposed = Normalizer.normalize(name, Normalizer.Form.NFD);
    String withoutAccents = COMBINING_MARKS.matcher(decomposed).replaceAll("");
    String collapsed = REPEATED_WHITESPACE.matcher(withoutAccents.trim()).replaceAll(" ");
    return collapsed.toLowerCase(Locale.ROOT);
  }
}
