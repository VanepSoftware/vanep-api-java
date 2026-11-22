package br.com.vanep.schooltransportcredential.mapper;

import br.com.vanep.schooltransportcredential.dto.CredentialDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportCredentialsResponseDTO;
import br.com.vanep.schooltransportcredential.dto.SchoolTransportRequirementResponseDTO;
import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SchoolTransportCredentialMapper {

  public SchoolTransportCredentialsResponseDTO toResponse(
      String vehicleToken, LocalDate date, SchoolTransportCredentialsDTO credentials) {
    Map<CredentialRequirement, CredentialDTO> byRequirement =
        credentials.credentials().stream()
            .collect(Collectors.toMap(credential -> credential.requirement(), Function.identity()));
    return new SchoolTransportCredentialsResponseDTO(
        vehicleToken,
        date,
        credentials.satisfied(),
        Arrays.stream(CredentialRequirement.values())
            .map(requirement -> toRequirement(requirement, byRequirement.get(requirement)))
            .toList());
  }

  static SchoolTransportRequirementResponseDTO toRequirement(
      CredentialRequirement requirement, @Nullable CredentialDTO credential) {
    return new SchoolTransportRequirementResponseDTO(
        requirement,
        requirement.isOptional(),
        credential != null,
        credential != null ? credential.number() : null,
        credential != null ? credential.validUntil() : null);
  }
}
