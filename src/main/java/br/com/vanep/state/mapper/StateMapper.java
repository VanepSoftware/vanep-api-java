package br.com.vanep.state.mapper;

import br.com.vanep.state.dto.StateResponseDTO;
import br.com.vanep.state.model.StateModel;
import org.springframework.stereotype.Component;

@Component
public class StateMapper {

  public StateResponseDTO toResponse(StateModel state) {
    return new StateResponseDTO(state.getToken(), state.getName(), state.getUf(), state.isActive());
  }
}
