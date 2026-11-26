package br.com.vanep.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.job.enums.JobType;
import br.com.vanep.job.handler.JobHandlerRegistry;
import br.com.vanep.job.model.FailedJobModel;
import br.com.vanep.job.model.JobModel;
import br.com.vanep.job.repository.FailedJobRepository;
import br.com.vanep.job.repository.JobRepository;
import br.com.vanep.job.service.JobService;
import br.com.vanep.job.worker.JobWorker;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.repository.NotificationRepository;
import br.com.vanep.notification.service.PushNotificationService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationDeliveryTest {

  @MockitoBean private PushNotificationService push;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private ApplicationContext context;
  @Autowired private TransactionTemplate transaction;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;
  @Autowired private NotificationRepository notifications;
  @Autowired private JobRepository jobs;
  @Autowired private FailedJobRepository failedJobs;
  @Autowired private JobService jobService;
  @Autowired private JobHandlerRegistry handlers;

  private DriverModel carlos;

  @BeforeEach
  void setUp() {
    UserModel user = new UserModel();
    user.setType(UserType.DRIVER);
    user.setName("Carlos");
    user.setEmail("carlos@vanep.com");
    user.setDocument("52998224725");
    user.setVerified(true);
    user.setTermsAcceptedAt(Instant.now());
    DriverModel driver = new DriverModel();
    driver.setUser(users.save(user));
    driver.setBasePrice(new BigDecimal("100.00"));
    driver.setApprovalStatus(DriverApprovalStatus.APPROVED);
    carlos = drivers.save(driver);
  }

  @Test
  void theNotificationAndItsPushJobCommitWithTheEvent() {
    approveCarlos();

    NotificationModel stored = notifications.findAll().getFirst();
    assertThat(stored.getUserId()).isEqualTo(carlos.getUser().getId());
    assertThat(stored.getType()).isEqualTo(NotificationType.DRIVER_APPROVED);
    assertThat(stored.getTitle()).isEqualTo("Cadastro aprovado");
    JobModel job = jobs.findAll().getFirst();
    assertThat(job.findJobType()).contains(JobType.SEND_PUSH);
    assertThat(job.getPayload()).containsEntry("notificationToken", stored.getToken());
  }

  @Test
  void aRolledBackTransactionLeavesNeitherNotificationNorJob() {
    transaction.executeWithoutResult(
        status -> {
          events.publishEvent(new DriverReviewEvent.Approved(carlos.getToken()));
          status.setRollbackOnly();
        });

    assertThat(notifications.count()).isZero();
    assertThat(jobs.count()).isZero();
  }

  @Test
  void anUnknownRecipientDoesNotBreakTheBusinessTransaction() {
    transaction.executeWithoutResult(
        status -> {
          carlos.setBasePrice(new BigDecimal("150.00"));
          drivers.save(carlos);
          events.publishEvent(new DriverReviewEvent.Approved("no-such-driver"));
        });

    assertThat(drivers.findById(carlos.getId()).orElseThrow().getBasePrice())
        .isEqualByComparingTo("150.00");
    assertThat(notifications.count()).isZero();
    assertThat(jobs.count()).isZero();
  }

  @Test
  void theWorkerSendsThePushAndRemovesTheJob() {
    approveCarlos();
    NotificationModel stored = notifications.findAll().getFirst();
    when(push.sendToUser(anyLong(), any(), any(), any(), isNull())).thenReturn(List.of());

    newWorker().runCycle();

    verify(push)
        .sendToUser(
            eq(carlos.getUser().getId()),
            eq("Cadastro aprovado"),
            any(),
            argThat(data -> stored.getToken().equals(data.get("notificationToken"))),
            isNull());
    assertThat(jobs.count()).isZero();
    assertThat(failedJobs.count()).isZero();
    assertThat(notifications.count()).isOne();
  }

  @Test
  void aTransientPushFailureKeepsTheJobForRetryWithoutStoringTheNotificationAgain() {
    approveCarlos();
    when(push.sendToUser(anyLong(), any(), any(), any(), isNull()))
        .thenThrow(new IllegalStateException("fcm down"));

    newWorker().runCycle();

    JobModel retried = jobs.findAll().getFirst();
    assertThat(retried.getAttempts()).isOne();
    assertThat(retried.getRunAt()).isAfter(Instant.now());
    assertThat(notifications.count()).isOne();
  }

  @Test
  void aJobOfARemovedTypeFailsPermanentlyWithoutBlockingTheQueue() {
    JobModel removed = new JobModel();
    removed.setToken("removed-type");
    removed.setType("REMOVED_TYPE");
    removed.setPayload(Map.of());
    removed.setMaxAttempts(5);
    removed.setRunAt(Instant.now().minusSeconds(60));
    jobs.save(removed);
    approveCarlos();
    when(push.sendToUser(anyLong(), any(), any(), any(), isNull())).thenReturn(List.of());

    newWorker().runCycle();

    FailedJobModel failed = failedJobs.findByToken("removed-type").orElseThrow();
    assertThat(failed.isPermanent()).isTrue();
    assertThat(failed.getType()).isEqualTo("REMOVED_TYPE");
    assertThat(failed.getException()).contains("No handler registered for job type REMOVED_TYPE");
    assertThat(jobs.count()).isZero();
    verify(push).sendToUser(anyLong(), any(), any(), any(), isNull());
  }

  @Test
  void noInMemoryExecutorOrAsyncListenerRemains() {
    assertThat(context.containsBean("pushNotificationExecutor")).isFalse();
    Method[] listeners = NotificationEventListener.class.getDeclaredMethods();
    assertThat(Arrays.stream(listeners).filter(method -> method.isAnnotationPresent(Async.class)))
        .isEmpty();
  }

  private void approveCarlos() {
    transaction.executeWithoutResult(
        status -> events.publishEvent(new DriverReviewEvent.Approved(carlos.getToken())));
  }

  private JobWorker newWorker() {
    return new JobWorker(jobService, handlers, transaction, 20, Duration.ofMinutes(5));
  }
}
