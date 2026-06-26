package br.com.vanep.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Após o login social: se a conta ainda não foi completada (autoridade {@link
 * VanepOidcUserService#ROLE_PRE_REGISTER}), envia para o passo 2 do cadastro preservando o pedido
 * original (a tela de autorização OAuth). Caso contrário, segue para o destino salvo.
 */
@Component
public class OAuthLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {

    boolean pending =
        authentication.getAuthorities().stream()
            .anyMatch(a -> VanepOidcUserService.ROLE_PRE_REGISTER.equals(a.getAuthority()));

    if (pending) {
      getRedirectStrategy().sendRedirect(request, response, "/signup/complete");
      return;
    }

    super.onAuthenticationSuccess(request, response, authentication);
  }
}
