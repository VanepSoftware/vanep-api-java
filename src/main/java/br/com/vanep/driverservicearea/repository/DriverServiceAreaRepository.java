package br.com.vanep.driverservicearea.repository;

import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverServiceAreaRepository extends JpaRepository<DriverServiceAreaModel, Long> {
  @Query(
      """
      select area from DriverServiceAreaModel area
      join fetch area.city city
      join fetch city.state state
      join fetch state.country
      left join fetch area.district
      where area.driver.id = :driverId
      """)
  List<DriverServiceAreaModel> findByDriverId(Long driverId);

  @Query(
      """
      select distinct area.driver.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
        and (district is null or district.id in :ancestorIds)
        and area.driver.active = true
        and area.driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  List<Long> findDriverIdsCoveringPoint(Long cityId, Collection<Long> ancestorIds);

  @Query(
      """
      select area.driver.id, district.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
        and (district is null or district.id in :ancestorIds)
        and area.driver.active = true
        and area.driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  List<Object[]> findDriverMatchesCoveringPoint(Long cityId, Collection<Long> ancestorIds);

  @Query(
      """
      select area.driver.id, district.id from DriverServiceAreaModel area
      left join area.district district
      where area.city.id = :cityId
        and area.driver.active = true
        and area.driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  List<Object[]> findDriverMatchesInCity(Long cityId);

  @Query(
      """
      select area from DriverServiceAreaModel area
      join fetch area.city city
      left join fetch area.district
      where area.driver.id in :driverIds
      """)
  List<DriverServiceAreaModel> findByDriverIds(Collection<Long> driverIds);

  @Query(
      """
      select distinct area.driver.id from DriverServiceAreaModel area
      where area.city.id = :cityId
        and area.driver.active = true
        and area.driver.approvalStatus = br.com.vanep.driver.DriverApprovalStatus.APPROVED
      """)
  List<Long> findDriverIdsInCity(Long cityId);
}
