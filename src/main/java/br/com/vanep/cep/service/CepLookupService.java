package br.com.vanep.cep.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.cep.client.ViaCepClient;
import br.com.vanep.cep.dto.CepLookupResponseDTO;
import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CepLookupService {
  private static final Logger log = LoggerFactory.getLogger(CepLookupService.class);
  private static final Pattern EIGHT_DIGITS = Pattern.compile("^\\d{8}$");

  private final ViaCepClient viaCepClient;
  private final CityRepository cities;
  private final RateLimiter rateLimiter;
  private final MessageSource messages;

  public CepLookupService(
      ViaCepClient viaCepClient,
      CityRepository cities,
      @Qualifier("viaCepRateLimiter") RateLimiter rateLimiter,
      MessageSource messages) {
    this.viaCepClient = viaCepClient;
    this.cities = cities;
    this.rateLimiter = rateLimiter;
    this.messages = messages;
  }

  public CepLookupResponseDTO lookup(String callerUid, String cep) {
    if (cep == null || !EIGHT_DIGITS.matcher(cep).matches()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message("cep.invalid"));
    }
    if (!rateLimiter.tryAcquire("cep-lookup:" + callerUid)) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message("cep.rate_limited"));
    }

    ViaCepResponseDTO viaCep = viaCepClient.findByCep(cep);
    CityModel city = requireCatalogCity(viaCep.ibge());

    return new CepLookupResponseDTO(
        city.getToken(),
        city.getName(),
        city.getState().getUf(),
        blankToNull(viaCep.street()),
        blankToNull(viaCep.neighborhood()));
  }

  private CityModel requireCatalogCity(String ibgeCode) {
    if (ibgeCode == null || ibgeCode.isBlank()) {
      throw catalogMiss(ibgeCode);
    }
    return cities.findByIbgeCode(ibgeCode).orElseThrow(() -> catalogMiss(ibgeCode));
  }

  private ResponseStatusException catalogMiss(String ibgeCode) {
    log.warn("ViaCEP ibge_code {} has no city catalog row", ibgeCode);
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message("cep.ibge.not_found"));
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
