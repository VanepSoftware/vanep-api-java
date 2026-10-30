package br.com.vanep.contractreadiness.dto;

import java.util.List;

public record ContractReadinessResponseDTO(
    boolean ready, List<ContractReadinessMissingDTO> missing) {}
