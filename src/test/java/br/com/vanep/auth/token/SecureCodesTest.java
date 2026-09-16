package br.com.vanep.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.auth.enums.AuthCodePurpose;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SecureCodesTest {

  private final SecureCodes codes = new SecureCodes("pepper-for-tests");

  @Test
  void generatesSixDigitCodesKeepingLeadingZeros() {
    IntStream.range(0, 500)
        .forEach(
            attempt -> {
              String code = codes.generate();
              assertThat(code).hasSize(6).containsOnlyDigits();
            });
  }

  @Test
  void hmacIsDeterministicForTheSameTriple() {
    String first = codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "012345");
    String second = codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "012345");

    assertThat(first).isEqualTo(second);
  }

  @Test
  void hmacDiffersByUserPurposeAndCode() {
    String reference = codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "012345");

    assertThat(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 8L, "012345"))
        .isNotEqualTo(reference);
    assertThat(codes.hmac(AuthCodePurpose.PASSWORD_RESET, 7L, "012345")).isNotEqualTo(reference);
    assertThat(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "543210"))
        .isNotEqualTo(reference);
  }

  @Test
  void hmacDependsOnThePepper() {
    SecureCodes other = new SecureCodes("another-pepper");

    assertThat(other.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "012345"))
        .isNotEqualTo(codes.hmac(AuthCodePurpose.EMAIL_VERIFICATION, 7L, "012345"));
  }

  @Test
  void matchesOnlyTheCodeBehindTheStoredHash() {
    String stored = codes.hmac(AuthCodePurpose.PASSWORD_RESET, 3L, "000042");

    assertThat(codes.matches(stored, AuthCodePurpose.PASSWORD_RESET, 3L, "000042")).isTrue();
    assertThat(codes.matches(stored, AuthCodePurpose.PASSWORD_RESET, 3L, "000043")).isFalse();
    assertThat(codes.matches(null, AuthCodePurpose.PASSWORD_RESET, 3L, "000042")).isFalse();
  }
}
