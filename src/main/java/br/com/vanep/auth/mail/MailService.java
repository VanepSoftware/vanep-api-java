package br.com.vanep.auth.mail;

import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);
  private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

  private final ObjectProvider<JavaMailSender> mailSender;
  private final SpringTemplateEngine templateEngine;
  private final boolean enabled;
  private final String from;

  public MailService(
      ObjectProvider<JavaMailSender> mailSender,
      SpringTemplateEngine templateEngine,
      @Value("${vanep.mail.enabled:true}") boolean enabled,
      @Value("${vanep.mail.from:no-reply@vanep.com.br}") String from) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
    this.enabled = enabled;
    this.from = from;
  }

  public void send(String to, String subject, String template, Map<String, Object> variables) {
    Context context = new Context(PT_BR);
    context.setVariables(variables);
    String html = templateEngine.process(template, context);

    JavaMailSender sender = mailSender.getIfAvailable();
    if (!enabled || sender == null) {
      log.info("[mail desabilitado] para={} assunto=\"{}\"\n{}", to, subject, html);
      return;
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
      helper.setTo(to);
      helper.setFrom(from);
      helper.setSubject(subject);
      helper.setText(html, true);
      sender.send(message);
    } catch (Exception ex) {
      log.error("Falha ao enviar e-mail para {}.", to, ex);
    }
  }
}
