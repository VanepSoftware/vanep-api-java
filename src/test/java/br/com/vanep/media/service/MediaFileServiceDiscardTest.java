package br.com.vanep.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MediaFileServiceDiscardTest {

  @Autowired private MediaFileService media;
  @Autowired private MediaFileRepository repository;
  @Autowired private TransactionTemplate transactions;
  @MockitoBean private StorageService storage;

  @Test
  void theObjectIsDeletedOnlyAfterTheCommit() {
    MediaFileModel file = repository.save(newFile("driver/tok/photo/committed.jpg"));

    transactions.executeWithoutResult(
        status -> {
          media.discard(file);
          verify(storage, never()).delete(any());
        });

    verify(storage).delete("driver/tok/photo/committed.jpg");
    assertThat(repository.findByToken(file.getToken())).isEmpty();
  }

  @Test
  void aRolledBackTransactionKeepsTheObjectAndTheRow() {
    MediaFileModel file = repository.save(newFile("driver/tok/photo/kept.jpg"));

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      media.discard(file);
                      throw new IllegalStateException("the approval failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    verify(storage, never()).delete(any());
    assertThat(repository.findByToken(file.getToken())).isPresent();
  }

  @Test
  void aStorageFailureAfterTheCommitDoesNotFailTheRequest() {
    MediaFileModel file = repository.save(newFile("driver/tok/photo/stuck.jpg"));
    doThrow(new IllegalStateException("storage down")).when(storage).delete(any());

    transactions.executeWithoutResult(status -> media.discard(file));

    assertThat(repository.findByToken(file.getToken())).isEmpty();
  }

  private static MediaFileModel newFile(String objectKey) {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey(objectKey);
    file.setMimeType("image/jpeg");
    file.setSizeBytes(1024L);
    return file;
  }
}
