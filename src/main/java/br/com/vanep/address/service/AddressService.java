package br.com.vanep.address.service;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddressService {

  private final AddressRepository addressRepository;
  private final CityRepository cityRepository;
  private final AddressMapper mapper;
  private final MessageSource messages;

  public AddressService(
      AddressRepository addressRepository,
      CityRepository cityRepository,
      AddressMapper mapper,
      MessageSource messages) {
    this.addressRepository = addressRepository;
    this.cityRepository = cityRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public Page<AddressResponseDTO> findAll(Pageable pageable) {
    return addressRepository.findAll(pageable).map(mapper::toResponse);
  }

  public AddressResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public AddressResponseDTO create(AddressRequestDTO request) {
    AddressModel address = new AddressModel();
    applyRequest(address, request);
    return mapper.toResponse(addressRepository.save(address));
  }

  @Transactional
  public AddressResponseDTO update(String token, AddressRequestDTO request) {
    AddressModel address = requireByToken(token);
    applyRequest(address, request);
    return mapper.toResponse(addressRepository.save(address));
  }

  @Transactional
  public void delete(String token) {
    addressRepository.delete(requireByToken(token));
  }

  @Transactional
  public AddressResponseDTO restore(String token) {
    if (addressRepository.existsDeletedByToken(token)) {
      addressRepository.restoreByToken(token);
      return mapper.toResponse(requireByToken(token));
    }

    if (addressRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("address.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("address.not_found"));
  }

  private void applyRequest(AddressModel address, AddressRequestDTO request) {
    address.setCity(requireCityByToken(request.cityToken()));
    address.setZipCode(request.zipCode());
    address.setStreet(request.street());
    address.setNumber(request.number());
    address.setComplement(request.complement());
    address.setDistrict(request.district());
  }

  private CityModel requireCityByToken(String cityToken) {
    return cityRepository
        .findByToken(cityToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }

  private AddressModel requireByToken(String token) {
    return addressRepository
        .findByToken(token)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("address.not_found")));
  }
}
