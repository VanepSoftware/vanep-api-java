package br.com.vanep.assistant.dto;

import java.math.BigDecimal;

public record AssistantDriverSummaryDTO(
    String name, String photo, String city, BigDecimal rating) {}
