package br.com.vanep.dependent.dto;

import br.com.vanep.address.dto.AddressResponseDTO;
import org.jspecify.annotations.Nullable;

public record DependentSchoolDTO(String token, String name, @Nullable AddressResponseDTO address) {}
