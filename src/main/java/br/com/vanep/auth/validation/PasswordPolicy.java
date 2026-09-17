package br.com.vanep.auth.validation;

import java.util.regex.Pattern;

/**
 * Character requirements every new password must meet. The minimum length stays on {@code @Size} so
 * its message keeps the configured number; the native app mirrors these same two rules.
 */
public final class PasswordPolicy {

  private static final Pattern UPPERCASE_LETTER = Pattern.compile("\\p{Lu}");
  private static final Pattern SPECIAL_CHARACTER = Pattern.compile("[^\\p{L}\\p{N}\\s]");

  private PasswordPolicy() {}

  public static boolean hasUppercaseLetter(String password) {
    return UPPERCASE_LETTER.matcher(password).find();
  }

  public static boolean hasSpecialCharacter(String password) {
    return SPECIAL_CHARACTER.matcher(password).find();
  }
}
