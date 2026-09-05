package br.com.vanep.location.service;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.district.repository.DistrictRepository;
import br.com.vanep.location.AddressComponentClassifier;
import br.com.vanep.location.LocationNameNormalizer;
import br.com.vanep.location.dto.LocationComponentDTO;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.enums.LocationLevel;
import br.com.vanep.location.exception.PlaceNotResolvableException;
import br.com.vanep.location.exception.UnsupportedCountryException;
import br.com.vanep.location.exception.UnsupportedStateException;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationResolverService {
  private final CountryRepository countries;
  private final StateRepository states;
  private final CityRepository cities;
  private final DistrictRepository districts;

  public LocationResolverService(
      CountryRepository countries,
      StateRepository states,
      CityRepository cities,
      DistrictRepository districts) {
    this.countries = countries;
    this.states = states;
    this.cities = cities;
    this.districts = districts;
  }

  @Transactional
  public ResolvedLocationChainDTO resolveAndPersist(PlaceDetailsResponseDTO details) {
    List<LocationComponentDTO> components = classify(details);

    CountryModel country = requireSupportedCountry(components);
    StateModel state =
        requireSupportedState(country, requireComponent(components, LocationLevel.STATE));
    CityModel city = findOrCreateCity(state, requireComponent(components, LocationLevel.CITY));

    List<DistrictModel> chain = new ArrayList<>();
    DistrictModel parent = null;
    for (LocationComponentDTO component :
        AddressComponentClassifier.districtsFromShallowToDeep(components)) {
      parent = findOrCreateDistrict(city, parent, component);
      chain.add(parent);
    }

    return new ResolvedLocationChainDTO(
        country, state, city, chain, AddressComponentClassifier.hasDistrictComponent(components));
  }

  @Transactional(readOnly = true)
  public Optional<ResolvedLocationChainDTO> resolveAnchor(PlaceDetailsResponseDTO details) {
    List<LocationComponentDTO> components = classify(details);

    Optional<CountryModel> country = findCountry(components);
    if (country.isEmpty()) {
      return Optional.empty();
    }
    Optional<StateModel> state =
        states.findByCountryIdAndUfIgnoreCase(
            country.get().getId(), requireComponent(components, LocationLevel.STATE).shortName());
    if (state.isEmpty()) {
      return Optional.empty();
    }
    Optional<CityModel> city =
        cities.findByStateIdAndNormalizedName(
            state.get().getId(),
            LocationNameNormalizer.normalize(
                requireComponent(components, LocationLevel.CITY).name()));
    if (city.isEmpty()) {
      return Optional.empty();
    }

    List<DistrictModel> chain = new ArrayList<>();
    DistrictModel parent = null;
    for (LocationComponentDTO component :
        AddressComponentClassifier.districtsFromShallowToDeep(components)) {
      Optional<DistrictModel> existing = findDistrict(city.get(), parent, component);
      if (existing.isEmpty()) {
        break;
      }
      parent = existing.get();
      chain.add(parent);
    }

    return Optional.of(
        new ResolvedLocationChainDTO(
            country.get(),
            state.get(),
            city.get(),
            chain,
            AddressComponentClassifier.hasDistrictComponent(components)));
  }

  @Transactional(readOnly = true)
  public List<DistrictModel> findAncestors(DistrictModel district) {
    List<DistrictModel> ancestors = new ArrayList<>();
    DistrictModel current = district;
    while (current != null) {
      ancestors.add(current);
      current = current.getParent();
    }
    return ancestors;
  }

  private List<LocationComponentDTO> classify(PlaceDetailsResponseDTO details) {
    return AddressComponentClassifier.classify(details.addressComponents());
  }

  private Optional<CountryModel> findCountry(List<LocationComponentDTO> components) {
    LocationComponentDTO component = requireComponent(components, LocationLevel.COUNTRY);
    return countries.findByIsoCodeIgnoreCase(component.shortName());
  }

  private CountryModel requireSupportedCountry(List<LocationComponentDTO> components) {
    LocationComponentDTO component = requireComponent(components, LocationLevel.COUNTRY);
    return countries
        .findByIsoCodeIgnoreCase(component.shortName())
        .orElseThrow(() -> new UnsupportedCountryException(component.shortName()));
  }

  private StateModel requireSupportedState(CountryModel country, LocationComponentDTO component) {
    return states
        .findByCountryIdAndUfIgnoreCase(country.getId(), component.shortName())
        .orElseThrow(() -> new UnsupportedStateException(component.shortName()));
  }

  private LocationComponentDTO requireComponent(
      List<LocationComponentDTO> components, LocationLevel level) {
    return AddressComponentClassifier.findFirstOfLevel(components, level)
        .orElseThrow(() -> new PlaceNotResolvableException(level.name()));
  }

  private CityModel findOrCreateCity(StateModel state, LocationComponentDTO component) {
    String normalized = LocationNameNormalizer.normalize(component.name());
    return cities
        .findByStateIdAndNormalizedName(state.getId(), normalized)
        .orElseGet(
            () -> {
              CityModel city = new CityModel();
              city.setState(state);
              city.setName(component.name());
              return cities.save(city);
            });
  }

  private Optional<DistrictModel> findDistrict(
      CityModel city, DistrictModel parent, LocationComponentDTO component) {
    String normalized = LocationNameNormalizer.normalize(component.name());
    return parent == null
        ? districts.findByCityIdAndParentIsNullAndNormalizedName(city.getId(), normalized)
        : districts.findByCityIdAndParentIdAndNormalizedName(
            city.getId(), parent.getId(), normalized);
  }

  private DistrictModel findOrCreateDistrict(
      CityModel city, DistrictModel parent, LocationComponentDTO component) {
    return findDistrict(city, parent, component)
        .orElseGet(
            () -> {
              DistrictModel district = new DistrictModel();
              district.setCity(city);
              district.setParent(parent);
              district.setName(component.name());
              return districts.save(district);
            });
  }
}
