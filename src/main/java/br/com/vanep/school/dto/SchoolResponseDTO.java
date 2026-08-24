package br.com.vanep.school.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import java.time.Instant;

/**
 * Escola magra: os campos que só existiam em cadastro manual (cnpj, phone, email) saíram, porque a
 * fonte passou a ser o Google Places e ele não os fornece. Um campo que nasce sempre nulo é ruído.
 */
public record SchoolResponseDTO(
    String token,
    String name,
    String googlePlaceId,
    String cityName,
    String cityToken,
    String districtName,
    String districtToken,
    AddressResponseDTO address,
    boolean active,
    Instant createdAt) {}
