package br.com.vanep.country.service;

import br.com.vanep.country.dto.CountryRequestDTO;
import br.com.vanep.country.dto.CountryResponseDTO;
import br.com.vanep.country.mapper.CountryMapper;
import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CountryService {

  private final CountryRepository countryRepository;
  private final CountryMapper mapper;
  private final MessageSource messages;

  public CountryService(
      CountryRepository countryRepository, CountryMapper mapper, MessageSource messages) {
    this.countryRepository = countryRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public CountryResponseDTO create(CountryRequestDTO request) {
    if (countryRepository.existsByName(request.name())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("country.name.duplicate"));
    }

    String isoCodeUpper = request.isoCode().toUpperCase();
    if (countryRepository.existsByIsoCode(isoCodeUpper)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("country.iso_code.duplicate"));
    }

    CountryModel country = new CountryModel();
    country.setName(request.name());
    country.setIsoCode(isoCodeUpper);
    country.setPhoneCode(request.phoneCode());
    country.setCurrency(request.currency().toUpperCase());
    country.setLocale(request.locale());

    return mapper.toResponse(countryRepository.save(country));
  }

  public List<CountryResponseDTO> findAll() {
    List<CountryModel> countries = countryRepository.findAll();
    return countries.stream().map(mapper::toResponse).toList();
  }

  public CountryResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public CountryResponseDTO update(String token, CountryRequestDTO request) {
    CountryModel country = requireByToken(token);

    if (!country.getName().equalsIgnoreCase(request.name())
        && countryRepository.existsByName(request.name())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("country.name.duplicate"));
    }

    String isoCodeUpper = request.isoCode().toUpperCase();
    if (!country.getIsoCode().equalsIgnoreCase(isoCodeUpper)
        && countryRepository.existsByIsoCode(isoCodeUpper)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("country.iso_code.duplicate"));
    }

    country.setName(request.name());
    country.setIsoCode(isoCodeUpper);
    country.setPhoneCode(request.phoneCode());
    country.setCurrency(request.currency().toUpperCase());
    country.setLocale(request.locale());

    return mapper.toResponse(countryRepository.save(country));
  }

  @Transactional
  public void delete(String token) {
    countryRepository.delete(requireByToken(token));
  }

  @Transactional
  public CountryResponseDTO restore(String token) {
    if (countryRepository.existsDeletedByToken(token)) {
      countryRepository.restoreByToken(token);
      CountryModel restored = requireByToken(token);
      return mapper.toResponse(restored);
    }

    if (countryRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("country.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("country.not_found"));
  }

  private CountryModel requireByToken(String token) {
    return countryRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("country.not_found")));
  }
}
