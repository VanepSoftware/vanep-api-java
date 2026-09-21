package br.com.vanep.assistant.service;

import br.com.vanep.assistant.model.AssistantModel;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssistantPhotoService {

  private final AssistantRepository assistants;
  private final MediaFileService media;
  private final MessageSource messages;

  public AssistantPhotoService(
      AssistantRepository assistants, MediaFileService media, MessageSource messages) {
    this.assistants = assistants;
    this.media = media;
    this.messages = messages;
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
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("media.not_found"));
    }
    return photo;
  }

  private AssistantModel require(String token) {
    return assistants
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("assistant.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
