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
  private final ClientMapper mapper;
  private final UserService userService;
  private final AddressService addressService;
  private final MessageSource messages;

  public ClientService(
      ClientRepository clients,
      ClientMapper mapper,
      UserService userService,
      AddressService addressService,
      MessageSource messages) {
    this.clients = clients;
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
    if (request.photo() != null) {
      client.setPhoto(request.photo());
    }
    return toListResponse(clients.save(client));
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
