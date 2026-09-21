package br.com.vanep.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MimeTypeDetectorTest {

  private final MimeTypeDetector detector = new MimeTypeDetector();

  private byte[] bytes(int... values) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int value : values) {
      out.write(value);
    }
    return out.toByteArray();
  }

  @Test
  void recognisesJpeg() {
    assertThat(detector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10))).contains("image/jpeg");
  }

  @Test
  void recognisesPng() {
    assertThat(detector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        .contains("image/png");
  }

  @Test
  void recognisesWebp() {
    assertThat(
            detector.detect(
                bytes(
                    0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50, 0x00)))
        .contains("image/webp");
  }

  @Test
  void recognisesPdf() {
    assertThat(detector.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)))
        .contains("application/pdf");
  }

  @Test
  void refusesAnExecutableEvenThoughItWouldBeNamedAsAnImage() {
    assertThat(detector.detect(bytes(0x4D, 0x5A, 0x90, 0x00))).isEmpty();
  }

  @Test
  void refusesPlainTextAndOtherUnknownContent() {
    assertThat(detector.detect("apenas texto".getBytes(StandardCharsets.UTF_8))).isEmpty();
    assertThat(detector.detect(bytes(0x00, 0x01, 0x02, 0x03))).isEmpty();
  }

  @Test
  void refusesEmptyOrTooShortContent() {
    assertThat(detector.detect(null)).isEmpty();
    assertThat(detector.detect(new byte[0])).isEmpty();
    assertThat(detector.detect(bytes(0xFF, 0xD8))).isEmpty();
  }

  @Test
  void refusesARiffThatIsNotWebp() {
    assertThat(
            detector.detect(
                bytes(
                    0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x41, 0x56, 0x49, 0x20, 0x00)))
        .isEmpty();
  }
}
