package br.com.vanep.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ConversationMessageCipherTest {

  private static final String KEY = "7e57".repeat(16);
  private static final String OTHER_KEY = "abcd".repeat(16);

  private final ConversationMessageCipher cipher = new ConversationMessageCipher(KEY);

  @Test
  void encryptsIntoTheVersionedFormatWithoutThePlaintext() {
    String stored = cipher.encrypt("Tem vaga para a manhã?");

    assertThat(stored).startsWith("v1:").doesNotContain("vaga");
  }

  @Test
  void decryptsBackToTheOriginalText() {
    for (String text : new String[] {"Tem vaga para a manhã?", "Combinado 👍", "a".repeat(2000)}) {
      assertThat(cipher.decrypt(cipher.encrypt(text))).isEqualTo(text);
    }
  }

  @Test
  void theSameTextNeverProducesTheSameValue() {
    assertThat(cipher.encrypt("Oi")).isNotEqualTo(cipher.encrypt("Oi"));
  }

  @Test
  void aTamperedValueFailsInsteadOfReturningAlteredText() {
    byte[] payload = Base64.getDecoder().decode(cipher.encrypt("Oi").substring(3));
    payload[payload.length - 1] ^= 1;
    String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aValueWrittenWithAnotherKeyFails() {
    String stored = new ConversationMessageCipher(OTHER_KEY).encrypt("Oi");

    assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aValueThatIsNotBase64Fails() {
    assertThatThrownBy(() -> cipher.decrypt("v1:not base64!"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aBodyStoredBeforeEncryptionIsReturnedAsStored() {
    assertThat(cipher.decrypt("Mensagem antiga")).isEqualTo("Mensagem antiga");
  }

  @Test
  void aMissingOrMalformedKeyIsRefused() {
    for (String key : new String[] {null, "", "   ", "abc", "zz".repeat(32), "7e57".repeat(8)}) {
      assertThatThrownBy(() -> new ConversationMessageCipher(key))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("vanep.chat.encryption-key");
    }
  }
}
