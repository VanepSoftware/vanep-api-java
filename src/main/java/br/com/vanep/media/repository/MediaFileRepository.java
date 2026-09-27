package br.com.vanep.media.repository;

import br.com.vanep.media.enums.StorageProvider;
import br.com.vanep.media.model.MediaFileModel;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFileModel, Long> {

  Optional<MediaFileModel> findByToken(String token);

  Page<MediaFileModel> findByProvider(StorageProvider provider, Pageable pageable);
}
