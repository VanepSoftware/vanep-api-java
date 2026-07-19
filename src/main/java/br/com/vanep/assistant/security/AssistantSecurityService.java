package br.com.vanep.assistant.security;

import br.com.vanep.assistant.repository.AssistantInviteRepository;
import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.security.SecurityHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("assistantSecurity")
public class AssistantSecurityService {

  private final AssistantInviteRepository inviteRepository;
  private final AssistantRepository assistantRepository;

  public AssistantSecurityService(
      AssistantInviteRepository inviteRepository, AssistantRepository assistantRepository) {
    this.inviteRepository = inviteRepository;
    this.assistantRepository = assistantRepository;
  }

  public boolean ownsInvite(String token, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            callerUid ->
                inviteRepository
                    .findByToken(token)
                    .map(invite -> invite.getDriver().getUser().getToken().equals(callerUid)))
        .orElse(false);
  }

  public boolean isDriverOfAssistant(String assistantToken, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(
            callerUid ->
                assistantRepository
                    .findByToken(assistantToken)
                    .filter(assistant -> assistant.getDriver() != null)
                    .map(assistant -> assistant.getDriver().getUser().getToken().equals(callerUid)))
        .orElse(false);
  }
}
