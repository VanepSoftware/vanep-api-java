package br.com.vanep.conversation.repository;

import br.com.vanep.conversation.model.ConversationMessageModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationMessageRepository
    extends JpaRepository<ConversationMessageModel, Long> {
  @Query(
      value =
          """
          select message from ConversationMessageModel message
          where message.conversation.id = :conversationId
          order by message.sentAt desc, message.id desc
          """,
      countQuery =
          """
          select count(message) from ConversationMessageModel message
          where message.conversation.id = :conversationId
          """)
  Page<ConversationMessageModel> findByConversationIdNewestFirst(
      Long conversationId, Pageable pageable);
}
