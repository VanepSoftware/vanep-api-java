package br.com.vanep.address.service;

import br.com.vanep.address.dto.AddressRequestDTO;
import br.com.vanep.address.dto.AddressResponseDTO;
import br.com.vanep.address.mapper.AddressMapper;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import java.util.Collection;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
  private final DependentRepository dependents;
  private final SchoolRepository schools;

  public AddressService(
      AddressRepository addressRepository,
      CityRepository cityRepository,
      AddressMapper mapper,
      MessageSource messages,
      DependentRepository dependents,
      SchoolRepository schools) {
    this.addressRepository = addressRepository;
    this.cityRepository = cityRepository;
    this.mapper = mapper;
    this.messages = messages;
    this.dependents = dependents;
    this.schools = schools;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public AddressResponseDTO toResponseOrNull(Long addressId) {
    if (addressId == null) {
      return null;
    }
    return addressRepository.findById(addressId).map(mapper::toResponse).orElse(null);
  }

  public Map<Long, AddressResponseDTO> toResponsesByIds(Collection<Long> addressIds) {
    if (addressIds == null || addressIds.isEmpty()) {
      return Map.of();
    }
    return addressRepository.findAllById(addressIds).stream()
        .collect(Collectors.toMap(address -> address.getId(), mapper::toResponse));
  }

  @Transactional
  public AddressResponseDTO upsertForDependent(Long dependentId, AddressRequestDTO request) {
    DependentModel dependent = requireDependent(dependentId);
    return upsertOwnedAddress(
        dependent.getAddressId(),
        request,
        () ->
            rejectIfOwnedByAnotherActiveOwner(
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
                dependents.countByAddressId(school.getAddressId()),
                schools.countByAddressIdAndIdNot(school.getAddressId(), school.getId())),
        savedId -> {
          school.setAddressId(savedId);
          schools.save(school);
        });
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

  private void rejectIfOwnedByAnotherActiveOwner(long dependentCount, long schoolCount) {
    if (dependentCount + schoolCount > 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("address.already_owned"));
    }
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
  }

  private CityModel requireCityByToken(String cityToken) {
    return cityRepository
        .findByToken(cityToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("city.not_found")));
  }
}
