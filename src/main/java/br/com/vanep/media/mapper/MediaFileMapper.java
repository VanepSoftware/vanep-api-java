package br.com.vanep.media.mapper;

import br.com.vanep.media.dto.MediaFileResponseDTO;
import br.com.vanep.media.model.MediaFileModel;
import org.springframework.stereotype.Component;

@Component
public class MediaFileMapper {

  // Always our own endpoint, never a provider URL, so clients survive a storage change.
  public MediaFileResponseDTO toResponse(MediaFileModel media) {
    return new MediaFileResponseDTO(
        media.getToken(),
        "/api/media/" + media.getToken() + "/download",
        media.getPurpose(),
        media.getVisibility(),
        media.getMimeType(),
        media.getSizeBytes(),
        media.getOriginalName(),
        media.getCreatedAt());
  }
}
