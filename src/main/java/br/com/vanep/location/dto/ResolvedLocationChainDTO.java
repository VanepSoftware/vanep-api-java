package br.com.vanep.location.dto;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.state.model.StateModel;
import java.util.List;
import java.util.Optional;

public record ResolvedLocationChainDTO(
    CountryModel country,
    StateModel state,
    CityModel city,
    List<DistrictModel> districts,
    boolean hasDistrictComponent) {
  public ResolvedLocationChainDTO {
    districts = districts == null ? List.of() : List.copyOf(districts);
  }

  public Optional<DistrictModel> deepestDistrict() {
    return districts.isEmpty()
        ? Optional.empty()
        : Optional.of(districts.get(districts.size() - 1));
  }

  public Optional<DistrictModel> shallowestDistrict() {
    return districts.isEmpty() ? Optional.empty() : Optional.of(districts.get(0));
  }

  public boolean anchoredAboveTheDistrictComponents() {
    return hasDistrictComponent && districts.isEmpty();
  }
}
