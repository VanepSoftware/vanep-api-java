package br.com.vanep.media.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.media.enums.MediaVisibility;
import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MediaFileRepositoryTest {

  @Autowired private MediaFileRepository repository;

  private MediaFileModel newFile() {
    MediaFileModel file = new MediaFileModel();
    file.setObjectKey("driver/tok/photo/" + System.nanoTime() + ".jpg");
    file.setMimeType("image/jpeg");
    file.setSizeBytes(1024L);
    file.setOriginalName("foto do usuario.jpg");
    return file;
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    MediaFileModel saved = repository.save(newFile());

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void defaultsToTheLocalProviderAndPrivateVisibility() {
    MediaFileModel saved = repository.save(newFile());

    assertThat(saved.getProvider()).isEqualTo(StorageProvider.LOCAL);
    assertThat(saved.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
  }

  @Test
  void keepsTheOriginalNameAsMetadataOnly() {
    MediaFileModel saved = repository.save(newFile());

    assertThat(saved.getOriginalName()).isEqualTo("foto do usuario.jpg");
    assertThat(saved.getObjectKey()).doesNotContain("foto do usuario");
  }

  @Test
  void listsByProviderSoAMigrationCanWorkInBatches() {
    MediaFileModel local = repository.save(newFile());
    MediaFileModel moved = newFile();
    moved.setProvider(StorageProvider.FIREBASE);
    moved = repository.save(moved);

    assertThat(repository.findByProvider(StorageProvider.LOCAL, PageRequest.of(0, 10)))
        .extracting(MediaFileModel::getToken)
        .containsExactly(local.getToken());
    assertThat(repository.findByProvider(StorageProvider.FIREBASE, PageRequest.of(0, 10)))
        .extracting(MediaFileModel::getToken)
        .containsExactly(moved.getToken());
  }

  @Test
  void softDeletedFileIsAbsentFromDefaultQueries() {
    MediaFileModel saved = repository.save(newFile());

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }
}
