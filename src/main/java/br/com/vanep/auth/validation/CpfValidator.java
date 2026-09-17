package br.com.vanep.auth.validation;

public final class CpfValidator {

  private CpfValidator() {}

  public static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replaceAll("\\D", "");
  }

  public static boolean isValid(String raw) {
    String digits = normalize(raw);
    if (digits.length() != 11) {
      return false;
    }
    if (digits.chars().distinct().count() == 1) {
      return false;
    }
    return checkDigit(digits, 9) == digitAt(digits, 9)
        && checkDigit(digits, 10) == digitAt(digits, 10);
  }

  private static int checkDigit(String digits, int length) {
    int sum = 0;
    int weight = length + 1;
    for (int i = 0; i < length; i++) {
      sum += digitAt(digits, i) * (weight - i);
    }
    int remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  }

  private static int digitAt(String digits, int index) {
    return digits.charAt(index) - '0';
  }
}
