package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.repository.ContractRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityEvaluatorTest {

  @Mock private ClientDriverRepository clientDriverRepository;
  @Mock private ContractRepository contractRepository;

  @InjectMocks private SecurityEvaluator evaluator;

  @BeforeEach
  void setUp() {
    when(contractRepository.findClientDriverTokenByToken("contract"))
        .thenReturn(Optional.of("link"));
    when(clientDriverRepository.findClientUserTokenByLinkToken("link"))
        .thenReturn(Optional.of("maria-uid"));
    when(clientDriverRepository.findDriverUserTokenByLinkToken("link"))
        .thenReturn(Optional.of("carlos-uid"));
  }

  @Test
  void theClientOfTheLinkIsAContractParty() {
    assertThat(evaluator.isContractParty("contract", callerWithUid("maria-uid"))).isTrue();
  }

  @Test
  void theDriverOfTheLinkIsAContractParty() {
    assertThat(evaluator.isContractParty("contract", callerWithUid("carlos-uid"))).isTrue();
  }

  @Test
  void anotherClientIsNotAContractParty() {
    assertThat(evaluator.isContractParty("contract", callerWithUid("ana-uid"))).isFalse();
  }

  @Test
  void anUnknownContractHasNoParty() {
    when(contractRepository.findClientDriverTokenByToken("missing")).thenReturn(Optional.empty());

    assertThat(evaluator.isContractParty("missing", callerWithUid("maria-uid"))).isFalse();
  }

  private Authentication callerWithUid(String uid) {
    Jwt jwt = Jwt.withTokenValue("jwt").header("alg", "none").claim("uid", uid).build();
    return new JwtAuthenticationToken(jwt);
  }
}
