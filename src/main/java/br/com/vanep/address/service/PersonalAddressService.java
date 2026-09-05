package br.com.vanep.address.service;

import br.com.vanep.address.dto.PersonalAddressRequestDTO;
import br.com.vanep.address.dto.PersonalAddressResponseDTO;
import br.com.vanep.address.model.AddressModel;
import br.com.vanep.address.repository.AddressRepository;
import br.com.vanep.district.model.DistrictModel;
import br.com.vanep.location.StreetAddressExtractor;
import br.com.vanep.location.dto.ResolvedLocationChainDTO;
import br.com.vanep.location.service.LocationResolverService;
import br.com.vanep.places.client.PlacesClient;
import br.com.vanep.places.dto.PlaceDetailsResponseDTO;
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
  private final PlacesClient places;
  private final LocationResolverService resolver;
  private final AddressRepository addresses;
  private final UserRepository users;
  private final MessageSource messages;

  public PersonalAddressService(
      PlacesClient places,
      LocationResolverService resolver,
      AddressRepository addresses,
      UserRepository users,
      MessageSource messages) {
    this.places = places;
    this.resolver = resolver;
    this.addresses = addresses;
    this.users = users;
    this.messages = messages;
  }

  @Transactional
  public PersonalAddressResponseDTO replaceMyAddress(
      String callerUid, PersonalAddressRequestDTO request) {
    UserModel caller = requireCaller(callerUid);
    PlaceDetailsResponseDTO details =
        places.findPlaceDetails(request.placeId(), request.sessionToken());

    String street =
        StreetAddressExtractor.findStreet(details)
            .orElseThrow(() -> badRequest("location.address.street_required"));

    ResolvedLocationChainDTO chain = resolver.resolveAndPersist(details);

    AddressModel address =
        Optional.ofNullable(caller.getAddressId())
            .flatMap(addresses::findById)
            .orElseGet(AddressModel::new);

    address.setCity(chain.city());
    address.setDistrict(chain.deepestDistrict().orElse(null));
    address.setGooglePlaceId(details.id());
    address.setStreet(street);
    address.setZipCode(StreetAddressExtractor.findZipCode(details).orElse(null));

    address.setNumber(
        Optional.ofNullable(request.number())
            .filter(value -> !value.isBlank())
            .or(() -> StreetAddressExtractor.findNumber(details))
            .orElse(null));
    address.setComplement(request.complement());

    AddressModel saved = addresses.save(address);
    caller.setAddressId(saved.getId());
    users.save(caller);

    return toResponse(saved);
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

  private ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
