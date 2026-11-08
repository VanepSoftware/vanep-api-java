package br.com.vanep.familymatch.repository;

import br.com.vanep.familymatch.dto.FamilyMatchSummaryRow;
import br.com.vanep.familymatch.model.FamilyMatchModel;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FamilyMatchRepository extends JpaRepository<FamilyMatchModel, Long> {

  @Query(
      """
      select familyMatch from FamilyMatchModel familyMatch
      where familyMatch.unlinkedPassengerId = :unlinkedPassengerId
        and familyMatch.status = br.com.vanep.familymatch.enums.FamilyMatchStatus.PENDING
      """)
  Optional<FamilyMatchModel> findPendingByUnlinkedPassengerId(Long unlinkedPassengerId);

  @Query(
      """
      select new br.com.vanep.familymatch.dto.FamilyMatchSummaryRow(
        familyMatch.unlinkedPassengerId, familyMatch.token, dependent.token, dependent.name,
        dependent.birthDate, clientUser.name)
      from FamilyMatchModel familyMatch
      join DependentModel dependent on dependent.id = familyMatch.dependentId
      join ClientModel client on client.id = dependent.clientId
      join client.user clientUser
      where familyMatch.unlinkedPassengerId in :unlinkedPassengerIds
        and familyMatch.status = br.com.vanep.familymatch.enums.FamilyMatchStatus.PENDING
      """)
  List<FamilyMatchSummaryRow> findPendingSummaries(Collection<Long> unlinkedPassengerIds);

  // Conditional on PENDING so that, of two concurrent answers, only the first changes the row.
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          """
          UPDATE family_match SET status = :status, responded_at = :now, updated_at = :now
          WHERE id = :id AND status = 'PENDING' AND deleted_at IS NULL
          """,
      nativeQuery = true)
  int respondIfPending(
      @Param("id") Long id, @Param("status") String status, @Param("now") Instant now);
}
