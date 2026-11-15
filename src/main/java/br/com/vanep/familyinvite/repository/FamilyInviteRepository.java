package br.com.vanep.familyinvite.repository;

import br.com.vanep.familyinvite.model.FamilyInviteModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FamilyInviteRepository extends JpaRepository<FamilyInviteModel, Long> {

  Optional<FamilyInviteModel> findByCode(String code);

  Optional<FamilyInviteModel> findByUnlinkedPassengerId(Long unlinkedPassengerId);

  @Query(
      """
      select invite from FamilyInviteModel invite
      where invite.driverId = :driverId and invite.unlinkedPassengerId is null
      """)
  Optional<FamilyInviteModel> findGroupInviteByDriverId(Long driverId);

  List<FamilyInviteModel> findByUnlinkedPassengerIdIn(Collection<Long> unlinkedPassengerIds);

  boolean existsByCode(String code);
}
