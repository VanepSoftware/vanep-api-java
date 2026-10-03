package br.com.vanep.driverrating.service;

import br.com.vanep.client.model.ClientModel;
import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.dto.DriverRatingCreateRequestDTO;
import br.com.vanep.driverrating.dto.DriverRatingResponseDTO;
import br.com.vanep.driverrating.dto.DriverRatingStatusResponseDTO;
import br.com.vanep.driverrating.mapper.DriverRatingMapper;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
  private final ClientDriverRepository linkRepository;
  private final UserRepository userRepository;
  private final DriverRatingMapper mapper;
  private final MessageSource messages;
  private final DriverRatingEligibilityPolicy eligibility;

  public DriverRatingService(
      DriverRatingRepository driverRatingRepository,
      DriverRepository driverRepository,
      ClientRepository clientRepository,
      ClientDriverRepository linkRepository,
      UserRepository userRepository,
      DriverRatingMapper mapper,
      MessageSource messages,
      DriverRatingEligibilityPolicy eligibility) {
    this.driverRatingRepository = driverRatingRepository;
    this.driverRepository = driverRepository;
    this.clientRepository = clientRepository;
    this.linkRepository = linkRepository;
    this.userRepository = userRepository;
    this.mapper = mapper;
    this.messages = messages;
    this.eligibility = eligibility;
  }

  private String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }

  @Transactional
  public DriverRatingResponseDTO create(DriverRatingCreateRequestDTO request, String callerEmail) {
    ClientModel client = requireCallerClient(callerEmail);
    DriverModel driver = requireDriver(request.driverToken());

    if (driver.getUser().getId().equals(client.getUser().getId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, message("driver_rating.cannot_rate_self"));
    }

    ClientDriverModel link =
        linkRepository
            .findByPair(client.getId(), driver.getId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("driver_rating.link.not_found")));

    if (!eligibility.isLinkActive(link.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, message("driver_rating.link.not_active"));
    }

    if (!eligibility.isLinkOldEnough(link.getCreatedAt(), Instant.now())) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, message("driver_rating.link.too_recent"));
    }

    if (driverRatingRepository.existsByLinkId(link.getId())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, message("driver_rating.duplicate"));
    }

    DriverRatingModel ratingModel = new DriverRatingModel();
    ratingModel.setLink(link);
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
    return driverRatingRepository.findPage(pageable).map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public DriverRatingResponseDTO findByToken(String token) {
    return mapper.toResponse(requireByToken(token));
  }

  @Transactional(readOnly = true)
  public DriverRatingStatusResponseDTO findRatingStatus(String driverToken, String callerEmail) {
    ClientModel client = requireCallerClient(callerEmail);
    DriverModel driver = requireDriver(driverToken);

    return linkRepository
        .findByPair(client.getId(), driver.getId())
        .map(
            link -> {
              boolean rated = driverRatingRepository.existsByLinkId(link.getId());
              boolean canRate =
                  !rated
                      && eligibility.canRate(link.getStatus(), link.getCreatedAt(), Instant.now());
              return new DriverRatingStatusResponseDTO(rated, canRate);
            })
        .orElse(new DriverRatingStatusResponseDTO(false, false));
  }

  @Transactional
  public void delete(String token) {
    DriverRatingModel ratingModel = requireByToken(token);
    DriverModel driver = ratingModel.getLink().getDriver();
    driverRatingRepository.delete(ratingModel);
    recalculateDriverAverage(driver);
  }

  private DriverRatingModel requireByToken(String token) {
    return driverRatingRepository
        .findByToken(token)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("driver_rating.not_found")));
  }

  ClientModel requireCallerClient(String callerEmail) {
    UserModel caller =
        userRepository
            .findByEmail(callerEmail)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, message("user.account.not_found")));

    return clientRepository
        .findByUserId(caller.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, message("driver_rating.client_profile.not_found")));
  }

  DriverModel requireDriver(String driverToken) {
    return driverRepository
        .findByToken(driverToken)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, message("driver.not_found")));
  }

  @Transactional
  public void recalculateDriverAverage(DriverModel driver) {
    BigDecimal avg =
        driverRatingRepository
            .calculateAverageRatingForDriver(driver.getId())
            .map(val -> BigDecimal.valueOf(val.doubleValue()).setScale(2, RoundingMode.HALF_UP))
            .orElse(null);
    driver.setRating(avg);
    driverRepository.save(driver);
  }
}
