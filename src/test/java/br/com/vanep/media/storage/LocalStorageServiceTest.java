package br.com.vanep.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.media.enums.StorageProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalStorageServiceTest {

  @TempDir Path root;

  private LocalStorageService storage;

  @BeforeEach
  void setUp() {
    storage = new LocalStorageService(root.toString());
  }

  private void store(String objectKey, String content) {
    storage.upload(
        objectKey,
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
        "text/plain",
        content.length());
  }

  private String read(String objectKey) throws IOException {
    try (InputStream stream = storage.open(objectKey)) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void announcesItsProvider() {
    assertThat(storage.provider()).isEqualTo(StorageProvider.LOCAL);
  }

  @Test
  void storesAndReadsBack() throws IOException {
    store("DRIVER/tok-1/PHOTO/media-1.txt", "conteudo");

    assertThat(read("DRIVER/tok-1/PHOTO/media-1.txt")).isEqualTo("conteudo");
    assertThat(storage.exists("DRIVER/tok-1/PHOTO/media-1.txt")).isTrue();
  }

  @Test
  void createsTheIntermediateDirectories() {
    store("DRIVER/tok-1/DOCUMENT/media-2.txt", "x");

    assertThat(root.resolve("DRIVER/tok-1/DOCUMENT/media-2.txt")).exists();
  }

  @Test
  void deleteRemovesTheFile() {
    store("DRIVER/tok-1/PHOTO/media-3.txt", "x");

    storage.delete("DRIVER/tok-1/PHOTO/media-3.txt");

    assertThat(storage.exists("DRIVER/tok-1/PHOTO/media-3.txt")).isFalse();
  }

  @Test
  void deletingSomethingThatIsNotThereIsNotAnError() {
    assertThatNoException().isThrownBy(() -> storage.delete("DRIVER/tok-1/PHOTO/ghost.txt"));
  }

  @Test
  void openingSomethingThatIsNotThereFailsAsNotFound() {
    assertThatThrownBy(() -> storage.open("DRIVER/tok-1/PHOTO/ghost.txt"))
        .isInstanceOf(ObjectNotFoundException.class);
  }

  @Test
  void refusesAKeyThatClimbsOutOfTheRoot() throws IOException {
    Path outside = root.getParent().resolve("escaped.txt");
    Files.deleteIfExists(outside);

    assertThatThrownBy(() -> store("../escaped.txt", "nao deveria existir"))
        .isInstanceOf(InvalidObjectKeyException.class);

    assertThat(outside).doesNotExist();
  }

  @Test
  void refusesAKeyThatClimbsOutThroughAValidLookingPrefix() throws IOException {
    Path outside = root.getParent().resolve("escaped-deep.txt");
    Files.deleteIfExists(outside);

    assertThatThrownBy(() -> store("DRIVER/tok-1/../../../escaped-deep.txt", "nao deveria existir"))
        .isInstanceOf(InvalidObjectKeyException.class);

    assertThat(outside).doesNotExist();
  }

  @Test
  void refusesAnAbsoluteKey() {
    String absolute = root.getParent().resolve("absolute.txt").toAbsolutePath().toString();

    assertThatThrownBy(() -> store(absolute, "nao deveria existir"))
        .isInstanceOf(InvalidObjectKeyException.class);
  }

  @Test
  void refusesABlankKey() {
    assertThatThrownBy(() -> store("   ", "x")).isInstanceOf(InvalidObjectKeyException.class);
    assertThatThrownBy(() -> storage.open("")).isInstanceOf(InvalidObjectKeyException.class);
  }

  @Test
  void aKeyThatOnlyLooksLikeAnEscapeIsAccepted() throws IOException {
    store("DRIVER/tok-1/PHOTO/..seguro.txt", "conteudo");

    assertThat(read("DRIVER/tok-1/PHOTO/..seguro.txt")).isEqualTo("conteudo");
  }
}
