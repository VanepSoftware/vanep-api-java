package br.com.vanep.media.storage;

import br.com.vanep.media.enums.StorageProvider;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LocalStorageService implements StorageService {

  private final Path root;

  public LocalStorageService(@Value("${vanep.media.storage.root}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  @Override
  public StorageProvider provider() {
    return StorageProvider.LOCAL;
  }

  @Override
  public void upload(String objectKey, InputStream content, String mimeType, long sizeBytes) {
    Path target = resolve(objectKey);
    try {
      Files.createDirectories(target.getParent());
      Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException cause) {
      throw new StorageException("Failed to store object " + objectKey, cause);
    }
  }

  @Override
  public InputStream open(String objectKey) {
    try {
      return Files.newInputStream(resolve(objectKey));
    } catch (NoSuchFileException cause) {
      throw new ObjectNotFoundException(objectKey, cause);
    } catch (IOException cause) {
      throw new StorageException("Failed to read object " + objectKey, cause);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      Files.deleteIfExists(resolve(objectKey));
    } catch (IOException cause) {
      throw new StorageException("Failed to delete object " + objectKey, cause);
    }
  }

  @Override
  public boolean exists(String objectKey) {
    return Files.exists(resolve(objectKey));
  }

  // Defense in depth: keys are built server-side, but a broken derivation must not escape the root.
  private Path resolve(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      throw new InvalidObjectKeyException("Object key must not be blank");
    }
    Path candidate = root.resolve(objectKey).normalize();
    if (!candidate.startsWith(root)) {
      throw new InvalidObjectKeyException("Object key escapes the storage root: " + objectKey);
    }
    return candidate;
  }
}
