package br.com.vanep.schooltransportcredential.dto;

import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import java.time.LocalDate;

public record SchoolTransportRequirementResponseDTO(
    CredentialRequirement requirement,
    boolean optional,
    boolean satisfied,
    String number,
    LocalDate validUntil) {}
