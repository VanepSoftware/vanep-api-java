package br.com.vanep.notification.dto;

import br.com.vanep.notification.enums.NotificationType;
import java.time.Instant;
import java.util.Map;

public record NotificationResponseDTO(
    String token,
    NotificationType type,
    String title,
    String body,
    Map<String, String> data,
    Instant createdAt,
    Instant readAt) {}
