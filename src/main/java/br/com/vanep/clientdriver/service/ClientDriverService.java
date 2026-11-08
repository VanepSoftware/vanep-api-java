package br.com.vanep.clientdriver.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.dto.ClientDriverCreateRequestDTO;
import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.enums.LinkOrigin;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.mapper.ClientDriverMapper;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverApprovalStatus;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientDriverService {

  private final ClientDriverRepository links;
  private final ClientRepository clients;
  private final DriverRepository drivers;
  private final UserService users;
  private final ClientDriverMapper mapper;
  private final MessageSource messages;

  public ClientDriverService(
      ClientDriverRepository links,
      ClientRepository clients,
      DriverRepository drivers,
      UserService users,
      ClientDriverMapper mapper,
      MessageSource messages) {
    this.links = links;
    this.clients = clients;
    this.drivers = drivers;
    this.users = users;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional
  public ClientDriverResponseDTO create(ClientDriverCreateRequestDTO request) {
    ClientModel client =
        clients
            .findByToken(request.clientToken())
            .orElseThrow(() -> notFound("client_driver.client.not_found"));
    DriverModel driver =
        drivers
            .findByToken(request.driverToken())
            .orElseThrow(() -> notFound("client_driver.driver.not_found"));

    if (driver.getApprovalStatus() != DriverApprovalStatus.APPROVED) {
      throw unprocessableEntity("client_driver.driver.not_approved");
    }

    links
        .findByPair(client.getId(), driver.getId())
        .ifPresent(
            _ -> {
              throw conflict("client_driver.duplicate_pair");
            });

    return mapper.toResponse(insertLink(newLink(client, driver, request.origin())));
  }

  // The lock makes a block of the same pair wait for the request, or the request see the block. A
  // concurrent first request of the same pair fails on the pair's unique index at flush; the caller
  // turns that violation into its own conflict.
  @Transactional
  public ClientDriverModel findOrCreateForRequest(ClientModel client, DriverModel driver) {
    return links
        .lockByPair(client.getId(), driver.getId())
        .orElseGet(() -> links.saveAndFlush(newLink(client, driver, LinkOrigin.CLIENT_SEARCH)));
  }

  ClientDriverModel newLink(ClientModel client, DriverModel driver, LinkOrigin origin) {
    ClientDriverModel link = new ClientDriverModel();
    link.setClient(client);
    link.setDriver(driver);
    link.setStatus(RelationshipStatus.PENDING);
    link.setOrigin(origin);
    return link;
  }

  ClientDriverModel insertLink(ClientDriverModel link) {
    try {
      return links.saveAndFlush(link);
    } catch (DataIntegrityViolationException concurrentCreation) {
      throw conflict("client_driver.duplicate_pair");
    }
  }

  @Transactional(readOnly = true)
  public Page<ClientDriverResponseDTO> findAll(Pageable pageable) {
    return links.findPage(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public ClientDriverResponseDTO findByToken(String token) {
    return mapper.toResponse(requireLink(token));
  }

  @Transactional(readOnly = true)
  public List<ClientDriverResponseDTO> findMine(String callerUid) {
    UserModel caller = users.requireByToken(callerUid);
    List<ClientDriverModel> mine =
        caller.getType() == UserType.DRIVER
            ? links.findByDriverUserId(caller.getId())
            : links.findByClientUserId(caller.getId());
    return mine.stream().map(mapper::toResponse).toList();
  }

  @Transactional
  public void delete(String token) {
    links.delete(requireLink(token));
  }

  @Transactional
  public ClientDriverResponseDTO restore(String token) {
    if (!links.existsDeletedByToken(token)) {
      throw notFound("client_driver.not_found");
    }
    links.restoreByToken(token);
    return mapper.toResponse(requireLink(token));
  }

  ClientDriverModel requireLink(String token) {
    return links.findByToken(token).orElseThrow(() -> notFound("client_driver.not_found"));
  }

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  ResponseStatusException unprocessableEntity(String key) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
