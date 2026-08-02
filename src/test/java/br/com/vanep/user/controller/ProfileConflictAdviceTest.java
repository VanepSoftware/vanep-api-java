package br.com.vanep.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.user.exception.ProfileCooldownException;
import br.com.vanep.user.exception.ProfileEmailDuplicateException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ProfileConflictAdviceTest {

  private static final Instant RETRY_AFTER = Instant.parse("2026-08-31T15:00:00Z");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProfileConflictFixtureController())
            .setControllerAdvice(new ProfileConflictAdvice())
            .build();
  }

  @Test
  void cooldownConflictReturnsStructuredBodyWithRetryAfter() throws Exception {
    mockMvc
        .perform(get("/__test/profile-conflict/cooldown").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Name cooldown active"))
        .andExpect(jsonPath("$.code").value("cooldown"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.retryAfter").value("2026-08-31T15:00:00Z"));
  }

  @Test
  void emailDuplicateConflictReturnsStructuredBodyWithoutRetryAfter() throws Exception {
    mockMvc
        .perform(get("/__test/profile-conflict/email-duplicate").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("auth.signup.email.duplicate"))
        .andExpect(jsonPath("$.code").value("email_duplicate"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @RestController
  @RequestMapping("/__test/profile-conflict")
  static class ProfileConflictFixtureController {

    @GetMapping("/cooldown")
    void throwCooldown() {
      throw new ProfileCooldownException("Name cooldown active", "name", RETRY_AFTER);
    }

    @GetMapping("/email-duplicate")
    void throwEmailDuplicate() {
      throw new ProfileEmailDuplicateException("auth.signup.email.duplicate");
    }
  }
}
