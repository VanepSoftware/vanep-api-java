package br.com.vanep.assistant.dto;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

public record AssistantDriverSummaryDTO(
    String name, @Nullable String photo, @Nullable BigDecimal rating) {}
