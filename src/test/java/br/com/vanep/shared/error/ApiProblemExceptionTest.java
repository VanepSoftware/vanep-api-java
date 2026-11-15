package br.com.vanep.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.config.ApiErrorAdvice;
import br.com.vanep.config.ValidationProblemDetailsFactory;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.model.MediaFileModel;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiProblemExceptionTest {

  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    ApiErrorAdvice advice = new ApiErrorAdvice(new ValidationProblemDetailsFactory());
    advice.setMessageSource(messages);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProblemFixtureController())
            .setControllerAdvice(advice)
            .build();
  }

  @Test
  void theResponseHasTheStatusTheCodeAndTheTranslatedDetail() throws Exception {
    mockMvc
        .perform(get("/__test/problem/plain").locale(PT_BR))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("driver_cnh.registration.duplicate"))
        .andExpect(
            jsonPath("$.detail")
                .value("Já existe uma CNH cadastrada com este número de registro."));
  }

  @Test
  void theDetailIsFormattedWithTheArguments() throws Exception {
    mockMvc
        .perform(get("/__test/problem/arguments").locale(PT_BR))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("user.profile.name.too_long"))
        .andExpect(jsonPath("$.detail").value("O nome deve ter no máximo 120 caracteres."));
  }

  @Test
  void extraPropertiesGoNextToTheCode() throws Exception {
    mockMvc
        .perform(get("/__test/problem/extras").locale(PT_BR))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("document.review.file_required"))
        .andExpect(jsonPath("$.blockers[0]").value("CRLV"))
        .andExpect(jsonPath("$.blockers.length()").value(1));
  }

  @Test
  void anOldResponseStatusExceptionKeepsTheResponseOfToday() throws Exception {
    mockMvc
        .perform(get("/__test/problem/legacy").locale(PT_BR))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Not here"))
        .andExpect(jsonPath("$.code").doesNotExist());
  }

  @Test
  void aLostOptimisticLockOnAnotherEntityIsAPlainConflict() throws Exception {
    mockMvc
        .perform(get("/__test/problem/other-stale").locale(PT_BR))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("request.conflict"));
  }

  @Test
  void aLostOptimisticLockIsAStaleDecision() throws Exception {
    mockMvc
        .perform(get("/__test/problem/stale").locale(PT_BR))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("document.review.stale"))
        .andExpect(jsonPath("$.detail").value("Este envio mudou desde que você abriu."));
  }

  @RestController
  @RequestMapping("/__test/problem")
  static class ProblemFixtureController {

    @GetMapping("/plain")
    void plain() {
      throw ApiProblemException.conflict("driver_cnh.registration.duplicate");
    }

    @GetMapping("/arguments")
    void arguments() {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, "user.profile.name.too_long", 120);
    }

    @GetMapping("/extras")
    void extras() {
      throw ApiProblemException.unprocessable("document.review.file_required")
          .withProperty("blockers", List.of("CRLV"));
    }

    @GetMapping("/stale")
    void stale() {
      throw new ObjectOptimisticLockingFailureException(DriverDocumentModel.class.getName(), 1L);
    }

    @GetMapping("/other-stale")
    void otherStale() {
      throw new ObjectOptimisticLockingFailureException(MediaFileModel.class.getName(), 1L);
    }

    @GetMapping("/legacy")
    void legacy() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not here");
    }
  }
}
