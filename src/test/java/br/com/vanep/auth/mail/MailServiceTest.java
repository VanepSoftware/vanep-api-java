package br.com.vanep.auth.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.service.JobService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MailServiceTest {

  @Test
  void sendingOnlyQueuesASendEmailJob() {
    JobService jobs = mock(JobService.class);

    new MailService(jobs)
        .send("to@vanep.com", "Assunto", "email/verification", Map.of("name", "X"));

    ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
    verify(jobs).enqueue(eq(JobType.SEND_EMAIL), payload.capture());
    assertThat(payload.getValue())
        .isEqualTo(
            new SendEmailJobHandler.Payload(
                "to@vanep.com", "Assunto", "email/verification", Map.of("name", "X")));
  }
}
