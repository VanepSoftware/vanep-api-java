package br.com.vanep.job.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "failed_jobs")
@Getter
@Setter
public class FailedJobModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  // Text for the same reason as JobModel.type: a removed type must still be recordable.
  @Column(nullable = false, length = 64)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private int tries;

  @Column(nullable = false)
  private boolean permanent;

  @Column(nullable = false, columnDefinition = "text")
  private String exception;

  @Column(name = "failed_at", nullable = false)
  private Instant failedAt;

  @Column(name = "last_tried_at")
  private Instant lastTriedAt;

  // When the replayer sends the row back to the queue; null while it is queued, and for good
  // once it failed permanently or ran out of replays.
  @Column(name = "retry_at")
  private Instant retryAt;
}
