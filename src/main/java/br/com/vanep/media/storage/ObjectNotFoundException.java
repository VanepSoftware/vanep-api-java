package br.com.vanep.media.storage;

public class ObjectNotFoundException extends StorageException {

  public ObjectNotFoundException(String objectKey, Throwable cause) {
    super("Object not found: " + objectKey, cause);
  }
}
