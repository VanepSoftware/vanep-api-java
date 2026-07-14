package br.com.vanep.state.service;

import br.com.vanep.state.dto.StateResponseDTO;
import br.com.vanep.state.mapper.StateMapper;
import br.com.vanep.state.repository.StateRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StateService {

  private final StateRepository stateRepository;
  private final StateMapper mapper;
  private final MessageSource messages;

  public StateService(StateRepository stateRepository, StateMapper mapper, MessageSource messages) {
    this.stateRepository = stateRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  public Page<StateResponseDTO> findAll(Pageable pageable) {
    return stateRepository.findAll(pageable).map(mapper::toResponse);
  }

  public StateResponseDTO findByToken(String token) {
    return stateRepository
        .findByToken(token)
        .map(mapper::toResponse)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    messages.getMessage("state.not_found", null, LocaleContextHolder.getLocale())));
  }
}
