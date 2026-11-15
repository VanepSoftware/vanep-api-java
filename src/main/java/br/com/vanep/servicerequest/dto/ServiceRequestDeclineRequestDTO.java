package br.com.vanep.servicerequest.dto;

import jakarta.validation.constraints.Size;

public record ServiceRequestDeclineRequestDTO(
    @Size(max = 500, message = "{service_request.decline_reason.too_long}") String reason) {}
