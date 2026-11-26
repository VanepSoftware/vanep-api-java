package br.com.vanep.job.repository;

import br.com.vanep.job.model.JobModel;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface JobRepository extends JpaRepository<JobModel, Long> {

  // A lock timeout of -2 is Hibernate's SKIP LOCKED: rows another worker holds are left out.
  String SKIP_LOCKED = "-2";

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
  @Query(
      """
      select job from JobModel job
      where job.status = br.com.vanep.job.enums.JobStatus.PENDING and job.runAt <= :now
      order by job.runAt, job.id
      """)
  List<JobModel> findDueForClaim(Instant now, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
  @Query(
      """
      select job from JobModel job
      where job.status = br.com.vanep.job.enums.JobStatus.PROCESSING and job.lockedAt < :cutoff
      order by job.lockedAt, job.id
      """)
  List<JobModel> findStuck(Instant cutoff, Limit limit);
}
