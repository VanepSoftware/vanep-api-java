package br.com.vanep.driverservicearea.dto;

/**
 * Região atendida, como o motorista e o cliente a veem.
 *
 * <p>Só nome de região e tokens opacos. Nenhum campo de logradouro aparece aqui — nem poderia, a
 * tabela não tem.
 */
public record DriverServiceAreaResponseDTO(
    String token,
    String name,
    String districtToken,
    String cityName,
    String cityToken,
    String stateUf,
    boolean coversWholeCity) {}
