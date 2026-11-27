package br.com.vanep.address.service;

import br.com.vanep.address.dto.PersonalAddressRequestDTO;
import br.com.vanep.address.dto.PersonalAddressResponseDTO;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.shared.error.ApiProblemException;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalAddressService {
  private final AddressCatalogResolverService catalogResolver;
  private final AddressRepository addresses;
  private final UserRepository users;

  public PersonalAddressService(
      AddressCatalogResolverService catalogResolver,
      AddressRepository addresses,
      UserRepository users) {
    this.catalogResolver = catalogResolver;
    this.addresses = addresses;
    this.users = users;
  }

  @Transactional
  public PersonalAddressResponseDTO replaceMyAddress(
      String callerUid, PersonalAddressRequestDTO request) {
    UserModel caller = requireCaller(callerUid);
    AddressModel address =
        Optional.ofNullable(caller.getAddressId())
            .flatMap(addresses::findById)
            .orElseGet(AddressModel::new);

    catalogResolver.applyCity(
        address,
        request.cityToken(),
        request.street(),
        request.zipCode(),
        request.number(),
        request.complement(),
        request.neighborhood());

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
        .orElseThrow(() -> ApiProblemException.notFound("location.address.not_set"));
  }

  private UserModel requireCaller(String callerUid) {
    return users
        .findByToken(callerUid)
        .orElseThrow(() -> ApiProblemException.unauthorized("auth.unauthenticated"));
  }

  PersonalAddressResponseDTO toResponse(AddressModel address) {
    return new PersonalAddressResponseDTO(
        address.getToken(),
        address.getStreet(),
        address.getNumber(),
        address.getComplement(),
        address.getZipCode(),
        address.getNeighborhood(),
        address.getCity().getName(),
        address.getCity().getToken(),
        address.getCity().getState().getUf(),
        address.getCity().getState().getCountry().getIsoCode());
  }
}
