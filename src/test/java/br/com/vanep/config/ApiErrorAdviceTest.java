package br.com.vanep.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiErrorAdviceTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ValidationFixtureController())
            .setControllerAdvice(new ApiErrorAdvice(new ValidationProblemDetailsFactory()))
            .build();
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
        .andExpect(jsonPath("$.detail").value("Zebra is required."))
        .andExpect(jsonPath("$.field").value("zebra"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("zebra"))
        .andExpect(jsonPath("$.errors[0].message").value("Zebra is required."))
        .andExpect(jsonPath("$.errors[1].field").value("alpha"))
        .andExpect(jsonPath("$.errors[2].field").value("beta"))
        .andExpect(jsonPath("$.errors[2].message").value("Beta accepts at most 3 characters."))
        .andExpect(jsonPath("$.instance").value("/__test/validation/fields"));
  }

  @Test
  void preservesTheCompleteNestedFieldPath() throws Exception {
    mockMvc
        .perform(
            post("/__test/validation/nested")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":{\"zipCode\":\"\"}}"))
        .andExpect(status().isBadRequest())
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
        .andExpect(jsonPath("$.detail").value("The object is invalid."))
        .andExpect(jsonPath("$.field").doesNotExist())
        .andExpect(jsonPath("$.errors[0].field").doesNotExist())
        .andExpect(jsonPath("$.errors[0].message").value("The object is invalid."));
  }

  @RestController
  @RequestMapping("/__test/validation")
  static class ValidationFixtureController {

    @PostMapping("/fields")
    void validateFields(@Valid @RequestBody FieldRequest request) {}

    @PostMapping("/nested")
    void validateNested(@Valid @RequestBody NestedContainerRequest request) {}

    @PostMapping("/object")
    void validateObject(@Valid @RequestBody ObjectRequest request) {}
  }

  record FieldRequest(
      @Size(min = 3, message = "Alpha must contain at least 3 characters.") String alpha,
      @Size(max = 3, message = "Beta accepts at most {max} characters.") String beta,
      @NotBlank(message = "Zebra is required.") String zebra) {}

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
