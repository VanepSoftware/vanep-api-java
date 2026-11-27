package br.com.vanep.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.vanep.shared.error.ApiProblemException;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiErrorAdviceTest {

  private static final Locale PT_BR = Locale.of("pt", "BR");

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(PT_BR);
    ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
    messages.setBasename("messages");
    messages.setDefaultEncoding("UTF-8");
    messages.setFallbackToSystemLocale(false);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ValidationFixtureController())
            .setControllerAdvice(
                new ApiErrorAdvice(new ValidationProblemDetailsFactory(), messages))
            .setLocaleResolver(new FixedLocaleResolver(PT_BR))
            .build();
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void returnsAllFieldErrorsInDeterministicOrderAndSelectsRequiredError() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alpha\":\"\",\"beta\":\"long\",\"zebra\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Bad Request"))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.detail").value("Zebra is required."))
        .andExpect(jsonPath("$.field").value("zebra"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("zebra"))
        .andExpect(jsonPath("$.errors[0].code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].message").value("Zebra is required."))
        .andExpect(jsonPath("$.errors[1].field").value("alpha"))
        .andExpect(jsonPath("$.errors[2].field").value("beta"))
        .andExpect(jsonPath("$.errors[2].message").value("Beta accepts at most 3 characters."))
        .andExpect(jsonPath("$.instance").value("/__test/validation/fields"));
  }

  @Test
  void extractsMessageTemplateKeyAsValidationItemCode() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/keyed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.field").value("amount"))
        .andExpect(jsonPath("$.errors[0].field").value("amount"))
        .andExpect(jsonPath("$.errors[0].code").value("contract.amount.invalid"));
  }

  @Test
  void preservesTheCompleteNestedFieldPath() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/nested")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":{\"zipCode\":\"\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.detail").value("ZIP code is required."))
        .andExpect(jsonPath("$.field").value("address.zipCode"))
        .andExpect(jsonPath("$.errors[0].field").value("address.zipCode"));
  }

  @Test
  void returnsAnObjectErrorWithoutAField() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/object")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"anything\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.detail").value("The object is invalid."))
        .andExpect(jsonPath("$.field").doesNotExist())
        .andExpect(jsonPath("$.errors[0].field").doesNotExist())
        .andExpect(jsonPath("$.errors[0].code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].message").value("The object is invalid."));
  }

  @Test
  void translatesApiProblemExceptionWithCodeAndExtras() throws Exception {
    mockMvc
        .perform(get("/__test/validation/problem"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("user.profile.name.cooldown"))
        .andExpect(jsonPath("$.field").value("name"))
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  void unreadableBodyReturnsRequestBodyUnreadableCode() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.body_unreadable"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Não foi possível ler o corpo da requisição. Verifique o formato do JSON enviado."));
  }

  @Test
  void missingOrInvalidParameterReturnsParameterInvalidCode() throws Exception {
    mockMvc
        .perform(get("/__test/validation/param"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.parameter_invalid"));

    mockMvc
        .perform(get("/__test/validation/param").param("count", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.parameter_invalid"));
  }

  @Test
  void methodNotAllowedAndUnsupportedMediaTypeIncludeGenericCodes() throws Exception {
    mockMvc
        .perform(get("/__test/validation/fields"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("request.method_not_allowed"));

    mockMvc
        .perform(
            post("/__test/validation/fields").contentType(MediaType.TEXT_PLAIN).content("hello"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("request.media_type_unsupported"));
  }

  @Test
  void noResourceFoundReturnsRouteNotFoundCode() throws Exception {
    mockMvc
        .perform(get("/__test/validation/missing-route"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("request.route_not_found"));
  }

  @Test
  void accessDeniedReturnsAuthForbiddenCode() throws Exception {
    mockMvc
        .perform(get("/__test/validation/forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("auth.forbidden"))
        .andExpect(jsonPath("$.detail").value("Você não tem permissão para realizar esta ação."));
  }

  @Test
  void unexpectedExceptionReturnsSafeInternalErrorWithoutLeakingInternals() throws Exception {
    mockMvc
        .perform(get("/__test/validation/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("internal.error"))
        .andExpect(
            jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente em instantes."))
        .andExpect(jsonPath("$.detail", not(containsString("SELECT"))))
        .andExpect(jsonPath("$.detail", not(containsString("IllegalStateException"))));
  }

  @RestController
  @RequestMapping("/__test/validation")
  static class ValidationFixtureController {

    @PostMapping("/fields")
    void validateFields(@Valid @RequestBody FieldRequest request) {}

    @PostMapping("/keyed")
    void validateKeyed(@Valid @RequestBody KeyedRequest request) {}

    @PostMapping("/nested")
    void validateNested(@Valid @RequestBody NestedContainerRequest request) {}

    @PostMapping("/object")
    void validateObject(@Valid @RequestBody ObjectRequest request) {}

    @GetMapping("/problem")
    void throwProblem() {
      throw ApiProblemException.conflict("user.profile.name.cooldown").with("field", "name");
    }

    @GetMapping("/param")
    void requireParam(@RequestParam int count) {}

    @GetMapping("/missing-route")
    void missingRoute() throws NoResourceFoundException {
      throw new NoResourceFoundException(
          org.springframework.http.HttpMethod.GET, "/__test/validation/missing-route", "");
    }

    @GetMapping("/forbidden")
    void forbidden() {
      throw new AccessDeniedException("denied");
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("SELECT * FROM users WHERE secret='123'");
    }
  }

  record FieldRequest(
      @Size(min = 3, message = "Alpha must contain at least 3 characters.") String alpha,
      @Size(max = 3, message = "Beta accepts at most {max} characters.") String beta,
      @NotBlank(message = "Zebra is required.") String zebra) {}

  record KeyedRequest(
      @DecimalMin(value = "0", inclusive = false, message = "{contract.amount.invalid}")
          BigDecimal amount) {}

  record NestedContainerRequest(@Valid NestedRequest address) {}

  record NestedRequest(@NotBlank(message = "ZIP code is required.") String zipCode) {}

  @InvalidObject
  record ObjectRequest(String value) {}

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = InvalidObjectValidator.class)
  @interface InvalidObject {
    String message() default "The object is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
  }

  public static final class InvalidObjectValidator
      implements ConstraintValidator<InvalidObject, ObjectRequest> {

    @Override
    public boolean isValid(ObjectRequest value, ConstraintValidatorContext context) {
      return false;
    }
  }
}
