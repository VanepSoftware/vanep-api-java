package br.com.vanep.driverservicearea.service;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;

public final class ServiceAreaGranularityPolicy {
  private ServiceAreaGranularityPolicy() {}

  public static boolean isAcceptable(ResolvedLocationChainDTO chain) {
    if (chain.hasDistrictComponent()) {
      return true;
    }
    return !requiresDistrict(chain.city());
  }

  public static boolean requiresDistrict(CityModel city) {
    if (city.getRequiresDistrict() != null) {
      return city.getRequiresDistrict();
    }
    return city.getState().isRequiresDistrict();
  }
}
