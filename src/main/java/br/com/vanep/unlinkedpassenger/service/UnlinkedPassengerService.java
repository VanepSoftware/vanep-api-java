package br.com.vanep.unlinkedpassenger.service;

import br.com.vanep.address.dto.DependentAddressRequestDTO;
import br.com.vanep.address.service.AddressService;
import br.com.vanep.driver.DriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schedule.service.ScheduleService;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import br.com.vanep.shared.enums.SchoolShift;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerCreateRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerResponseDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerScheduleRequestDTO;
import br.com.vanep.unlinkedpassenger.dto.UnlinkedPassengerUpdateRequestDTO;
import br.com.vanep.unlinkedpassenger.mapper.UnlinkedPassengerMapper;
import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import br.com.vanep.unlinkedpassenger.repository.UnlinkedPassengerRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UnlinkedPassengerService {

  private final UnlinkedPassengerRepository passengers;
  private final DriverRepository drivers;
  private final UserService users;
  private final SchoolRepository schools;
  private final AddressService addresses;
  private final ScheduleService schedules;
  private final UnlinkedPassengerMapper mapper;
  private final MessageSource messages;

  public UnlinkedPassengerService(
      UnlinkedPassengerRepository passengers,
      DriverRepository drivers,
      UserService users,
      SchoolRepository schools,
      AddressService addresses,
      ScheduleService schedules,
      UnlinkedPassengerMapper mapper,
      MessageSource messages) {
    this.passengers = passengers;
    this.drivers = drivers;
    this.users = users;
    this.schools = schools;
    this.addresses = addresses;
    this.schedules = schedules;
    this.mapper = mapper;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public List<UnlinkedPassengerResponseDTO> findMine(String callerUid) {
    return passengers.findByDriverId(requireDriver(callerUid).getId()).stream()
        .map(passenger -> mapper.toResponse(passenger))
        .toList();
  }

  @Transactional(readOnly = true)
  public UnlinkedPassengerResponseDTO findMineByToken(String callerUid, String token) {
    return mapper.toResponse(requireOwnPassenger(callerUid, token));
  }

  @Transactional
  public UnlinkedPassengerResponseDTO create(
      String callerUid, UnlinkedPassengerCreateRequestDTO request) {
    UnlinkedPassengerModel passenger = new UnlinkedPassengerModel();
    passenger.setDriver(requireDriver(callerUid));
    passenger.setName(request.name());
    passenger.setSchool(requireSchool(request.schoolToken()));
    passenger.setSchoolShift(request.schoolShift());
    passenger.setNotes(request.notes());
    passenger.setSchedule(schedules.create(request.slots()));
    addresses.upsertForUnlinkedPassenger(passenger, request.address());
    return mapper.toResponse(passengers.save(passenger));
  }

  @Transactional
  public UnlinkedPassengerResponseDTO update(
      String callerUid, String token, UnlinkedPassengerUpdateRequestDTO request) {
    UnlinkedPassengerModel passenger = requireOwnPassenger(callerUid, token);
    applyName(request.name(), passenger);
    applySchool(request.schoolToken(), passenger);
    applySchoolShift(request.schoolShift(), passenger);
    applyNotes(request.notes(), passenger);
    applyAddress(request.address(), passenger);
    return mapper.toResponse(passengers.save(passenger));
  }

  @Transactional
  public UnlinkedPassengerResponseDTO replaceSchedule(
      String callerUid, String token, UnlinkedPassengerScheduleRequestDTO request) {
    UnlinkedPassengerModel passenger = requireOwnPassenger(callerUid, token);
    schedules.replace(passenger.getSchedule(), request.slots());
    return mapper.toResponse(passenger);
  }

  @Transactional
  public void delete(String callerUid, String token) {
    UnlinkedPassengerModel passenger = requireOwnPassenger(callerUid, token);
    // Removing the address first makes Hibernate null the passenger's NOT NULL address_id.
    passengers.delete(passenger);
    addresses.clearForUnlinkedPassenger(passenger);
  }

  void applyName(JsonNullable<String> name, UnlinkedPassengerModel passenger) {
    if (!name.isPresent()) {
      return;
    }
    if (!StringUtils.hasText(name.get())) {
      throw badRequest("unlinked_passenger.name.required");
    }
    passenger.setName(name.get());
  }

  void applySchool(JsonNullable<String> schoolToken, UnlinkedPassengerModel passenger) {
    if (schoolToken.isPresent()) {
      passenger.setSchool(requireSchool(requireNonNullField(schoolToken.get())));
    }
  }

  void applySchoolShift(JsonNullable<SchoolShift> schoolShift, UnlinkedPassengerModel passenger) {
    if (schoolShift.isPresent()) {
      passenger.setSchoolShift(requireNonNullField(schoolShift.get()));
    }
  }

  void applyNotes(JsonNullable<String> notes, UnlinkedPassengerModel passenger) {
    if (notes.isPresent()) {
      passenger.setNotes(notes.get());
    }
  }

  void applyAddress(
      JsonNullable<DependentAddressRequestDTO> address, UnlinkedPassengerModel passenger) {
    if (address.isPresent()) {
      addresses.upsertForUnlinkedPassenger(passenger, requireNonNullField(address.get()));
    }
  }

  <T> T requireNonNullField(T value) {
    if (value == null) {
      throw badRequest("unlinked_passenger.field.null");
    }
    return value;
  }

  UnlinkedPassengerModel requireOwnPassenger(String callerUid, String token) {
    return passengers
        .findByTokenAndDriverId(token, requireDriver(callerUid).getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("unlinked_passenger.not_found")));
  }

  SchoolModel requireSchool(String schoolToken) {
    return schools
        .findByToken(schoolToken)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("unlinked_passenger.school.not_found")));
  }

  DriverModel requireDriver(String callerUid) {
    UserModel user = users.requireByTokenAndType(callerUid, UserType.DRIVER);
    return drivers
        .findByUserId(user.getId())
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, message("user.driver_profile.not_found")));
  }

  ResponseStatusException badRequest(String key) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message(key));
  }

  String message(String key) {
    return messages.getMessage(key, null, LocaleContextHolder.getLocale());
  }
}
