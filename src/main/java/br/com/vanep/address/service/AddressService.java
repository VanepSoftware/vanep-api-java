package br.com.vanep.address.service;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.function.LongConsumer;
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
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final SchoolRepository schools;

  public AddressService(
      AddressRepository addressRepository,
      CityRepository cityRepository,
      AddressMapper mapper,
      MessageSource messages,
      ClientRepository clients,
      DependentRepository dependents,
      SchoolRepository schools) {
    this.addressRepository = addressRepository;
    this.cityRepository = cityRepository;
    this.mapper = mapper;
    this.messages = messages;
    this.clients = clients;
    this.dependents = dependents;
    this.schools = schools;
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

  public Long resolveAddressId(String addressToken) {
    return requireByToken(addressToken).getId();
  }

  public String resolveAddressToken(Long addressId) {
    if (addressId == null) {
      return null;
    }
    return addressRepository.findById(addressId).map(address -> address.getToken()).orElse(null);
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

  @Transactional
  public AddressResponseDTO upsertForClient(Long clientId, AddressRequestDTO request) {
    ClientModel client = requireClient(clientId);
    return upsertOwnedAddress(
        client.getAddressId(),
        request,
        () ->
            rejectIfOwnedByAnotherActiveOwner(
                clients.countByAddressIdAndIdNot(client.getAddressId(), client.getId()),
                dependents.countByAddressId(client.getAddressId()),
                schools.countByAddressId(client.getAddressId())),
        savedId -> {
          client.setAddressId(savedId);
          clients.save(client);
        });
  }

  @Transactional
  public AddressResponseDTO upsertForDependent(Long dependentId, AddressRequestDTO request) {
    DependentModel dependent = requireDependent(dependentId);
    return upsertOwnedAddress(
        dependent.getAddressId(),
        request,
        () ->
            rejectIfOwnedByAnotherActiveOwner(
                clients.countByAddressId(dependent.getAddressId()),
                dependents.countByAddressIdAndIdNot(dependent.getAddressId(), dependent.getId()),
                schools.countByAddressId(dependent.getAddressId())),
        savedId -> {
          dependent.setAddressId(savedId);
          dependents.save(dependent);
        });
  }

  @Transactional
  public AddressResponseDTO upsertForSchool(Long schoolId, AddressRequestDTO request) {
    SchoolModel school = requireSchool(schoolId);
    return upsertOwnedAddress(
        school.getAddressId(),
        request,
        () ->
            rejectIfOwnedByAnotherActiveOwner(
                clients.countByAddressId(school.getAddressId()),
                dependents.countByAddressId(school.getAddressId()),
                schools.countByAddressIdAndIdNot(school.getAddressId(), school.getId())),
        savedId -> {
          school.setAddressId(savedId);
          schools.save(school);
        });
  }

  @Transactional
  public void clearForClient(Long clientId) {
    ClientModel client = requireClient(clientId);
    softDeleteOwnedAddress(client.getAddressId());
    if (client.getAddressId() != null) {
      client.setAddressId(null);
      clients.save(client);
    }
  }

  @Transactional
  public void clearForDependent(Long dependentId) {
    DependentModel dependent = requireDependent(dependentId);
    softDeleteOwnedAddress(dependent.getAddressId());
    if (dependent.getAddressId() != null) {
      dependent.setAddressId(null);
      dependents.save(dependent);
    }
  }

  @Transactional
  public void clearForSchool(Long schoolId) {
    SchoolModel school = requireSchool(schoolId);
    softDeleteOwnedAddress(school.getAddressId());
    if (school.getAddressId() != null) {
      school.setAddressId(null);
      schools.save(school);
    }
  }

  private AddressResponseDTO upsertOwnedAddress(
      Long currentAddressId,
      AddressRequestDTO request,
      Runnable rejectIfOwnedByAnother,
      LongConsumer linkToOwner) {
    if (currentAddressId != null) {
      rejectIfOwnedByAnother.run();
      AddressModel address =
          addressRepository
              .findById(currentAddressId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(
                          HttpStatus.NOT_FOUND, message("address.not_found")));
      applyRequest(address, request);
      return mapper.toResponse(addressRepository.save(address));
    }

    AddressModel address = new AddressModel();
    applyRequest(address, request);
    AddressModel saved = addressRepository.save(address);
    linkToOwner.accept(saved.getId());
    return mapper.toResponse(saved);
  }

  private void softDeleteOwnedAddress(Long addressId) {
    if (addressId == null) {
      return;
    }
    addressRepository.findById(addressId).ifPresent(addressRepository::delete);
  }

  private void rejectIfOwnedByAnotherActiveOwner(
      long clientCount, long dependentCount, long schoolCount) {
    if (clientCount + dependentCount + schoolCount > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("address.already_owned"));
    }
  }

  private ClientModel requireClient(Long clientId) {
    return clients
        .findById(clientId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("client.profile.not_found")));
  }

  private DependentModel requireDependent(Long dependentId) {
    return dependents
        .findById(dependentId)
        .orElseThrow(
            () ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, message("dependent.not_found")));
  }

  private SchoolModel requireSchool(Long schoolId) {
    return schools
        .findById(schoolId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("school.not_found")));
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
