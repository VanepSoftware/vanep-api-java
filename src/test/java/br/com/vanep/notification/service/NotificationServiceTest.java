package br.com.vanep.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.notification.dto.NotificationResponseDTO;
import br.com.vanep.notification.enums.NotificationType;
import br.com.vanep.notification.model.NotificationModel;
import br.com.vanep.notification.repository.NotificationRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

  @Mock private NotificationRepository notifications;
  @Mock private PushNotificationService push;
  @Mock private UserService users;
  @Captor private ArgumentCaptor<NotificationModel> saved;

  private NotificationService service;

  @BeforeEach
  void setUp() {
    service = new NotificationService(notifications, push, users, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void storesTheNotificationThenPushesItWithItsToken() {
    when(notifications.save(any()))
        .thenAnswer(
            call -> {
              NotificationModel notification = call.getArgument(0);
              notification.setToken("n-1");
              return notification;
            });

    service.notifyUser(
        7L,
        NotificationType.PROPOSAL_RECEIVED,
        "Nova proposta",
        "Toque para ver.",
        Map.of("proposalToken", "prop-1"));

    InOrder order = inOrder(notifications, push);
    order.verify(notifications).save(saved.capture());
    order
        .verify(push)
        .sendToUser(
            7L,
            "Nova proposta",
            "Toque para ver.",
            Map.of(
                "type",
                "PROPOSAL_RECEIVED",
                "notificationToken",
                "n-1",
                "proposalToken",
                "prop-1"));
    NotificationModel notification = saved.getValue();
    assertThat(notification.getUserId()).isEqualTo(7L);
    assertThat(notification.getType()).isEqualTo(NotificationType.PROPOSAL_RECEIVED);
    assertThat(notification.getTitle()).isEqualTo("Nova proposta");
    assertThat(notification.getBody()).isEqualTo("Toque para ver.");
    assertThat(notification.getData()).containsExactly(Map.entry("proposalToken", "prop-1"));
    assertThat(notification.getReadAt()).isNull();
  }

  @Test
  void listsTheCallersNotificationsNewestFirstIgnoringClientSorting() {
    when(users.requireByToken("maria-uid")).thenReturn(user(7L));
    NotificationModel stored = notification("n-1", NotificationType.CHAT_MESSAGE);
    when(notifications.findByUserIdNewestFirst(7L, PageRequest.of(1, 5)))
        .thenReturn(new PageImpl<>(List.of(stored), PageRequest.of(1, 5), 6));

    Page<NotificationResponseDTO> page =
        service.listMine("maria-uid", PageRequest.of(1, 5, Sort.by("title")));

    assertThat(page.getTotalElements()).isEqualTo(6);
    assertThat(page.getContent())
        .containsExactly(
            new NotificationResponseDTO(
                "n-1",
                NotificationType.CHAT_MESSAGE,
                "title",
                "body",
                Map.of("conversationToken", "c-1"),
                NOW,
                null));
  }

  @Test
  void countsTheCallersUnreadNotifications() {
    when(users.requireByToken("maria-uid")).thenReturn(user(7L));
    when(notifications.countByUserIdAndReadAtIsNull(7L)).thenReturn(3L);

    assertThat(service.countMyUnread("maria-uid")).isEqualTo(3L);
  }

  @Test
  void marksOneNotificationReadNow() {
    service.markRead("n-1");

    verify(notifications).markRead("n-1", NOW);
  }

  @Test
  void marksAllOfTheCallersNotificationsReadNow() {
    when(users.requireByToken("maria-uid")).thenReturn(user(7L));

    service.markAllMineRead("maria-uid");

    verify(notifications).markAllRead(eq(7L), eq(NOW));
  }

  private NotificationModel notification(String token, NotificationType type) {
    NotificationModel notification = new NotificationModel();
    notification.setToken(token);
    notification.setUserId(7L);
    notification.setType(type);
    notification.setTitle("title");
    notification.setBody("body");
    notification.setData(Map.of("conversationToken", "c-1"));
    notification.setCreatedAt(NOW);
    return notification;
  }

  private UserModel user(Long id) {
    UserModel user = new UserModel();
    user.setId(id);
    return user;
  }
}
