package br.com.vanep.media.repository;

import br.com.vanep.media.enums.MediaOwnerType;
import br.com.vanep.media.enums.MediaPurpose;
import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFileModel, Long> {

  Optional<MediaFileModel> findByToken(String token);

  List<MediaFileModel> findByOwnerTypeAndOwnerIdAndPurpose(
      MediaOwnerType ownerType, Long ownerId, MediaPurpose purpose);

  List<MediaFileModel> findByOwnerTypeAndOwnerId(MediaOwnerType ownerType, Long ownerId);

  boolean existsByObjectKey(String objectKey);

  // Lets a provider migration move files in batches while both providers serve.
  Page<MediaFileModel> findByProvider(StorageProvider provider, Pageable pageable);
}
