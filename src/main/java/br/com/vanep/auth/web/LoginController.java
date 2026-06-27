package br.com.vanep.auth.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

  private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

  public LoginController(
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  @GetMapping("/login")
  public String login(Model model) {

    model.addAttribute("googleEnabled", clientRegistrationRepository.getIfAvailable() != null);
    return "login";
  }
}
