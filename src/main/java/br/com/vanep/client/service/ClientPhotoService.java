package br.com.vanep.client.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.media.enums.MediaSlot;
import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.shared.error.ApiProblemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClientPhotoService {

  private final ClientRepository clients;
  private final MediaFileService media;

  public ClientPhotoService(ClientRepository clients, MediaFileService media) {
    this.clients = clients;
    this.media = media;
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
      throw ApiProblemException.notFound("media.not_found");
    }
    return photo;
  }

  private ClientModel require(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("client.not_found"));
  }
}
