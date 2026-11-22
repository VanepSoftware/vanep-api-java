package br.com.vanep.contractreadiness.dto;

import org.jspecify.annotations.Nullable;

public record ContractReadinessClientDTO(
    String document, @Nullable String phone, @Nullable Long addressId) {}
