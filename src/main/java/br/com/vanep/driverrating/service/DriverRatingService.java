package br.com.vanep.driverrating.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.dto.DriverRatingCreateRequestDTO;
import br.com.vanep.driverrating.dto.DriverRatingResponseDTO;
import br.com.vanep.driverrating.dto.DriverRatingUpdateRequestDTO;
import br.com.vanep.driverrating.mapper.DriverRatingMapper;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
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
public class DriverRatingService {

  private final DriverRatingRepository driverRatingRepository;
  private final DriverRepository driverRepository;
  private final ClientRepository clientRepository;
  private final UserRepository userRepository;
  private final DriverRatingMapper mapper;
  private final MessageSource messages;

  public DriverRatingService(
      DriverRatingRepository driverRatingRepository,
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      UserRepository userRepository,
      DriverRatingMapper mapper,
      MessageSource messages) {
    this.driverRatingRepository = driverRatingRepository;
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DriverRatingResponseDTO create(DriverRatingCreateRequestDTO request, String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    ClientModel client =
        clientRepository
            .findByUserId(caller.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, message("driver_rating.client_profile.not_found")));

    DriverModel driver =
        driverRepository
            .findByToken(request.driverToken())
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found")));

    if (driver.getUser().getId().equals(caller.getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver_rating.cannot_rate_self"));
    }

    if (driverRatingRepository.existsByDriverIdAndClientId(driver.getId(), client.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("driver_rating.duplicate"));
    }

    DriverRatingModel ratingModel = new DriverRatingModel();
    ratingModel.setDriver(driver);
    ratingModel.setClient(client);
    ratingModel.setRating(request.rating());
    ratingModel.setComment(request.comment());

    DriverRatingModel saved = driverRatingRepository.save(ratingModel);
    recalculateDriverAverage(driver);

    return mapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<DriverRatingResponseDTO> findAll(String driverToken, Pageable pageable) {
    if (driverToken != null && !driverToken.isBlank()) {
      return driverRatingRepository
          .findByDriverToken(driverToken, pageable)
          .map(mapper::toResponse);
    }
    return driverRatingRepository.findAll(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverRatingResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional
  public DriverRatingResponseDTO update(String token, DriverRatingUpdateRequestDTO request) {
    DriverRatingModel ratingModel = requireByToken(token);

    ratingModel.setRating(request.rating());
    ratingModel.setComment(request.comment());

    DriverRatingModel saved = driverRatingRepository.save(ratingModel);
    recalculateDriverAverage(saved.getDriver());

    return mapper.toResponse(saved);
  }

  @Transactional
  public void delete(String token) {
    DriverRatingModel ratingModel = requireByToken(token);
    DriverModel driver = ratingModel.getDriver();
    driverRatingRepository.delete(ratingModel);
    recalculateDriverAverage(driver);
  }

  @Transactional
  public DriverRatingResponseDTO restore(String token) {
    if (driverRatingRepository.existsDeletedByToken(token)) {
      driverRatingRepository.restoreByToken(token);
      DriverRatingModel restored = requireByToken(token);
      recalculateDriverAverage(restored.getDriver());
      return mapper.toResponse(restored);
    }

    if (driverRatingRepository.findByToken(token).isPresent()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, message("driver_rating.already_active"));
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver_rating.not_found"));
  }

  private DriverRatingModel requireByToken(String token) {
    return driverRatingRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("driver_rating.not_found")));
  }

  private void recalculateDriverAverage(DriverModel driver) {
    BigDecimal avg =
        driverRatingRepository
            .calculateAverageRatingForDriver(driver.getId())
            .map(val -> BigDecimal.valueOf(val.doubleValue()).setScale(2, RoundingMode.HALF_UP))
            .orElse(BigDecimal.valueOf(5.00));
    driver.setRating(avg);
    driverRepository.save(driver);
  }
}
