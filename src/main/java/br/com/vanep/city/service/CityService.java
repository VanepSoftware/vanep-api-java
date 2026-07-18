package br.com.vanep.city.service;

import br.com.vanep.city.dto.CityRequestDTO;
import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  public Page<CityResponseDTO> findAll(Pageable pageable) {
    return cityRepository.findAll(pageable).map(mapper::toResponse);
  }

  public CityResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public CityResponseDTO create(CityRequestDTO request) {
    StateModel state = requireStateByToken(request.stateToken());
    requireNameAvailableInState(request.name(), state);

    CityModel city = new CityModel();
    city.setState(state);
    city.setName(request.name());
    return mapper.toResponse(cityRepository.save(city));
  }

  @Transactional
  public CityResponseDTO update(String token, CityRequestDTO request) {
    CityModel city = requireByToken(token);
    StateModel state = requireStateByToken(request.stateToken());

    boolean sameNameAndState =
        city.getName().equalsIgnoreCase(request.name())
            && city.getState().getId().equals(state.getId());
    if (!sameNameAndState) {
      requireNameAvailableInState(request.name(), state);
    }

    city.setState(state);
    city.setName(request.name());
    return mapper.toResponse(cityRepository.save(city));
  }

  @Transactional
  public void delete(String token) {
    cityRepository.delete(requireByToken(token));
  }

  @Transactional
  public CityResponseDTO restore(String token) {
    if (cityRepository.existsDeletedByToken(token)) {
      cityRepository.restoreByToken(token);
      return mapper.toResponse(requireByToken(token));
    }

    if (cityRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("city.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found"));
  }

  private void requireNameAvailableInState(String name, StateModel state) {
    if (cityRepository.existsByNameIgnoreCaseAndStateId(name, state.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("city.name.duplicate"));
    }
  }

  private StateModel requireStateByToken(String stateToken) {
    return stateRepository
        .findByToken(stateToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("state.not_found")));
  }

  private CityModel requireByToken(String token) {
    return cityRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }
}
