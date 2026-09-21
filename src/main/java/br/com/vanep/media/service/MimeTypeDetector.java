package br.com.vanep.media.service;

import java.util.Optional;
import org.springframework.stereotype.Component;

// Allow-list by signature: the declared content type and the file name are client input.
@Component
public class MimeTypeDetector {

  public Optional<String> detect(byte[] content) {
    if (content == null || content.length < 4) {
      return Optional.empty();
    }
    if (startsWith(content, 0xFF, 0xD8, 0xFF)) {
      return Optional.of("image/jpeg");
    }
    if (startsWith(content, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
      return Optional.of("image/png");
    }
    if (isWebp(content)) {
      return Optional.of("image/webp");
    }
    if (startsWith(content, 0x25, 0x50, 0x44, 0x46)) {
      return Optional.of("application/pdf");
    }
    return Optional.empty();
  }

  private boolean isWebp(byte[] content) {
    return content.length >= 12
        && startsWith(content, 0x52, 0x49, 0x46, 0x46)
        && matchesAt(content, 8, 0x57, 0x45, 0x42, 0x50);
  }

  private boolean startsWith(byte[] content, int... signature) {
    return matchesAt(content, 0, signature);
  }

  private boolean matchesAt(byte[] content, int offset, int... signature) {
    if (content.length < offset + signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if ((content[offset + index] & 0xFF) != signature[index]) {
        return false;
      }
    }
    return true;
  }
}
