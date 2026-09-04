package br.com.vanep.city.service;

import br.com.vanep.city.dto.CityResponseDTO;
import br.com.vanep.city.mapper.CityMapper;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Leitura de cidades. Só leitura: a cidade nasce do Google Places, pelo resolver.
 *
 * <p>Um CRUD manual em paralelo criaria linhas que nunca casariam com as resolvidas pelo Google — o
 * R2 por outro caminho.
 */
@Service
public class CityService {

  private final CityRepository cityRepository;
  private final CityMapper mapper;
  private final MessageSource messages;

  public CityService(CityRepository cityRepository, CityMapper mapper, MessageSource messages) {
    this.cityRepository = cityRepository;
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

  private CityModel requireByToken(String token) {
    return cityRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }
}
