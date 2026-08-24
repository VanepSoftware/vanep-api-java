package br.com.vanep.address.dto;

/**
 * Endereço residencial do próprio chamador. Só ele lê isto — nenhuma resposta de busca ou voltada
 * ao motorista carrega estes campos (spec personal-address, requisito de privacidade).
 *
 * <p>Identificadores são {@code token} opacos (regra 13). O {@code googlePlaceId} é dado de entrada
 * do Google, não identificador de recurso da Vanep, e é devolvido para o cliente conseguir
 * reapresentar a seleção.
 */
public record PersonalAddressResponseDTO(
    String token,
    String street,
    String number,
    String complement,
    String zipCode,
    String districtName,
    String districtToken,
    String cityName,
    String cityToken,
    String stateUf,
    String countryIsoCode,
    String googlePlaceId) {}
