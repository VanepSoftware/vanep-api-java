package br.com.vanep.auth.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

class MailServiceTest {

  private final SpringTemplateEngine templateEngine = mock(SpringTemplateEngine.class);

  @SuppressWarnings("unchecked")
  private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(sender);
    return provider;
  }

  @Test
  void sendsWhenEnabledAndSenderPresent() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(sender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<p>oi</p>");

    MailService mail =
        new MailService(provider(sender), templateEngine, true, "no-reply@vanep.com");
    mail.send("to@vanep.com", "Assunto", "email/verification", Map.of("name", "X", "link", "L"));

    verify(sender).send(any(MimeMessage.class));
  }

  @Test
  void doesNotSendWhenDisabled() {
    JavaMailSender sender = mock(JavaMailSender.class);
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<p>oi</p>");

    MailService mail =
        new MailService(provider(sender), templateEngine, false, "no-reply@vanep.com");
    mail.send("to@vanep.com", "Assunto", "email/verification", Map.of("link", "L"));

    verify(sender, never()).send(any(MimeMessage.class));
  }

  @Test
  void doesNotFailWhenNoSenderAvailable() {
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<p>oi</p>");
    MailService mail = new MailService(provider(null), templateEngine, true, "no-reply@vanep.com");
    mail.send("to@vanep.com", "Assunto", "email/verification", Map.of("link", "L"));
  }
}
