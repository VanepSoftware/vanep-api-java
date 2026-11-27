package br.com.vanep.conversation.dto;

import br.com.vanep.conversation.model.ConversationMessageModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationMessageCreateRequestDTO(
    @NotBlank(message = "{conversation.message.body.required}")
        @Size(
            max = ConversationMessageModel.BODY_MAX_LENGTH,
            message = "{conversation.message.body.too_long}")
        String body) {}
