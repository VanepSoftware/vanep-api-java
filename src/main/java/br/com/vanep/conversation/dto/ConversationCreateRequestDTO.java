package br.com.vanep.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public record ConversationCreateRequestDTO(@NotBlank String driverToken) {}
