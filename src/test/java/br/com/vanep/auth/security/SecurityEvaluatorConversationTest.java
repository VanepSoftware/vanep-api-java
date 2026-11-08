package br.com.vanep.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.vanep.conversation.repository.ConversationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityEvaluatorConversationTest {

  private static final String CONVERSATION = "conversation-1";

  @Mock private ConversationRepository conversationRepository;

  @InjectMocks private SecurityEvaluator sec;

  @BeforeEach
  void setUp() {
    when(conversationRepository.findClientUserTokenByToken(CONVERSATION))
        .thenReturn(Optional.of("client-user"));
    when(conversationRepository.findDriverUserTokenByToken(CONVERSATION))
        .thenReturn(Optional.of("driver-user"));
  }

  @Test
  void theClientIsAParty() {
    assertThat(sec.isConversationParty(CONVERSATION, authenticationOf("client-user"))).isTrue();
  }

  @Test
  void theDriverIsAParty() {
    assertThat(sec.isConversationParty(CONVERSATION, authenticationOf("driver-user"))).isTrue();
  }

  @Test
  void anotherUserIsNotAParty() {
    assertThat(sec.isConversationParty(CONVERSATION, authenticationOf("someone-else"))).isFalse();
  }

  @Test
  void anUnknownConversationHasNoParty() {
    when(conversationRepository.findClientUserTokenByToken("unknown")).thenReturn(Optional.empty());
    when(conversationRepository.findDriverUserTokenByToken("unknown")).thenReturn(Optional.empty());

    assertThat(sec.isConversationParty("unknown", authenticationOf("client-user"))).isFalse();
  }

  @Test
  void anAuthenticationWithoutUidIsNotAParty() {
    assertThat(sec.isConversationParty(CONVERSATION, new TestingAuthenticationToken("x", "y")))
        .isFalse();
  }

  private Authentication authenticationOf(String uid) {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("uid", uid).build();
    return new JwtAuthenticationToken(jwt);
  }
}
