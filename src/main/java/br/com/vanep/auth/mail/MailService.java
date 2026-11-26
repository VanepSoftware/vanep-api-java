package br.com.vanep.auth.mail;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.service.JobService;
import java.util.Map;
import org.springframework.stereotype.Service;

// Every e-mail goes through the job queue: the caller's transaction only records a SEND_EMAIL job,
// and the worker renders and sends it (SendEmailJobHandler). The subject is resolved here, in the
// caller's locale; the template is rendered at send time.
@Service
public class MailService {

  private final JobService jobs;

  public MailService(JobService jobs) {
    this.jobs = jobs;
  }

  // Requires a transaction: the e-mail is only queued if the change that asked for it commits.
  public void send(String to, String subject, String template, Map<String, Object> variables) {
    jobs.enqueue(
        JobType.SEND_EMAIL, new SendEmailJobHandler.Payload(to, subject, template, variables));
  }
}
