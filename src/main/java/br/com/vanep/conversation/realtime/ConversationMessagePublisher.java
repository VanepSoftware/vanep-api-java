package br.com.vanep.conversation.realtime;

import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ConversationMessagePublisher {

  private final SimpMessagingTemplate messaging;

  public ConversationMessagePublisher(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publishSentMessage(ConversationMessageSentEvent event) {
    messaging.convertAndSend("/topic/conversations/" + event.conversationToken(), event.message());
  }
}
