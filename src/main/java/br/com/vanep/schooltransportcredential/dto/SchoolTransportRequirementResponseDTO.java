package br.com.vanep.schooltransportcredential.dto;

import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record SchoolTransportRequirementResponseDTO(
    CredentialRequirement requirement,
    boolean optional,
    boolean satisfied,
    @Nullable String number,
    @Nullable LocalDate validUntil) {}
