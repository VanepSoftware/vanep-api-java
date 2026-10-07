package br.com.vanep.absence.repository;

import br.com.vanep.absence.enums.AbsenceLeg;
import br.com.vanep.absence.model.AbsenceModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbsenceRepository extends JpaRepository<AbsenceModel, Long> {
  @Query(
      """
      select absence from AbsenceModel absence
      join fetch absence.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch absence.dependent
      left join fetch absence.trip trip
      left join fetch trip.driver tripDriver
      left join fetch tripDriver.user
      where absence.token = :token
      """)
  Optional<AbsenceModel> findByToken(@Param("token") String token);

  @Query(
      """
      select absence from AbsenceModel absence
      join fetch absence.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch absence.dependent
      left join fetch absence.trip trip
      left join fetch trip.driver tripDriver
      left join fetch tripDriver.user
      where absence.dependent.id = :dependentId
        and absence.absenceDate = :absenceDate
        and absence.leg = :leg
      """)
  Optional<AbsenceModel> findByDependentAndDateAndLeg(
      @Param("dependentId") Long dependentId,
      @Param("absenceDate") LocalDate absenceDate,
      @Param("leg") AbsenceLeg leg);

  @Query(
      """
      select absence from AbsenceModel absence
      join fetch absence.clientDriver link
      join fetch link.client client
      join fetch client.user
      join fetch link.driver driver
      join fetch driver.user
      join fetch absence.dependent
      left join fetch absence.trip trip
      left join fetch trip.driver tripDriver
      left join fetch tripDriver.user
      where absence.dependent.id = :dependentId
        and absence.absenceDate = :absenceDate
      order by absence.id
      """)
  List<AbsenceModel> findByDependentAndDate(
      @Param("dependentId") Long dependentId, @Param("absenceDate") LocalDate absenceDate);
}
