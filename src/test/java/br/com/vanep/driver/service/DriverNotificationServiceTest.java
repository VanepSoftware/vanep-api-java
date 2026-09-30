package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class DriverNotificationServiceTest {

  @Mock private MailService mailService;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private DriverNotificationService service;
  private DriverModel driver;
  private UserModel user;

  @BeforeEach
  void setUp() {
    service = new DriverNotificationService(mailService, messages);

    user = new UserModel();
    user.setId(10L);
    user.setName("Carlos Driver");
    user.setEmail("carlos.driver@vanep.com");

    driver = new DriverModel();
    driver.setId(1L);
    driver.setToken("driver-token-123");
    driver.setUser(user);
  }

  @Test
  void notifyApprovalSendsEmailWithCorrectParameters() {
    when(messages.getMessage(eq("driver.approval.email.subject"), any(), any()))
        .thenReturn("Cadastro aprovado — Vanep");

    service.notifyApproval(driver);

    verify(mailService)
        .send(
            eq("carlos.driver@vanep.com"),
            eq("Cadastro aprovado — Vanep"),
            eq("email/driver-approved"),
            variablesCaptor.capture());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat(variables).containsEntry("name", "Carlos Driver");
  }

  @Test
  void notifyApprovalHandlesNullNameGracefully() {
    user.setName(null);
    when(messages.getMessage(eq("driver.approval.email.subject"), any(), any()))
        .thenReturn("Cadastro aprovado — Vanep");

    service.notifyApproval(driver);

    verify(mailService)
        .send(
            eq("carlos.driver@vanep.com"),
            eq("Cadastro aprovado — Vanep"),
            eq("email/driver-approved"),
            variablesCaptor.capture());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat(variables).containsEntry("name", "");
  }

  @Test
  void notifyApprovalThrowsWhenDriverIsNull() {
    assertThatThrownBy(() -> service.notifyApproval(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("driver must not be null");
  }

  @Test
  void notifyApprovalThrowsWhenDriverUserIsNull() {
    driver.setUser(null);
    assertThatThrownBy(() -> service.notifyApproval(driver))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("driver user must not be null");
  }

  @Test
  void notifyRejectionSendsEmailWithCorrectParameters() {
    when(messages.getMessage(eq("driver.rejection.email.subject"), any(), any()))
        .thenReturn("Atualização sobre seu cadastro — Vanep");

    String reason = "Documento CRLV ilegível ou vencido.";
    service.notifyRejection(driver, reason);

    verify(mailService)
        .send(
            eq("carlos.driver@vanep.com"),
            eq("Atualização sobre seu cadastro — Vanep"),
            eq("email/driver-rejected"),
            variablesCaptor.capture());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat(variables)
        .containsEntry("name", "Carlos Driver")
        .containsEntry("reason", "Documento CRLV ilegível ou vencido.");
  }

  @Test
  void notifyRejectionHandlesNullReasonAndNullNameGracefully() {
    user.setName(null);
    when(messages.getMessage(eq("driver.rejection.email.subject"), any(), any()))
        .thenReturn("Atualização sobre seu cadastro — Vanep");

    service.notifyRejection(driver, null);

    verify(mailService)
        .send(
            eq("carlos.driver@vanep.com"),
            eq("Atualização sobre seu cadastro — Vanep"),
            eq("email/driver-rejected"),
            variablesCaptor.capture());

    Map<String, Object> variables = variablesCaptor.getValue();
    assertThat(variables).containsEntry("name", "").containsEntry("reason", "");
  }

  @Test
  void notifyRejectionThrowsWhenDriverIsNull() {
    assertThatThrownBy(() -> service.notifyRejection(null, "some reason"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("driver must not be null");
  }

  @Test
  void notifyRejectionThrowsWhenDriverUserIsNull() {
    driver.setUser(null);
    assertThatThrownBy(() -> service.notifyRejection(driver, "some reason"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("driver user must not be null");
  }

  @Test
  void rendersDriverApprovedTemplateWithoutErrors() {
    SpringTemplateEngine engine = createTemplateEngine();
    Context context = new Context(Locale.forLanguageTag("pt-BR"));
    context.setVariables(Map.of("name", "Carlos Driver"));

    String rendered = engine.process("email/driver-approved", context);

    assertThat(rendered).contains("Cadastro aprovado!");
    assertThat(rendered).contains("Olá, Carlos Driver!");
    assertThat(rendered).contains("Equipe Vanep.");
  }

  @Test
  void rendersDriverRejectedTemplateWithoutErrors() {
    SpringTemplateEngine engine = createTemplateEngine();
    Context context = new Context(Locale.forLanguageTag("pt-BR"));
    context.setVariables(
        Map.of("name", "Carlos Driver", "reason", "Documento CRLV ilegível ou vencido."));

    String rendered = engine.process("email/driver-rejected", context);

    assertThat(rendered).contains("Atualização sobre seu cadastro");
    assertThat(rendered).contains("Olá, Carlos Driver!");
    assertThat(rendered).contains("Documento CRLV ilegível ou vencido.");
    assertThat(rendered).contains("Equipe Vanep.");
  }

  private SpringTemplateEngine createTemplateEngine() {
    SpringTemplateEngine engine = new SpringTemplateEngine();
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    engine.setTemplateResolver(resolver);
    return engine;
  }
}
