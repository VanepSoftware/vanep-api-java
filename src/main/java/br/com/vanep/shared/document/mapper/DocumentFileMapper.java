package br.com.vanep.shared.document.mapper;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.shared.document.dto.DocumentFileResponseDTO;

public final class DocumentFileMapper {

  private DocumentFileMapper() {}

  public static DocumentFileResponseDTO toResponse(MediaFileModel file) {
    return file == null
        ? null
        : new DocumentFileResponseDTO(
            file.getMimeType(), file.getSizeBytes(), file.getOriginalName());
  }
}
