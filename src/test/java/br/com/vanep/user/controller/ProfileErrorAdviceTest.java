package br.com.vanep.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.user.exception.ProfileBadRequestException;
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

class ProfileErrorAdviceTest {

  private static final Instant RETRY_AFTER = Instant.parse("2026-08-31T15:00:00Z");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProfileErrorFixtureController())
            .setControllerAdvice(new ProfileErrorAdvice())
            .build();
  }

  @Test
  void cooldownConflictReturnsStructuredBodyWithRetryAfter() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/cooldown").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Name cooldown active"))
        .andExpect(jsonPath("$.code").value("cooldown"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.retryAfter").value("2026-08-31T15:00:00Z"));
  }

  @Test
  void emailDuplicateConflictReturnsStructuredBodyWithoutRetryAfter() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/email-duplicate").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("auth.signup.email.duplicate"))
        .andExpect(jsonPath("$.code").value("email_duplicate"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void fieldNullReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/field-null").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("field null"))
        .andExpect(jsonPath("$.code").value("field_null"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void phoneBlankReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/phone-blank").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("phone blank"))
        .andExpect(jsonPath("$.code").value("phone_blank"))
        .andExpect(jsonPath("$.field").value("phone"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailSameReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/email-same").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("email same"))
        .andExpect(jsonPath("$.code").value("email_same"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailInvalidReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/email-invalid").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("email invalid"))
        .andExpect(jsonPath("$.code").value("email_invalid"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @Test
  void emailRequiredReturns400StructuredBody() throws Exception {
    mockMvc
        .perform(get("/__test/profile-error/email-required").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("email required"))
        .andExpect(jsonPath("$.code").value("email_required"))
        .andExpect(jsonPath("$.field").value("email"))
        .andExpect(jsonPath("$.retryAfter").doesNotExist());
  }

  @RestController
  @RequestMapping("/__test/profile-error")
  static class ProfileErrorFixtureController {

    @GetMapping("/cooldown")
    void throwCooldown() {
      throw new ProfileCooldownException("Name cooldown active", "name", RETRY_AFTER);
    }

    @GetMapping("/email-duplicate")
    void throwEmailDuplicate() {
      throw new ProfileEmailDuplicateException("auth.signup.email.duplicate");
    }

    @GetMapping("/field-null")
    void throwFieldNull() {
      throw ProfileBadRequestException.fieldNull("field null", "name");
    }

    @GetMapping("/phone-blank")
    void throwPhoneBlank() {
      throw ProfileBadRequestException.phoneBlank("phone blank");
    }

    @GetMapping("/email-same")
    void throwEmailSame() {
      throw ProfileBadRequestException.emailSame("email same");
    }

    @GetMapping("/email-invalid")
    void throwEmailInvalid() {
      throw ProfileBadRequestException.emailInvalid("email invalid");
    }

    @GetMapping("/email-required")
    void throwEmailRequired() {
      throw ProfileBadRequestException.emailRequired("email required");
    }
  }
}
