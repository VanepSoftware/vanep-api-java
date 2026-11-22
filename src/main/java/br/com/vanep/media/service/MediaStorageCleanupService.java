package br.com.vanep.media.service;

import br.com.vanep.media.event.MediaDiscardedEvent;
import br.com.vanep.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class MediaStorageCleanupService {

  private static final Logger log = LoggerFactory.getLogger(MediaStorageCleanupService.class);

  private final StorageService storage;

  public MediaStorageCleanupService(StorageService storage) {
    this.storage = storage;
  }

  // After the commit, so a rolled back transaction never loses a file it still references.
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void deleteDiscardedObject(MediaDiscardedEvent event) {
    try {
      storage.delete(event.objectKey());
    } catch (RuntimeException failure) {
      log.warn("Could not delete the discarded object {}", event.objectKey(), failure);
    }
  }
}
