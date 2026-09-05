package br.com.vanep.driverservicearea.dto;

public record DriverServiceAreaResponseDTO(
    String token,
    String name,
    String districtToken,
    String cityName,
    String cityToken,
    String stateUf,
    boolean coversWholeCity) {}
