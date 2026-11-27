package br.com.vanep.media.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.vanep.media.model.MediaFileModel;
import br.com.vanep.media.service.MediaFileService;
import br.com.vanep.media.storage.StorageService;
import br.com.vanep.shared.error.ApiProblemException;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MediaResponderTest {

  @Mock private MediaFileService mediaFileService;
  @Mock private StorageService storageService;

  private MediaResponder responder;

  @BeforeEach
  void setUp() {
    responder = new MediaResponder(mediaFileService, storageService);
  }

  @Test
  void respondThrowsMediaNotFoundWhenMediaIsNull() {
    assertThatThrownBy(() -> responder.respond(null, null))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> {
              ApiProblemException problem = (ApiProblemException) ex;
              assertThat(problem.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(problem.getCode()).isEqualTo("media.not_found");
            });
  }

  @Test
  void respondRedirectsWhenSignedUrlIsAvailableAndStreamsOtherwise() {
    MediaFileModel media = new MediaFileModel();
    media.setToken("tok-1");
    media.setObjectKey("photos/tok-1.png");
    media.setMimeType(MediaType.IMAGE_PNG_VALUE);
    media.setSizeBytes(4L);

    URI signed = URI.create("https://cdn.vanep.test/signed");
    when(storageService.signedUrl(eq("photos/tok-1.png"), any())).thenReturn(Optional.of(signed));

    ResponseEntity<InputStreamResource> redirected = responder.respond(media, "tok-1");
    assertThat(redirected.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(redirected.getHeaders().getLocation()).isEqualTo(signed);

    when(storageService.signedUrl(eq("photos/tok-1.png"), any())).thenReturn(Optional.empty());
    when(mediaFileService.openContent(media))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

    ResponseEntity<InputStreamResource> streamed = responder.respond(media, "tok-1");
    assertThat(streamed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(streamed.getHeaders().getCacheControl()).contains("immutable");
    assertThat(streamed.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("tok-1");
  }
}
