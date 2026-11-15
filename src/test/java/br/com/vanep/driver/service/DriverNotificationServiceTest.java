package br.com.vanep.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.mail.MailService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.enums.DriverRejectionReason;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

  @Mock private DriverRepository drivers;
  @Mock private MailService mailService;
  @Mock private MessageSource messages;

  @Captor private ArgumentCaptor<Map<String, Object>> variablesCaptor;

  private DriverNotificationService service;
  private DriverModel driver;
  private UserModel user;

  @BeforeEach
  void setUp() {
    service = new DriverNotificationService(drivers, mailService, messages);

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

    driver.setRejectionReasonCode(DriverRejectionReason.OTHER);
    driver.setRejectionReason("Documento CRLV ilegível ou vencido.");
    service.notifyRejection(driver);

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

    service.notifyRejection(driver);

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
  void aCodedRejectionSendsTheReasonLabelAndTheMessageBelowIt() {
    when(messages.getMessage(eq("driver.rejection.email.subject"), any(), any()))
        .thenReturn("Atualização sobre seu cadastro — Vanep");
    when(messages.getMessage(eq("driver.rejection_reason.DATA_MISMATCH"), any(), any()))
        .thenReturn("Os dados do cadastro não conferem com os documentos.");
    driver.setRejectionReasonCode(DriverRejectionReason.DATA_MISMATCH);
    driver.setRejectionReason("O nome difere da CNH.");

    service.notifyRejection(driver);

    verify(mailService)
        .send(
            eq("carlos.driver@vanep.com"),
            any(),
            eq("email/driver-rejected"),
            variablesCaptor.capture());
    assertThat(variablesCaptor.getValue())
        .containsEntry("reason", "Os dados do cadastro não conferem com os documentos.")
        .containsEntry("details", "O nome difere da CNH.");
  }

  @Test
  void theDecisionEventsAreMailedFromTheStoredDriver() {
    driver.setRejectionReasonCode(DriverRejectionReason.OTHER);
    when(drivers.findByToken("driver-token-123")).thenReturn(Optional.of(driver));

    service.sendApproval(new DriverReviewEvent.Approved("driver-token-123"));
    service.sendRejection(new DriverReviewEvent.Rejected("driver-token-123"));

    verify(mailService)
        .send(eq("carlos.driver@vanep.com"), any(), eq("email/driver-approved"), any());
    verify(mailService)
        .send(eq("carlos.driver@vanep.com"), any(), eq("email/driver-rejected"), any());
  }

  @Test
  void notifyRejectionThrowsWhenDriverIsNull() {
    assertThatThrownBy(() -> service.notifyRejection(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("driver must not be null");
  }

  @Test
  void notifyRejectionThrowsWhenDriverUserIsNull() {
    driver.setUser(null);
    assertThatThrownBy(() -> service.notifyRejection(driver))
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
