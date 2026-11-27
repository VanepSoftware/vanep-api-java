package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ValidationProblemDetailsFactoryTest {

  private final ValidationProblemDetailsFactory factory = new ValidationProblemDetailsFactory();

  @Test
  void ordersRequiredConstraintsBeforeOtherConstraintsAndFallsBackToValidationFailedCode()
      throws NoSuchMethodException {
    BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "payload");
    result.addError(new ObjectError("payload", "Object invalid"));
    result.addError(
        new FieldError("payload", "zeta", null, false, new String[] {"Size"}, null, "Too short"));
    result.addError(
        new FieldError(
            "payload", "alpha", null, false, new String[] {"NotBlank"}, null, "Required"));

    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(stubParameter(), result);

    ProblemDetail detail = factory.create(exception);

    assertThat(detail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(detail.getDetail()).isEqualTo("Required");
    assertThat(detail.getProperties()).containsEntry("code", ValidationProblemDetailsFactory.CODE);
    assertThat(detail.getProperties()).containsEntry("field", "alpha");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> errors =
        (List<Map<String, String>>) detail.getProperties().get("errors");
    assertThat(errors).hasSize(3);
    assertThat(errors.get(0))
        .containsEntry("field", "alpha")
        .containsEntry("code", "validation.failed");
    assertThat(errors.get(1))
        .containsEntry("field", "zeta")
        .containsEntry("code", "validation.failed");
    assertThat(errors.get(2)).doesNotContainKey("field").containsEntry("message", "Object invalid");
  }

  @Test
  void ignoresBuiltInJakartaAndHibernateMessageTemplatesAndUsesCustomDomainKey()
      throws NoSuchMethodException {
    BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "payload");
    FieldError builtIn =
        new FieldError(
            "payload", "a", null, false, new String[] {"NotNull"}, null, "must not be null");
    builtIn.wrap(stubViolation("{jakarta.validation.constraints.NotNull.message}"));
    FieldError hibernate =
        new FieldError(
            "payload", "b", null, false, new String[] {"Length"}, null, "invalid length");
    hibernate.wrap(stubViolation("{org.hibernate.validator.constraints.Length.message}"));
    FieldError keyed =
        new FieldError("payload", "c", null, false, new String[] {"Min"}, null, "Amount invalid");
    keyed.wrap(stubViolation("{contract.amount.invalid}"));
    result.addError(hibernate);
    result.addError(builtIn);
    result.addError(keyed);

    ProblemDetail detail =
        factory.create(new MethodArgumentNotValidException(stubParameter(), result));

    @SuppressWarnings("unchecked")
    List<Map<String, String>> errors =
        (List<Map<String, String>>) detail.getProperties().get("errors");
    assertThat(errors)
        .extracting(e -> e.get("field") + ":" + e.get("code"))
        .containsExactly("a:validation.failed", "b:validation.failed", "c:contract.amount.invalid");
  }

  private static MethodParameter stubParameter() throws NoSuchMethodException {
    return new MethodParameter(
        ValidationProblemDetailsFactoryTest.class.getDeclaredMethod("stubEndpoint", Object.class),
        0);
  }

  @SuppressWarnings("unused")
  private void stubEndpoint(Object payload) {}

  private static ConstraintViolation<?> stubViolation(String messageTemplate) {
    return new ConstraintViolation<>() {
      @Override
      public String getMessage() {
        return messageTemplate;
      }

      @Override
      public String getMessageTemplate() {
        return messageTemplate;
      }

      @Override
      public Object getRootBean() {
        return null;
      }

      @Override
      public Class<Object> getRootBeanClass() {
        return Object.class;
      }

      @Override
      public Object getLeafBean() {
        return null;
      }

      @Override
      public Object[] getExecutableParameters() {
        return new Object[0];
      }

      @Override
      public Object getExecutableReturnValue() {
        return null;
      }

      @Override
      public Path getPropertyPath() {
        return null;
      }

      @Override
      public Object getInvalidValue() {
        return null;
      }

      @Override
      public ConstraintDescriptor<?> getConstraintDescriptor() {
        return null;
      }

      @Override
      public <U> U unwrap(Class<U> type) {
        return null;
      }
    };
  }
}
