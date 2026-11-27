package br.com.vanep.state.service;

import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.state.dto.StateResponseDTO;
import br.com.vanep.state.mapper.StateMapper;
import br.com.vanep.state.repository.StateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class StateService {

  private final StateRepository stateRepository;
  private final StateMapper mapper;

  public StateService(StateRepository stateRepository, StateMapper mapper) {
    this.stateRepository = stateRepository;
    this.mapper = mapper;
  }

  public Page<StateResponseDTO> findAll(Pageable pageable) {
    return stateRepository.findAll(pageable).map(mapper::toResponse);
  }

  public StateResponseDTO findByToken(String token) {
    return stateRepository
        .findByToken(token)
        .map(mapper::toResponse)
        .orElseThrow(() -> ApiProblemException.notFound("state.not_found"));
  }
}
