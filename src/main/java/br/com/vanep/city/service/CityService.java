package br.com.vanep.city.service;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.location.LocationNameNormalizer;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CityService {
  private final CityRepository cityRepository;
  private final StateRepository stateRepository;
  private final CityMapper mapper;

  public CityService(
      CityRepository cityRepository, StateRepository stateRepository, CityMapper mapper) {
    this.cityRepository = cityRepository;
    this.stateRepository = stateRepository;
    this.mapper = mapper;
  }

  public Page<CityResponseDTO> findByUf(String uf, String search, Pageable pageable) {
    if (uf == null || uf.isBlank()) {
      throw ApiProblemException.badRequest("city.uf.required");
    }
    StateModel state =
        stateRepository
            .findByUf(uf.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> ApiProblemException.notFound("state.uf.not_found"));
    if (search == null || search.isBlank()) {
      return cityRepository
          .findByStateIdAndActiveTrue(state.getId(), pageable)
          .map(mapper::toResponse);
    }
    String normalizedSearch = LocationNameNormalizer.normalize(search);
    return cityRepository
        .findByStateIdAndActiveTrueAndNormalizedNameContaining(
            state.getId(), normalizedSearch, pageable)
        .map(mapper::toResponse);
  }

  public CityResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  private CityModel requireByToken(String token) {
    return cityRepository
        .findByToken(token)
        .orElseThrow(() -> ApiProblemException.notFound("city.not_found"));
  }
}
