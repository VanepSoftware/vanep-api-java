package br.com.vanep.driverservicearea.repository;

import br.com.vanep.driverservicearea.model.DriverServiceAreaModel;
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
}
