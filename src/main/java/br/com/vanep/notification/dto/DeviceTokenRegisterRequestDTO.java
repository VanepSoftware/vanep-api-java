package br.com.vanep.notification.dto;

import br.com.vanep.notification.enums.DevicePlatform;
import br.com.vanep.notification.model.DeviceTokenModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceTokenRegisterRequestDTO(
    @NotBlank @Size(max = DeviceTokenModel.FCM_TOKEN_MAX_LENGTH) String fcmToken,
    @NotNull DevicePlatform platform) {}
