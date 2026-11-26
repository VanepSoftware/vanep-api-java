package br.com.vanep.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.PermanentJobFailureException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

class SendEmailJobHandlerTest {

  private static final SendEmailJobHandler.Payload PAYLOAD =
      new SendEmailJobHandler.Payload(
          "to@vanep.com", "Assunto", "email/verification", Map.of("name", "X", "link", "L"));

  private final SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);
  private final JavaMailSender sender = mock(JavaMailSender.class);

  @BeforeEach
  void setUp() {
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<p>oi</p>");
    when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
  }

  @SuppressWarnings("unchecked")
  private SendEmailJobHandler handler(JavaMailSender available, boolean enabled) {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(available);
    return new SendEmailJobHandler(provider, templateEngine, enabled, "no-reply@vanep.com");
  }

  @Test
  void handlesSendEmailJobs() {
    assertThat(handler(sender, true).type()).isEqualTo(JobType.SEND_EMAIL);
  }

  @Test
  void rendersTheTemplateWithTheVariablesAndSends() throws Exception {
    handler(sender, true).perform(PAYLOAD);

    ArgumentCaptor<Context> context = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine).process(eq("email/verification"), context.capture());
    assertThat(context.getValue().getVariable("link")).isEqualTo("L");
    ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
    verify(sender).send(message.capture());
    assertThat(message.getValue().getSubject()).isEqualTo("Assunto");
    assertThat(message.getValue().getAllRecipients()[0].toString()).isEqualTo("to@vanep.com");
  }

  @Test
  void aFailedSendIsThrownSoTheJobGoesToFailedJobs() {
    doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));

    assertThatThrownBy(() -> handler(sender, true).perform(PAYLOAD))
        .isInstanceOf(MailSendException.class);
  }

  @Test
  void aMalformedAddressFailsPermanently() {
    SendEmailJobHandler.Payload malformed =
        new SendEmailJobHandler.Payload(
            "not an address@@", "Assunto", "email/verification", Map.of());

    assertThatThrownBy(() -> handler(sender, true).perform(malformed))
        .isInstanceOf(PermanentJobFailureException.class);
    verify(sender, never()).send(any(MimeMessage.class));
  }

  @Test
  void doesNotSendWhenDisabled() {
    handler(sender, false).perform(PAYLOAD);

    verify(sender, never()).send(any(MimeMessage.class));
  }

  @Test
  void doesNotFailWhenNoSenderAvailable() {
    handler(null, true).perform(PAYLOAD);
  }
}
