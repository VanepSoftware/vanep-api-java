package br.com.vanep.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.repository.MediaFileRepository;
import br.com.vanep.media.storage.StorageService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MediaFileServiceStoreTest {

  private static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

  @Autowired private MediaFileService media;
  @Autowired private MediaFileRepository repository;
  @MockitoBean private StorageService storage;

  @BeforeEach
  void storage() {
    when(storage.provider()).thenReturn(StorageProvider.LOCAL);
  }

  @Test
  void contentWithoutARequestIsStoredUnderTheSlotOfItsOwner() {
    MediaFileModel stored =
        media.store(MediaSlot.DRIVER_DOCUMENT_FILE, "owner-token", PDF, "criminal_record.pdf");

    assertThat(stored.getObjectKey())
        .isEqualTo("driver-document/owner-token/file/" + stored.getToken() + ".pdf");
    assertThat(stored.getMimeType()).isEqualTo("application/pdf");
    assertThat(stored.getSizeBytes()).isEqualTo(PDF.length);
    assertThat(stored.getOriginalName()).isEqualTo("criminal_record.pdf");
    assertThat(stored.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
    assertThat(repository.findByToken(stored.getToken())).isPresent();
    verify(storage)
        .upload(eq(stored.getObjectKey()), any(), eq("application/pdf"), eq((long) PDF.length));
  }

  @Test
  void emptyContentIsRefused() {
    assertThatThrownBy(
            () -> media.store(MediaSlot.DRIVER_DOCUMENT_FILE, "owner", new byte[0], "empty.pdf"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(storage, never()).upload(anyString(), any(), anyString(), anyLong());
  }

  @Test
  void contentOfATypeTheSlotDoesNotTakeIsRefused() {
    assertThatThrownBy(() -> media.store(MediaSlot.DRIVER_PHOTO, "owner", PDF, "photo.pdf"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verify(storage, never()).upload(anyString(), any(), anyString(), anyLong());
  }
}
