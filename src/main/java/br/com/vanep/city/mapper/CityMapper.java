package br.com.vanep.city.mapper;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.model.CityModel;
import org.springframework.stereotype.Component;

@Component
public class CityMapper {

  public CityResponseDTO toResponse(CityModel city) {
    return new CityResponseDTO(
        city.getToken(),
        city.getName(),
        city.getState().getToken(),
        city.getState().getUf(),
        city.isActive(),
        city.getCreatedAt());
  }
}
