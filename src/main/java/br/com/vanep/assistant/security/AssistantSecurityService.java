package br.com.vanep.assistant.security;

import br.com.vanep.assistant.repository.AssistantRepository;
import br.com.vanep.auth.security.SecurityHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("assistantSecurity")
public class AssistantSecurityService {

  private final AssistantRepository assistants;

  public AssistantSecurityService(AssistantRepository assistants) {
    this.assistants = assistants;
  }

  public boolean isDriverOwnerOfAssistant(String assistantToken, String callerUid) {
    return assistants
        .findDriverUserTokenByAssistantToken(assistantToken)
        .map(driverUserToken -> driverUserToken.equals(callerUid))
        .orElse(false);
  }

  public boolean isDriverOwnerOfAssistant(String assistantToken, Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .map(callerUid -> isDriverOwnerOfAssistant(assistantToken, callerUid))
        .orElse(false);
  }

  public boolean isSelfAssistant(Authentication authentication) {
    return SecurityHelper.getCallerUid(authentication)
        .flatMap(assistants::findByUserToken)
        .isPresent();
  }
}
