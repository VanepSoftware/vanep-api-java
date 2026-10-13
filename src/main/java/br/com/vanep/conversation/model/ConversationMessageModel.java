package br.com.vanep.conversation.model;

import br.com.vanep.conversation.enums.ConversationParticipant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;

@Entity
@Table(name = "conversation_message")
@SoftDelete(columnName = "deleted_at", strategy = SoftDeleteType.TIMESTAMP)
@Getter
@Setter
public class ConversationMessageModel {
  public static final int BODY_MAX_LENGTH = 2000;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private ConversationModel conversation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ConversationParticipant sender;

  @Column(nullable = false, length = BODY_MAX_LENGTH)
  private String body;

  @Column(name = "sent_at", nullable = false, updatable = false)
  private Instant sentAt;

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
    if (sentAt == null) {
      sentAt = Instant.now();
    }
  }
}
