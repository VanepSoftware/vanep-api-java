package br.com.vanep.notification.dto;

import br.com.vanep.notification.enums.DevicePlatform;
import br.com.vanep.notification.model.DeviceTokenModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceTokenRegisterRequestDTO(
    @NotBlank(message = "{notification.device_token.fcm_token.required}")
        @Size(
            max = DeviceTokenModel.FCM_TOKEN_MAX_LENGTH,
            message = "{notification.device_token.fcm_token.too_long}")
        String fcmToken,
    @NotNull(message = "{notification.device_token.platform.required}") DevicePlatform platform) {}
