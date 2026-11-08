package br.com.vanep.conversation.dto;

import br.com.vanep.conversation.model.ConversationMessageModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageCreateRequestDTO(
    @NotBlank @Size(max = ConversationMessageModel.BODY_MAX_LENGTH) String body) {}
