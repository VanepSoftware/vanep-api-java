package br.com.vanep.assistant.service;

import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.error.ApiProblemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssistantPhotoService {

  private final AssistantRepository assistants;
  private final MediaFileService media;

  public AssistantPhotoService(AssistantRepository assistants, MediaFileService media) {
    this.assistants = assistants;
    this.media = media;
  }

  @Transactional
  public void replace(String token, MultipartFile file) {
    AssistantModel assistant = require(token);
    MediaFileModel previous = assistant.getPhoto();

    assistant.setPhoto(media.store(MediaSlot.ASSISTANT_PHOTO, assistant.getToken(), file));
    assistants.save(assistant);

    media.discard(previous);
  }

  @Transactional(readOnly = true)
  public MediaFileModel requirePhoto(String token) {
    MediaFileModel photo = require(token).getPhoto();
    if (photo == null) {
      throw ApiProblemException.notFound("media.not_found");
    }
    return photo;
  }

  private AssistantModel require(String token) {
    return assistants
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("assistant.not_found"));
  }
}
