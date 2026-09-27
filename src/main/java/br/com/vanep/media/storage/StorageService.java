package br.com.vanep.media.storage;

import br.com.vanep.media.enums.StorageProvider;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

public interface StorageService {

  StorageProvider provider();

  void upload(String objectKey, InputStream content, String mimeType, long sizeBytes);

  InputStream open(String objectKey);

  void delete(String objectKey);

  boolean exists(String objectKey);

  // Empty means this provider cannot sign: the caller streams the bytes instead.
  default Optional<URI> signedUrl(String objectKey, Duration ttl) {
    return Optional.empty();
  }
}
