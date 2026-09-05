package br.com.vanep.location;

import java.text.Normalizer;
import java.util.Locale;

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
