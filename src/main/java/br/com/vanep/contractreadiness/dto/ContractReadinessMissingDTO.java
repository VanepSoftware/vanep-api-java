package br.com.vanep.contractreadiness.dto;

import br.com.vanep.contractreadiness.enums.ContractReadinessField;
import br.com.vanep.contractreadiness.enums.ContractReadinessSubject;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractReadinessMissingDTO(
    ContractReadinessSubject subject,
    @Nullable String dependentToken,
    ContractReadinessField field) {}
