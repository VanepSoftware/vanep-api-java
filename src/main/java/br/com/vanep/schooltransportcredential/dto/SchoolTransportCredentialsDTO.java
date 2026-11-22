package br.com.vanep.schooltransportcredential.dto;

import br.com.vanep.schooltransportcredential.enums.CredentialRequirement;
import java.util.List;
import java.util.Set;

public record SchoolTransportCredentialsDTO(
    List<CredentialDTO> credentials, Set<CredentialRequirement> failed) {

  public boolean satisfied() {
    return failed.isEmpty();
  }
}
