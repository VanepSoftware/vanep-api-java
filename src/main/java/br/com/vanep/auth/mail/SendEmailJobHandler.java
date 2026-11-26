package br.com.vanep.auth.mail;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobHandler;
import br.com.vanep.job.handler.PermanentJobFailureException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

// A failed send (MailException) is thrown on: SEND_EMAIL has a single attempt, so the job moves
// to failed_jobs and is retried from there. A message that cannot even be built (a malformed
// address) will never go out and fails permanently.
@Component
public class SendEmailJobHandler implements JobHandler<SendEmailJobHandler.Payload> {

  public record Payload(
      String to, String subject, String template, Map<String, Object> variables) {}

  private static final Logger log = LoggerFactory.getLogger(SendEmailJobHandler.class);
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final ObjectProvider<JavaMailSender> mailSender;
  private final SpringTemplateEngine templateEngine;
  private final boolean enabled;
  private final String from;

  public SendEmailJobHandler(
      ObjectProvider<JavaMailSender> mailSender,
      SpringTemplateEngine templateEngine,
      @Value("${vanep.mail.enabled:true}") boolean enabled,
      @Value("${vanep.mail.from:no-reply@vanep.com.br}") String from) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.enabled = enabled;
    this.from = from;
  }

  @Override
  public JobType type() {
    return JobType.SEND_EMAIL;
  }

  @Override
  public Class<Payload> payloadType() {
    return Payload.class;
  }

  @Override
  public void perform(Payload payload) {
    Context context = new Context(PT_BR);
    if (payload.variables() != null) {
      context.setVariables(payload.variables());
    }
    String html = templateEngine.process(payload.template(), context);

    JavaMailSender sender = mailSender.getIfAvailable();
    if (!enabled || sender == null) {
      log.info(
          "[mail desabilitado] para={} assunto=\"{}\"\n{}", payload.to(), payload.subject(), html);
      return;
    }
    MimeMessage message = sender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setTo(payload.to());
      helper.setFrom(from);
      helper.setSubject(payload.subject());
      helper.setText(html, true);
    } catch (MessagingException unbuildable) {
      throw new PermanentJobFailureException(
          "E-mail \"%s\" could not be built".formatted(payload.subject()), unbuildable);
    }
    sender.send(message);
  }
}
