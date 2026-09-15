package br.com.vanep.clientrating.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientrating.dto.ClientRatingCreateRequestDTO;
import br.com.vanep.clientrating.dto.ClientRatingResponseDTO;
import br.com.vanep.clientrating.mapper.ClientRatingMapper;
import br.com.vanep.clientrating.model.ClientRatingModel;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientRatingService {

  private final ClientRatingRepository clientRatingRepository;
  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;
  private final UserRepository userRepository;
  private final ClientRatingMapper mapper;
  private final MessageSource messages;

  public ClientRatingService(
      ClientRatingRepository clientRatingRepository,
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      UserRepository userRepository,
      ClientRatingMapper mapper,
      MessageSource messages) {
    this.clientRatingRepository = clientRatingRepository;
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  // TODO: exigir que o motorista já tenha tido algum vínculo (viagem concluída) com o cliente
  // antes de permitir a avaliação, uma vez que exista o relacionamento client_driver.
  @Transactional
  public ClientRatingResponseDTO create(ClientRatingCreateRequestDTO request, String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    DriverModel driver =
        driverRepository
            .findByUserId(caller.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, message("client_rating.driver_profile.not_found")));

    ClientModel client =
        clientRepository
            .findByToken(request.clientToken())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("client_rating.client.not_found")));

    if (client.getUser().getId().equals(caller.getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("client_rating.cannot_rate_self"));
    }

    if (clientRatingRepository.existsByDriverIdAndClientId(driver.getId(), client.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("client_rating.duplicate"));
    }

    ClientRatingModel ratingModel = new ClientRatingModel();
    ratingModel.setDriver(driver);
    ratingModel.setClient(client);
    ratingModel.setRating(request.rating());
    ratingModel.setComment(request.comment());

    ClientRatingModel saved = clientRatingRepository.save(ratingModel);
    recalculateClientAverage(client);

    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<ClientRatingResponseDTO> findAll(String clientToken, Pageable pageable) {
    if (clientToken != null && !clientToken.isBlank()) {
      return clientRatingRepository
          .findByClientToken(clientToken, pageable)
          .map(mapper::toResponse);
    }
    return clientRatingRepository.findAll(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public ClientRatingResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public void delete(String token) {
    ClientRatingModel ratingModel = requireByToken(token);
    ClientModel client = ratingModel.getClient();
    clientRatingRepository.delete(ratingModel);
    recalculateClientAverage(client);
  }

  private ClientRatingModel requireByToken(String token) {
    return clientRatingRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("client_rating.not_found")));
  }

  private void recalculateClientAverage(ClientModel client) {
    BigDecimal avg =
        clientRatingRepository
            .calculateAverageRatingForClient(client.getId())
            .map(val -> BigDecimal.valueOf(val.doubleValue()).setScale(2, RoundingMode.HALF_UP))
            .orElse(BigDecimal.valueOf(5.00));
    client.setRating(avg);
    clientRepository.save(client);
  }
}
