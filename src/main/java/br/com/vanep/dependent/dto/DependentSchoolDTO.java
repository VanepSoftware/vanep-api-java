package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressResponseDTO;

public record DependentSchoolDTO(String token, String name, AddressResponseDTO address) {}
