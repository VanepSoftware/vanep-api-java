package br.com.vanep.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.auth.password.PasswordResetToken;
import br.com.vanep.auth.password.PasswordResetTokenRepository;
import br.com.vanep.auth.token.SecureTokens;
import br.com.vanep.auth.verification.EmailVerificationToken;
import br.com.vanep.auth.verification.EmailVerificationTokenRepository;
import br.com.vanep.user.User;
import br.com.vanep.user.UserRepository;
import br.com.vanep.user.UserType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class PasswordRecoveryFlowTest {

  @Autowired private WebApplicationContext context;
  @Autowired private UserRepository users;
  @Autowired private PasswordResetTokenRepository resetTokens;
  @Autowired private EmailVerificationTokenRepository verificationTokens;
  @Autowired private PasswordEncoder passwordEncoder;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    resetTokens.deleteAll();
    verificationTokens.deleteAll();
    users.deleteAll();
  }

  private User saveUser(boolean verified) {
    User user = new User();
    user.setType(UserType.CLIENT);
    user.setName("Rec");
    user.setEmail("rec@vanep.com");
    user.setDocument("55555555555");
    user.setPassword(passwordEncoder.encode("oldpass12"));
    user.setVerified(verified);
    return users.save(user);
  }

  @Test
  void forgotPasswordPageIsPublic() throws Exception {
    mockMvc.perform(get("/forgot-password")).andExpect(status().isOk());
  }

  @Test
  void forgotPasswordAlwaysRedirectsGeneric() throws Exception {
    mockMvc
        .perform(post("/forgot-password").with(csrf()).param("email", "nobody@vanep.com"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?reset-requested"));
  }

  @Test
  void resetPasswordWithInvalidTokenShowsError() throws Exception {
    mockMvc
        .perform(get("/reset-password").param("token", "bad"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("inválido")));
  }

  @Test
  void fullResetFlowChangesPassword() throws Exception {
    User user = saveUser(true);
    String raw = SecureTokens.generate();
    PasswordResetToken token = new PasswordResetToken();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    resetTokens.save(token);

    mockMvc.perform(get("/reset-password").param("token", raw)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/reset-password")
                .with(csrf())
                .param("token", raw)
                .param("password", "newpass12")
                .param("confirmPassword", "newpass12"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?reset"));

    User reloaded = users.findById(user.getId()).orElseThrow();
    assertThat(passwordEncoder.matches("newpass12", reloaded.getPassword())).isTrue();
  }

  @Test
  void verifyEmailActivatesAccount() throws Exception {
    User user = saveUser(false);
    String raw = SecureTokens.generate();
    EmailVerificationToken token = new EmailVerificationToken();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(raw));
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    verificationTokens.save(token);

    mockMvc
        .perform(get("/verify-email").param("token", raw))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verified"));

    assertThat(users.findById(user.getId()).orElseThrow().isVerified()).isTrue();
  }

  @Test
  void verifyEmailWithInvalidTokenShowsResendPage() throws Exception {
    mockMvc
        .perform(get("/verify-email").param("token", "bad"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Reenviar verificação")));
  }
}
