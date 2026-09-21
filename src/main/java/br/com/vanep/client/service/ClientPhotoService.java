package br.com.vanep.client.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
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
public class ClientPhotoService {

  private final ClientRepository clients;
  private final MediaFileService media;
  private final MessageSource messages;

  public ClientPhotoService(
      ClientRepository clients, MediaFileService media, MessageSource messages) {
    this.clients = clients;
    this.media = media;
    this.messages = messages;
  }

  @Transactional
  public void replace(String token, MultipartFile file) {
    ClientModel client = require(token);
    MediaFileModel previous = client.getPhoto();

    client.setPhoto(media.store(MediaSlot.CLIENT_PHOTO, client.getToken(), file));
    clients.save(client);

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

  private ClientModel require(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("client.not_found")));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
