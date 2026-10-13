package br.com.vanep.conversation.realtime;

import static org.mockito.Mockito.verify;

import br.com.vanep.conversation.dto.ConversationMessageResponseDTO;
import br.com.vanep.conversation.enums.ConversationParticipant;
import br.com.vanep.conversation.event.ConversationMessageSentEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class ConversationMessagePublisherTest {

  @Mock private SimpMessagingTemplate messaging;

  @InjectMocks private ConversationMessagePublisher publisher;

  @Test
  void publishesTheMessageOnTheConversationTopic() {
    ConversationMessageResponseDTO message =
        new ConversationMessageResponseDTO(
            "message-1", "Oi", ConversationParticipant.CLIENT, Instant.now());

    publisher.publishSentMessage(new ConversationMessageSentEvent("abc123", message));

    verify(messaging).convertAndSend("/topic/conversations/abc123", message);
  }
}
