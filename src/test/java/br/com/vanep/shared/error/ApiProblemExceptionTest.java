package br.com.vanep.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.config.ApiErrorAdvice;
import br.com.vanep.config.ValidationProblemDetailsFactory;
import br.com.vanep.driverdocument.model.DriverDocumentModel;
import br.com.vanep.media.model.MediaFileModel;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
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
    ApiErrorAdvice advice = new ApiErrorAdvice(new ValidationProblemDetailsFactory(), messages);
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
  void responseStatusExceptionKeepsDetailAndReceivesGenericCode() throws Exception {
    mockMvc
        .perform(get("/__test/problem/legacy").locale(PT_BR))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Not here"))
        .andExpect(jsonPath("$.code").value("request.route_not_found"));
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

  @Test
  void aRequestOverTheUploadLimitIsTooLargeWithTheMediaCode() throws Exception {
    mockMvc
        .perform(get("/__test/problem/upload-too-large").locale(PT_BR))
        .andExpect(status().isContentTooLarge())
        .andExpect(jsonPath("$.code").value("media.file.too_large"))
        .andExpect(jsonPath("$.detail").value("O arquivo é maior que o permitido."));
  }

  @Test
  void factoriesSetStatusCodeKeyAndDetailMessageCode() {
    ApiProblemException conflict = ApiProblemException.conflict("service_request.open_exists");
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getCode()).isEqualTo("service_request.open_exists");
    assertThat(conflict.getMessageKey()).isEqualTo("service_request.open_exists");
    assertThat(conflict.getDetailMessageCode()).isEqualTo("service_request.open_exists");
    assertThat(conflict.getBody().getProperties())
        .containsEntry("code", "service_request.open_exists");

    assertThat(ApiProblemException.badRequest("a").getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ApiProblemException.unauthorized("b").getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(ApiProblemException.forbidden("c").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(ApiProblemException.notFound("d").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(ApiProblemException.unprocessable("e").getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(ApiProblemException.tooManyRequests("f").getStatusCode())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(ApiProblemException.serviceUnavailable("g").getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void supportsMessageArgumentsAndExtraProperties() {
    Instant retryAfter = Instant.parse("2026-11-24T12:00:00Z");
    RuntimeException cause = new IllegalStateException("root");

    ApiProblemException exception =
        ApiProblemException.conflict("user.profile.name.cooldown", 30)
            .with("field", "name")
            .with("ignored", null)
            .withAll(Map.of("retryAfter", retryAfter));

    assertThat(exception.getDetailMessageArguments()).containsExactly(30);
    assertThat(exception.getMessage()).isEqualTo("409 CONFLICT user.profile.name.cooldown");
    assertThat(exception.getBody().getProperties())
        .containsEntry("code", "user.profile.name.cooldown")
        .containsEntry("field", "name")
        .containsEntry("retryAfter", retryAfter)
        .doesNotContainKey("ignored");

    ApiProblemException withCause =
        ApiProblemException.of(HttpStatus.BAD_GATEWAY, "request.error", cause, "x");
    assertThat(withCause.getCause()).isSameAs(cause);
    assertThat(withCause.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    assertThat(withCause.getCode()).isEqualTo("request.error");
    assertThat(withCause.getDetailMessageArguments()).containsExactly("x");
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

    @GetMapping("/upload-too-large")
    void uploadTooLarge() {
      throw new MaxUploadSizeExceededException(11L * 1024 * 1024);
    }

    @GetMapping("/legacy")
    void legacy() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not here");
    }
  }
}
