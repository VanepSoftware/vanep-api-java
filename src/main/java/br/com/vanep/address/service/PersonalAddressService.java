package br.com.vanep.address.service;

import br.com.vanep.address.dto.PersonalAddressRequestDTO;
import br.com.vanep.address.dto.PersonalAddressResponseDTO;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PersonalAddressService {
  private final AddressPlaceResolverService placeResolver;
  private final AddressRepository addresses;
  private final UserRepository users;
  private final MessageSource messages;

  public PersonalAddressService(
      AddressPlaceResolverService placeResolver,
      AddressRepository addresses,
      UserRepository users,
      MessageSource messages) {
    this.placeResolver = placeResolver;
    this.addresses = addresses;
    this.users = users;
    this.messages = messages;
  }

  @Transactional
  public PersonalAddressResponseDTO replaceMyAddress(
      String callerUid, PersonalAddressRequestDTO request) {
    UserModel caller = requireCaller(callerUid);

    AddressModel address =
        Optional.ofNullable(caller.getAddressId())
            .flatMap(addresses::findById)
            .orElseGet(AddressModel::new);

    placeResolver.applyPlace(
        address, request.placeId(), request.sessionToken(), request.number(), request.complement());

    AddressModel saved = addresses.save(address);
    caller.setAddressId(saved.getId());
    users.save(caller);

    return toResponse(saved);
  }

  @Transactional
  public void clearMyAddress(String callerUid) {
    UserModel caller = requireCaller(callerUid);
    Long addressId = caller.getAddressId();
    if (addressId == null) {
      return;
    }
    addresses.findById(addressId).ifPresent(addresses::delete);
    caller.setAddressId(null);
    users.save(caller);
  }

  @Transactional(readOnly = true)
  public PersonalAddressResponseDTO findMyAddress(String callerUid) {
    UserModel caller = requireCaller(callerUid);
    return Optional.ofNullable(caller.getAddressId())
        .flatMap(addresses::findById)
        .map(this::toResponse)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("location.address.not_set")));
  }

  private UserModel requireCaller(String callerUid) {
    return users
        .findByToken(callerUid)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  }

  PersonalAddressResponseDTO toResponse(AddressModel address) {
    DistrictModel district = address.getDistrict();
    return new PersonalAddressResponseDTO(
        address.getToken(),
        address.getStreet(),
        address.getNumber(),
        address.getComplement(),
        address.getZipCode(),
        district == null ? null : district.getName(),
        district == null ? null : district.getToken(),
        address.getCity().getName(),
        address.getCity().getToken(),
        address.getCity().getState().getUf(),
        address.getCity().getState().getCountry().getIsoCode(),
        address.getGooglePlaceId());
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
