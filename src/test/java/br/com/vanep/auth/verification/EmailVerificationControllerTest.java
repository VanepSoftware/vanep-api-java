package br.com.vanep.auth.verification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.com.vanep.config.ApiErrorAdvice;
import br.com.vanep.config.ValidationProblemDetailsFactory;
import br.com.vanep.shared.error.ApiProblemException;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

class EmailVerificationControllerTest {

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private EmailVerificationService verification;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    verification = mock(EmailVerificationService.class);
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new EmailVerificationController(verification))
            .setControllerAdvice(
                new ApiErrorAdvice(new ValidationProblemDetailsFactory(), messages))
            .setLocaleResolver(new FixedLocaleResolver(PT_BR))
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();
  }

  @Test
  void verifyHandlesSuccessInvalidDuplicateAndUnexpectedProblem() throws Exception {
    when(verification.verify("ok")).thenReturn(true);
    mockMvc
        .perform(get("/verify-email").param("token", "ok"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verified"));

    when(verification.verify("bad")).thenReturn(false);
    mockMvc
        .perform(get("/verify-email").param("token", "bad"))
        .andExpect(status().isOk())
        .andExpect(view().name("verify-email"))
        .andExpect(model().attribute("invalid", true));

    when(verification.verify("dup"))
        .thenThrow(ApiProblemException.conflict("auth.signup.email.duplicate"));
    mockMvc
        .perform(get("/verify-email").param("token", "dup"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?email_taken"));

    when(verification.verify("other"))
        .thenThrow(ApiProblemException.badRequest("auth.verification.expired"));
    mockMvc
        .perform(get("/verify-email").param("token", "other"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("auth.verification.expired"));
  }

  @Test
  void resendTriggersServiceOnlyWhenEmailIsNotBlank() throws Exception {
    mockMvc
        .perform(post("/verify-email/resend").param("email", "   "))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verify-sent"));
    verify(verification, never()).resend("   ");

    mockMvc
        .perform(post("/verify-email/resend").param("email", "user@vanep.com"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?verify-sent"));
    verify(verification).resend("user@vanep.com");
  }
}
