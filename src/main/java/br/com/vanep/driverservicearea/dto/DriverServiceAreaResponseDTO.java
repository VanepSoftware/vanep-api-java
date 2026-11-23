package br.com.vanep.driverservicearea.dto;

import org.jspecify.annotations.Nullable;

public record DriverServiceAreaResponseDTO(
    String token,
    String name,
    @Nullable String districtToken,
    String cityName,
    String cityToken,
    String stateUf,
    boolean coversWholeCity) {}
