package br.com.vanep.job.model;

import br.com.vanep.job.enums.JobStatus;
import br.com.vanep.job.enums.JobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs")
@Getter
@Setter
public class JobModel {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 32)
  private String token;

  // Text, not an enum mapping: a row whose type was removed from the code must still load, or it
  // would break every claim. The worker fails such a job permanently instead.
  @Column(nullable = false, length = 64)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private JobStatus status = JobStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "run_at", nullable = false)
  private Instant runAt;

  @Column(name = "locked_at")
  private Instant lockedAt;

  @Column(name = "locked_by", length = 128)
  private String lockedBy;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public void setType(JobType jobType) {
    this.type = jobType.name();
  }

  public void setType(String type) {
    this.type = type;
  }

  public Optional<JobType> findJobType() {
    return Arrays.stream(JobType.values())
        .filter(jobType -> jobType.name().equals(type))
        .findFirst();
  }

  @PrePersist
  void onCreate() {
    if (token == null) {
      token = UUID.randomUUID().toString().replace("-", "").substring(0, 25);
    }
  }
}
