package br.com.vanep.media.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.media.enums.MediaOwnerType;
import br.com.vanep.media.enums.MediaPurpose;
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

  private MediaFileModel newFile(MediaOwnerType ownerType, Long ownerId, MediaPurpose purpose) {
    MediaFileModel file = new MediaFileModel();
    file.setOwnerType(ownerType);
    file.setOwnerId(ownerId);
    file.setPurpose(purpose);
    file.setObjectKey(ownerType + "/tok/" + purpose + "/" + System.nanoTime() + ".jpg");
    file.setMimeType("image/jpeg");
    file.setSizeBytes(1024L);
    file.setOriginalName("foto do usuario.jpg");
    return file;
  }

  @Test
  void generatesOpaqueTokenOnPersist() {
    MediaFileModel saved = repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));

    assertThat(saved.getToken()).isNotBlank();
    assertThat(saved.getToken()).doesNotContain("-");
    assertThat(repository.findByToken(saved.getToken())).isPresent();
  }

  @Test
  void defaultsToTheLocalProvider() {
    MediaFileModel saved = repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));

    assertThat(saved.getProvider()).isEqualTo(StorageProvider.LOCAL);
  }

  @Test
  void takesTheVisibilityFromThePurposeWhenNotGiven() {
    MediaFileModel photo = repository.save(newFile(MediaOwnerType.CLIENT, 1L, MediaPurpose.PHOTO));
    MediaFileModel document =
        repository.save(newFile(MediaOwnerType.DRIVER_DOCUMENT, 1L, MediaPurpose.DOCUMENT));

    assertThat(photo.getVisibility()).isEqualTo(MediaVisibility.PUBLIC);
    assertThat(document.getVisibility()).isEqualTo(MediaVisibility.PRIVATE);
  }

  @Test
  void keepsTheOriginalNameAsMetadataOnly() {
    MediaFileModel saved = repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));

    assertThat(saved.getOriginalName()).isEqualTo("foto do usuario.jpg");
    assertThat(saved.getObjectKey()).doesNotContain("foto do usuario");
  }

  @Test
  void findsTheFilesOfAnOwnerByPurpose() {
    repository.save(newFile(MediaOwnerType.VEHICLE, 7L, MediaPurpose.PHOTO_FRONT));
    repository.save(newFile(MediaOwnerType.VEHICLE, 7L, MediaPurpose.PHOTO_SIDE));
    repository.save(newFile(MediaOwnerType.VEHICLE, 8L, MediaPurpose.PHOTO_FRONT));

    assertThat(
            repository.findByOwnerTypeAndOwnerIdAndPurpose(
                MediaOwnerType.VEHICLE, 7L, MediaPurpose.PHOTO_FRONT))
        .hasSize(1);
    assertThat(repository.findByOwnerTypeAndOwnerId(MediaOwnerType.VEHICLE, 7L)).hasSize(2);
  }

  @Test
  void twoOwnersOfDifferentTypesDoNotCollideOnTheSameId() {
    repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));
    repository.save(newFile(MediaOwnerType.CLIENT, 1L, MediaPurpose.PHOTO));

    assertThat(repository.findByOwnerTypeAndOwnerId(MediaOwnerType.DRIVER, 1L)).hasSize(1);
    assertThat(repository.findByOwnerTypeAndOwnerId(MediaOwnerType.CLIENT, 1L)).hasSize(1);
  }

  @Test
  void listsByProviderSoAMigrationCanWorkInBatches() {
    MediaFileModel local = repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));
    MediaFileModel moved = newFile(MediaOwnerType.DRIVER, 2L, MediaPurpose.PHOTO);
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
    MediaFileModel saved = repository.save(newFile(MediaOwnerType.DRIVER, 1L, MediaPurpose.PHOTO));

    repository.delete(saved);

    assertThat(repository.findByToken(saved.getToken())).isEmpty();
    assertThat(repository.findByOwnerTypeAndOwnerId(MediaOwnerType.DRIVER, 1L)).isEmpty();
    assertThat(repository.findAll()).isEmpty();
  }
}
