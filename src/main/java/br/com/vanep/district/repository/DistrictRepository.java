package br.com.vanep.district.repository;

import br.com.vanep.district.model.DistrictModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<DistrictModel, Long> {
  Optional<DistrictModel> findByToken(String token);

  Optional<DistrictModel> findByGooglePlaceId(String googlePlaceId);

  Optional<DistrictModel> findByCityIdAndParentIsNullAndNormalizedName(
      Long cityId, String normalizedName);

  Optional<DistrictModel> findByCityIdAndParentIdAndNormalizedName(
      Long cityId, Long parentId, String normalizedName);

  List<DistrictModel> findByCityId(Long cityId);
}
