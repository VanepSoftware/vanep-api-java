package br.com.vanep.cep.service;

import br.com.vanep.auth.security.RateLimiter;
import br.com.vanep.cep.client.ViaCepClient;
import br.com.vanep.cep.dto.CepLookupResponseDTO;
import br.com.vanep.cep.dto.ViaCepResponseDTO;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.shared.error.ApiProblemException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CepLookupService {
  private static final Logger log = LoggerFactory.getLogger(CepLookupService.class);
  private static final Pattern EIGHT_DIGITS = Pattern.compile("^\\d{8}$");

  private final ViaCepClient viaCepClient;
  private final CityRepository cities;
  private final RateLimiter rateLimiter;

  public CepLookupService(
      ViaCepClient viaCepClient,
      CityRepository cities,
      @Qualifier("viaCepRateLimiter") RateLimiter rateLimiter) {
    this.viaCepClient = viaCepClient;
    this.cities = cities;
    this.rateLimiter = rateLimiter;
  }

  public CepLookupResponseDTO lookup(String callerUid, String cep) {
    if (cep == null || !EIGHT_DIGITS.matcher(cep).matches()) {
      throw ApiProblemException.badRequest("cep.invalid");
    }
    if (!rateLimiter.tryAcquire("cep-lookup:" + callerUid)) {
      throw ApiProblemException.tooManyRequests("cep.rate_limited");
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

  private ApiProblemException catalogMiss(String ibgeCode) {
    log.warn("ViaCEP ibge_code {} has no city catalog row", ibgeCode);
    return ApiProblemException.notFound("cep.ibge.not_found");
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
