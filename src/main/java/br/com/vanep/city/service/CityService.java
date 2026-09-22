package br.com.vanep.city.service;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.location.LocationNameNormalizer;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CityService {
  private final CityRepository cityRepository;
  private final StateRepository stateRepository;
  private final CityMapper mapper;
  private final MessageSource messages;

  public CityService(
      CityRepository cityRepository,
      StateRepository stateRepository,
      CityMapper mapper,
      MessageSource messages) {
    this.cityRepository = cityRepository;
    this.stateRepository = stateRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public Page<CityResponseDTO> findByUf(String uf, String search, Pageable pageable) {
    if (uf == null || uf.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("city.uf.required"));
    }
    StateModel state =
        stateRepository
            .findByUf(uf.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("state.uf.not_found")));
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
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }
}
