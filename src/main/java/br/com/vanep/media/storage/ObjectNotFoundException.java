package br.com.vanep.media.storage;

import java.io.Serial;

public class ObjectNotFoundException extends StorageException {

  @Serial private static final long serialVersionUID = 1L;

  public ObjectNotFoundException(String objectKey, Throwable cause) {
    super("Object not found: " + objectKey, cause);
  }
}
