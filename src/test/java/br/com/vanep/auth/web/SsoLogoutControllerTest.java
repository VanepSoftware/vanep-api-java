package br.com.vanep.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.config.ApiErrorAdvice;
import br.com.vanep.config.ValidationProblemDetailsFactory;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

class SsoLogoutControllerTest {

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  @Test
  void redirectsToAllowedOrDefaultUriAndInvalidatesSession() throws Exception {
    MockMvc mockMvc =
        mockMvcFor(List.of("http://localhost:3000", "https://app.vanep.com.br/logout"));
    MockHttpSession session = new MockHttpSession();

    mockMvc
        .perform(
            get("/auth/sso-logout")
                .param("redirect_uri", "https://app.vanep.com.br/logout")
                .session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("https://app.vanep.com.br/logout"));
    assertThat(session.isInvalid()).isTrue();

    mockMvc
        .perform(get("/auth/sso-logout").param("redirect_uri", "https://evil.example"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("http://localhost:3000"));
  }

  @Test
  void rejectsWhenNoAllowedRedirectUriExistsWithProblemDetail() throws Exception {
    MockMvc mockMvc = mockMvcFor(List.of("   "));

    mockMvc
        .perform(get("/auth/sso-logout").param("redirect_uri", "https://evil.example"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("auth.sso_logout.redirect_uri_invalid"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  private static MockMvc mockMvcFor(List<String> allowedRedirectUris) {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    return MockMvcBuilders.standaloneSetup(new SsoLogoutController(allowedRedirectUris))
        .setControllerAdvice(new ApiErrorAdvice(new ValidationProblemDetailsFactory(), messages))
        .setLocaleResolver(new FixedLocaleResolver(PT_BR))
        .build();
  }
}
