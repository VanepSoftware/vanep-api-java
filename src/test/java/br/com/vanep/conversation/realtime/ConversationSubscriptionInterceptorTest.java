package br.com.vanep.conversation.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import br.com.vanep.auth.security.SecurityEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ConversationSubscriptionInterceptorTest {

  private static final Authentication CALLER = new TestingAuthenticationToken("uid", "n/a");

  @Mock private SecurityEvaluator sec;
  @Mock private MessageChannel channel;

  @InjectMocks private ConversationSubscriptionInterceptor interceptor;

  @Test
  void aPartySubscribesToTheConversationTopic() {
    when(sec.isConversationParty(eq("abc123"), any())).thenReturn(true);
    Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/conversations/abc123");

    assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
  }

  @Test
  void anOutsiderCannotSubscribe() {
    when(sec.isConversationParty(eq("abc123"), any())).thenReturn(false);

    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, "/topic/conversations/abc123"), channel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void anyOtherDestinationIsRejected() {
    assertThatThrownBy(
            () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/anything"), channel))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    frame(StompCommand.SUBSCRIBE, "/topic/conversations/abc/extra"), channel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void clientsCannotSendFrames() {
    assertThatThrownBy(
            () ->
                interceptor.preSend(
                    frame(StompCommand.SEND, "/topic/conversations/abc123"), channel))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void otherFramesPassThrough() {
    Message<byte[]> unsubscribe = frame(StompCommand.UNSUBSCRIBE, null);

    assertThat(interceptor.preSend(unsubscribe, channel)).isSameAs(unsubscribe);
  }

  private Message<byte[]> frame(StompCommand command, String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setDestination(destination);
    accessor.setUser(CALLER);
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
