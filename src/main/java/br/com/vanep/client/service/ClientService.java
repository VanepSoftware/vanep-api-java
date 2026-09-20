package br.com.vanep.client.service;

import br.com.vanep.address.service.AddressService;
import br.com.vanep.client.dto.ClientMeSummaryResponseDTO;
import br.com.vanep.client.dto.ClientResponseDTO;
import br.com.vanep.client.dto.ClientUpdateRequestDTO;
import br.com.vanep.client.mapper.ClientMapper;
import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import br.com.vanep.user.service.UserService;
import java.util.Objects;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {
  private final ClientRepository clients;
  private final UserRepository users;
  private final ClientMapper mapper;
  private final UserService userService;
  private final AddressService addressService;
  private final MessageSource messages;

  public ClientService(
      ClientRepository clients,
      UserRepository users,
      ClientMapper mapper,
      UserService userService,
      AddressService addressService,
      MessageSource messages) {
    this.clients = clients;
    this.users = users;
    this.mapper = mapper;
    this.userService = userService;
    this.addressService = addressService;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  public Page<ClientResponseDTO> findAll(Pageable pageable) {
    Page<ClientModel> page = clients.findAll(pageable);
    var addressesById =
        addressService.toResponsesByIds(
            page.getContent().stream()
                .map(client -> client.getUser().getAddressId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return page.map(
        client ->
            mapper.toResponse(
                client,
                client.getUser().getAddressId() == null
                    ? null
                    : addressesById.get(client.getUser().getAddressId())));
  }

  public ClientResponseDTO findByToken(String token) {
    return toListResponse(requireByToken(token));
  }

  @Transactional(readOnly = true)
  public ClientMeSummaryResponseDTO getMyProfile(String uid) {
    UserModel user = userService.requireByTokenAndType(uid, UserType.CLIENT);
    ClientModel client = requireByUserId(user.getId());
    return mapper.toMeSummary(
        client,
        userService.toMeResponse(user),
        addressService.toResponseOrNull(client.getUser().getAddressId()));
  }

  @Transactional
  public ClientResponseDTO update(String token, ClientUpdateRequestDTO request) {
    ClientModel client = requireByToken(token);
    UserModel user = client.getUser();

    if (request.name().isPresent()) {
      user.setName(requireText(request.name().get(), "client.name.required"));
    }
    if (request.email().isPresent()) {
      applyEmail(user, requireText(request.email().get(), "client.email.required"));
    }
    if (request.active().isPresent()) {
      Boolean active = request.active().get();
      if (active == null) {
        throw badRequest("client.active.required");
      }
      client.setActive(active);
    }
    if (request.photo().isPresent()) {
      client.setPhoto(request.photo().get());
    }
    if (request.rating().isPresent()) {
      client.setRating(request.rating().get());
    }

    return toListResponse(clients.save(client));
  }

  void applyEmail(UserModel user, String email) {
    if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmail(email)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("client.email.duplicate"));
    }
    user.setEmail(email);
  }

  String requireText(String value, String messageKey) {
    if (value == null || value.isBlank()) {
      throw badRequest(messageKey);
    }
    return value;
  }

  ResponseStatusException badRequest(String messageKey) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(messageKey));
  }

  @Transactional
  public void delete(String token) {
    ClientModel client = requireByToken(token);
    clients.delete(client);
  }

  private ClientResponseDTO toListResponse(ClientModel client) {
    return mapper.toResponse(
        client, addressService.toResponseOrNull(client.getUser().getAddressId()));
  }

  private ClientModel requireByUserId(Long userId) {
    return clients
        .findByUserId(userId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("client.profile.not_found")));
  }

  private ClientModel requireByToken(String token) {
    return clients
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("client.profile.not_found")));
  }
}
