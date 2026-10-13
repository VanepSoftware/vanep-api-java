package br.com.vanep.conversation.realtime;

import br.com.vanep.auth.security.SecurityEvaluator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ConversationSubscriptionInterceptor implements ChannelInterceptor {

  private static final Pattern CONVERSATION_TOPIC =
      Pattern.compile("^/topic/conversations/([A-Za-z0-9]+)$");

  private final SecurityEvaluator sec;

  public ConversationSubscriptionInterceptor(SecurityEvaluator sec) {
    this.sec = sec;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    // Sending goes through REST; a SEND frame would reach the broker and spoof a message.
    if (StompCommand.SEND.equals(accessor.getCommand())) {
      throw new AccessDeniedException("Sending over the socket is not allowed");
    }
    if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      authorizeSubscription(accessor);
    }
    return message;
  }

  void authorizeSubscription(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    Matcher topic = CONVERSATION_TOPIC.matcher(destination == null ? "" : destination);
    boolean allowed =
        topic.matches()
            && accessor.getUser() instanceof Authentication caller
            && sec.isConversationParty(topic.group(1), caller);
    if (!allowed) {
      throw new AccessDeniedException("Subscription not allowed");
    }
  }
}
