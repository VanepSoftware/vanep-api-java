package br.com.vanep.conversation.model;

import br.com.vanep.conversation.service.ConversationMessageCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ConversationMessageBodyConverter implements AttributeConverter<String, String> {

  private final ConversationMessageCipher cipher;

  public ConversationMessageBodyConverter(ConversationMessageCipher cipher) {
    this.cipher = cipher;
  }

  @Override
  public String convertToDatabaseColumn(String body) {
    return body == null ? null : cipher.encrypt(body);
  }

  @Override
  public String convertToEntityAttribute(String stored) {
    return stored == null ? null : cipher.decrypt(stored);
  }
}
