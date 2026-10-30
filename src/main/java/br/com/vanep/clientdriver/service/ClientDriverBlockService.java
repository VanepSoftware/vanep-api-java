package br.com.vanep.clientdriver.service;

import br.com.vanep.clientdriver.dto.ClientDriverResponseDTO;
import br.com.vanep.clientdriver.enums.RelationshipStatus;
import br.com.vanep.clientdriver.mapper.ClientDriverMapper;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.contract.repository.ContractRepository;
import br.com.vanep.proposal.enums.ProposalStatus;
import br.com.vanep.servicerequest.enums.ServiceRequestStatus;
import br.com.vanep.servicerequest.service.ServiceRequestService;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Instant;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientDriverBlockService {

  private final ClientDriverRepository links;
  private final ContractRepository contracts;
  private final ServiceRequestService requests;
  private final UserService users;
  private final LinkStatusPolicy statusPolicy;
  private final ClientDriverMapper mapper;
  private final MessageSource messages;

  public ClientDriverBlockService(
      ClientDriverRepository links,
      ContractRepository contracts,
      ServiceRequestService requests,
      UserService users,
      LinkStatusPolicy statusPolicy,
      ClientDriverMapper mapper,
      MessageSource messages) {
    this.links = links;
    this.contracts = contracts;
    this.requests = requests;
    this.users = users;
    this.statusPolicy = statusPolicy;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional
  public ClientDriverResponseDTO block(String callerUid, String token) {
    UserModel caller = users.requireByToken(callerUid);
    ClientDriverModel link = requireLink(token);
    if (link.getStatus() == RelationshipStatus.BLOCKED) {
      throw conflict("client_driver.already_blocked");
    }
    if (statusPolicy.hasSignedContractNotEnded(
        contracts.findStatusesByClientDriverId(link.getId()))) {
      throw unprocessableContent("client_driver.block.has_contract");
    }

    if (links.block(link.getId(), caller.getId(), Instant.now()) == 0) {
      throw conflict("client_driver.already_blocked");
    }
    if (isClientOf(link, caller)) {
      requests.closeOpenRequestOf(link, ServiceRequestStatus.WITHDRAWN, ProposalStatus.DECLINED);
    } else {
      requests.closeOpenRequestOf(link, ServiceRequestStatus.DECLINED, ProposalStatus.WITHDRAWN);
    }
    return mapper.toResponse(requireLink(token));
  }

  @Transactional
  public ClientDriverResponseDTO unblock(String callerUid, String token) {
    UserModel caller = users.requireByToken(callerUid);
    ClientDriverModel link = requireLink(token);
    if (link.getStatus() != RelationshipStatus.BLOCKED) {
      throw conflict("client_driver.not_blocked");
    }
    if (!link.getBlockedBy().getId().equals(caller.getId())) {
      throw forbidden("client_driver.unblock.not_blocker");
    }
    RelationshipStatus unblocked =
        statusPolicy.deriveStatus(
            RelationshipStatus.PENDING, contracts.findStatusesByClientDriverId(link.getId()));

    if (links.unblock(link.getId(), caller.getId(), unblocked.name()) == 0) {
      throw conflict("client_driver.not_blocked");
    }
    return mapper.toResponse(requireLink(token));
  }

  boolean isClientOf(ClientDriverModel link, UserModel caller) {
    return link.getClient().getUser().getId().equals(caller.getId());
  }

  ClientDriverModel requireLink(String token) {
    return links.findByToken(token).orElseThrow(() -> notFound("client_driver.not_found"));
  }

  ResponseStatusException conflict(String key) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message(key));
  }

  ResponseStatusException forbidden(String key) {
    return new ResponseStatusException(HttpStatus.FORBIDDEN, message(key));
  }

  ResponseStatusException notFound(String key) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, message(key));
  }

  ResponseStatusException unprocessableContent(String key) {
    return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
