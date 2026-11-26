package br.com.vanep.job.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobHandler;
import br.com.vanep.job.handler.JobHandlerRegistry;
import br.com.vanep.job.handler.JobRetryException;
import br.com.vanep.job.handler.PermanentJobFailureException;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.service.JobService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class JobWorkerTest {

  @Mock private JobService jobs;
  @Mock private JobHandlerRegistry handlers;

  private RecordingHandler handler;
  private JobWorker worker;

  @BeforeEach
  void setUp() {
    handler = new RecordingHandler();
    worker = new JobWorker(jobs, handlers, new InlineTransaction(), 20, Duration.ofMinutes(5));
  }

  @Test
  void aSuccessfulJobIsCompleted() {
    JobModel job = job(1L);
    givenHandledWithPayload(job, "payload");

    worker.runJob(job);

    assertThat(handler.performed).containsExactly("payload");
    verify(jobs).completeJob(1L);
  }

  @Test
  void aFailingJobRegistersAFailedAttempt() {
    JobModel job = job(1L);
    givenHandledWithPayload(job, "payload");
    IllegalStateException boom = new IllegalStateException("boom");
    handler.behavior =
        payload -> {
          throw boom;
        };

    worker.runJob(job);

    verify(jobs).registerFailedAttempt(eq(1L), eq(boom), isNull());
    verify(jobs, never()).completeJob(any());
  }

  @Test
  void aRetryRequestCarriesTheNarrowedPayload() {
    JobModel job = job(1L);
    givenHandledWithPayload(job, "payload");
    handler.behavior =
        payload -> {
          throw new JobRetryException("partial", "narrowed");
        };

    worker.runJob(job);

    verify(jobs).registerFailedAttempt(eq(1L), any(JobRetryException.class), eq("narrowed"));
  }

  @Test
  void aPermanentFailureFromTheHandlerSkipsTheRetries() {
    JobModel job = job(1L);
    givenHandledWithPayload(job, "payload");
    handler.behavior =
        payload -> {
          throw new PermanentJobFailureException("gone");
        };

    worker.runJob(job);

    verify(jobs).failPermanently(eq(1L), any(PermanentJobFailureException.class));
    verify(jobs, never()).registerFailedAttempt(any(), any(), any());
  }

  @Test
  void anUnreadablePayloadFailsPermanently() {
    JobModel job = job(1L);
    when(handlers.findHandler(JobType.SEND_PUSH)).thenReturn(Optional.of(handler));
    when(jobs.readPayload(job, String.class)).thenThrow(new PermanentJobFailureException("bad"));

    worker.runJob(job);

    verify(jobs).failPermanently(eq(1L), any(PermanentJobFailureException.class));
    assertThat(handler.performed).isEmpty();
  }

  @Test
  void aTypeWithoutHandlerFailsPermanently() {
    JobModel job = job(1L);
    when(handlers.findHandler(JobType.SEND_PUSH)).thenReturn(Optional.empty());

    worker.runJob(job);

    verify(jobs)
        .failPermanently(eq(1L), argThat(failure -> failure.getMessage().contains("No handler")));
  }

  @Test
  void aCycleRecoversThenRunsEveryClaimedJobEvenIfOneCannotBeSettled() {
    JobModel first = job(1L);
    JobModel second = job(2L);
    when(jobs.claimDueJobs(worker.workerId(), 20)).thenReturn(List.of(first, second));
    givenHandledWithPayload(first, "first");
    givenHandledWithPayload(second, "second");
    doThrow(new IllegalStateException("db down")).when(jobs).completeJob(1L);

    worker.runCycle();

    verify(jobs).recoverStuckJobs(Duration.ofMinutes(5), 20);
    assertThat(handler.performed).containsExactly("first", "second");
    verify(jobs).completeJob(2L);
  }

  @Test
  void theWorkerIdIncludesTheHostAndARandomSuffix() {
    String other = JobWorker.buildWorkerId();

    assertThat(worker.workerId()).isNotEqualTo(other);
    assertThat(worker.workerId()).matches(".+-[0-9a-f]{8}");
  }

  private void givenHandledWithPayload(JobModel job, String payload) {
    when(handlers.findHandler(JobType.SEND_PUSH)).thenReturn(Optional.of(handler));
    when(jobs.readPayload(job, String.class)).thenReturn(payload);
  }

  private static JobModel job(Long id) {
    JobModel job = new JobModel();
    job.setId(id);
    job.setToken("job-" + id);
    job.setType(JobType.SEND_PUSH);
    return job;
  }

  static class RecordingHandler implements JobHandler<String> {

    final java.util.List<String> performed = new java.util.ArrayList<>();
    Consumer<String> behavior = payload -> {};

    @Override
    public JobType type() {
      return JobType.SEND_PUSH;
    }

    @Override
    public Class<String> payloadType() {
      return String.class;
    }

    @Override
    public void perform(String payload) {
      performed.add(payload);
      behavior.accept(payload);
    }
  }

  static class InlineTransaction extends TransactionTemplate {

    @Override
    public <T> T execute(TransactionCallback<T> action) {
      return action.doInTransaction(null);
    }
  }
}
