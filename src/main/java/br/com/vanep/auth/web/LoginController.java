package br.com.vanep.auth.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serve a tela de login (Thymeleaf), no mesmo modelo do "front" de login dos checklists. */
@Controller
public class LoginController {

  @GetMapping("/login")
  public String login() {
    return "login";
  }
}
