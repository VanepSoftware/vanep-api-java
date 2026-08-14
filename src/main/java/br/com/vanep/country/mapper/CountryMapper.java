package br.com.vanep.country.mapper;

import br.com.vanep.country.dto.CountryResponseDTO;
import br.com.vanep.country.model.CountryModel;
import org.springframework.stereotype.Component;

@Component
public class CountryMapper {

  public CountryResponseDTO toResponse(CountryModel country) {
    return new CountryResponseDTO(
        country.getToken(),
        country.getName(),
        country.getIsoCode(),
        country.getPhoneCode(),
        country.getCurrency(),
        country.getLocale(),
        country.isActive(),
        country.getCreatedAt());
  }
}
