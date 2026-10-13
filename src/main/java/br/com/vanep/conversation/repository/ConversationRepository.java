package br.com.vanep.conversation.repository;

import br.com.vanep.conversation.model.ConversationModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationRepository extends JpaRepository<ConversationModel, Long> {
  @Query(
      """
      select conversation from ConversationModel conversation
      join fetch conversation.client client
      join fetch client.user
      join fetch conversation.driver driver
      join fetch driver.user
      where conversation.token = :token
      """)
  Optional<ConversationModel> findByToken(String token);

  @Query(
      """
      select conversation from ConversationModel conversation
      join fetch conversation.client client
      join fetch client.user
      join fetch conversation.driver driver
      join fetch driver.user
      where client.id = :clientId and driver.id = :driverId
      """)
  Optional<ConversationModel> findByPair(Long clientId, Long driverId);

  @Query(
      """
      select conversation from ConversationModel conversation
      join fetch conversation.client client
      join fetch client.user
      join fetch conversation.driver driver
      join fetch driver.user
      where driver.id = :driverId
      order by coalesce(conversation.lastMessageAt, conversation.createdAt) desc,
               conversation.id desc
      """)
  List<ConversationModel> findByDriverIdMostRecentFirst(Long driverId);

  @Query(
      """
      select conversation from ConversationModel conversation
      join fetch conversation.client client
      join fetch client.user
      join fetch conversation.driver driver
      join fetch driver.user
      where client.id = :clientId
      order by coalesce(conversation.lastMessageAt, conversation.createdAt) desc,
               conversation.id desc
      """)
  List<ConversationModel> findByClientIdMostRecentFirst(Long clientId);

  @Query(
      """
      select conversation.client.user.token from ConversationModel conversation
      where conversation.token = :token
      """)
  Optional<String> findClientUserTokenByToken(String token);

  @Query(
      """
      select conversation.driver.user.token from ConversationModel conversation
      where conversation.token = :token
      """)
  Optional<String> findDriverUserTokenByToken(String token);
}
