package br.com.vanep.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.event.DriverReviewEvent;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.notification.service.PushNotificationService;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PushNotificationAfterCommitTest {

  @MockitoBean private PushNotificationService push;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private TransactionTemplate transaction;
  @Autowired private UserRepository users;
  @Autowired private DriverRepository drivers;

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
  void pushesOnAPoolThreadOnceTheTransactionCommits() {
    AtomicReference<String> thread = new AtomicReference<>();
    doAnswer(
            call -> {
              thread.set(Thread.currentThread().getName());
              return null;
            })
        .when(push)
        .sendToUser(anyLong(), any(), any(), any());

    transaction.executeWithoutResult(
        status -> events.publishEvent(new DriverReviewEvent.Approved(carlos.getToken())));

    verify(push, timeout(5000))
        .sendToUser(
            eq(carlos.getUser().getId()),
            eq("Cadastro aprovado"),
            any(),
            eq(Map.of("type", "DRIVER_APPROVED")));
    assertThat(thread.get()).startsWith("push-");
  }

  @Test
  void aRolledBackTransactionPushesNothing() {
    transaction.executeWithoutResult(
        status -> {
          events.publishEvent(new DriverReviewEvent.Approved(carlos.getToken()));
          status.setRollbackOnly();
        });

    verify(push, after(500).never()).sendToUser(anyLong(), any(), any(), any());
  }
}
