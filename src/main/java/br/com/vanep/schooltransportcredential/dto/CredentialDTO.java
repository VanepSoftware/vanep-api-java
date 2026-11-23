package br.com.vanep.schooltransportcredential.dto;

import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record CredentialDTO(
    CredentialRequirement requirement, @Nullable String number, @Nullable LocalDate validUntil) {}
