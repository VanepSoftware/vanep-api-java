package br.com.vanep.job.repository;

import br.com.vanep.job.model.FailedJobModel;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface FailedJobRepository extends JpaRepository<FailedJobModel, Long> {

  Optional<FailedJobModel> findByToken(String token);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(
      @QueryHint(name = "jakarta.persistence.lock.timeout", value = JobRepository.SKIP_LOCKED))
  @Query(
      """
      select failed from FailedJobModel failed
      where failed.retryAt <= :now
        and not exists (select job.id from JobModel job where job.token = failed.token)
      order by failed.retryAt, failed.id
      """)
  List<FailedJobModel> findDueForRetry(Instant now, Limit limit);

  @Modifying
  @Query("delete from FailedJobModel failed where failed.token = :token")
  int deleteByToken(String token);
}
