package br.com.vanep.contractreadiness.dto;

import br.com.vanep.contractreadiness.enums.ContractReadinessField;
import br.com.vanep.contractreadiness.enums.ContractReadinessSubject;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractReadinessMissingDTO(
    ContractReadinessSubject subject, String dependentToken, ContractReadinessField field) {}
