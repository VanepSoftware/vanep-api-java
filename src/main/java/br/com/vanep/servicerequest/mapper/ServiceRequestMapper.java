package br.com.vanep.servicerequest.mapper;

import br.com.vanep.address.model.AddressModel;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.proposal.dto.ProposalSummaryResponseDTO;
import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.servicerequest.dto.ServiceRequestClientItemResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestClientResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayRequestDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDayResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverItemResponseDTO;
import br.com.vanep.servicerequest.dto.ServiceRequestDriverResponseDTO;
import br.com.vanep.servicerequest.model.ServiceRequestDayModel;
import br.com.vanep.servicerequest.model.ServiceRequestItemModel;
import br.com.vanep.servicerequest.model.ServiceRequestModel;
import br.com.vanep.shared.enums.SchoolShift;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestMapper {

  public ServiceRequestItemModel toItemModel(
      DependentModel dependent, SchoolModel school, AddressModel pickup) {
    ServiceRequestItemModel item = new ServiceRequestItemModel();
    item.setDependentId(dependent.getId());
    item.setSchoolId(school.getId());
    item.setPickupCityId(pickup.getCity().getId());
    item.setSnapshot(toItemSnapshot(dependent, school, pickup));
    return item;
  }

  public ServiceRequestItemModel.Snapshot toItemSnapshot(
      DependentModel dependent, SchoolModel school, AddressModel pickup) {
    return new ServiceRequestItemModel.Snapshot(
        ServiceRequestItemModel.Snapshot.CURRENT_VERSION,
        new ServiceRequestItemModel.Snapshot.Dependent(
            dependent.getName(), dependent.getBirthDate()),
        new ServiceRequestItemModel.Snapshot.School(school.getName()),
        new ServiceRequestItemModel.Snapshot.Pickup(
            pickup.getCity().getName(),
            pickup.getCity().getState().getUf(),
            pickup.getNeighborhood()));
  }

  public ServiceRequestDayModel toDayModel(
      ServiceRequestDayRequestDTO request, SchoolShift schoolShift) {
    ServiceRequestDayModel day = new ServiceRequestDayModel();
    day.setWeekday(request.weekday());
    day.setSchoolShift(schoolShift);
    day.setNeedsOutbound(request.needsOutbound());
    day.setNeedsReturn(request.needsReturn());
    return day;
  }

  public ServiceRequestClientResponseDTO toClientResponse(
      ServiceRequestModel request,
      Map<Long, String> dependentTokens,
      ProposalSummaryResponseDTO currentProposal,
      String conversationToken) {
    return new ServiceRequestClientResponseDTO(
        request.getToken(),
        request.getClientDriver().getToken(),
        conversationToken,
        request.getClientDriver().getDriver().getToken(),
        request.getClientDriver().getDriver().getUser().getName(),
        request.getClientDriver().getDriver().getRating(),
        request.getStatus(),
        request.getDesiredStartDate(),
        request.getNotes(),
        request.getDeclineReason(),
        request.getExpiresAt(),
        currentProposal,
        itemsInOrder(request).stream()
            .map(item -> toClientItemResponse(item, dependentTokens))
            .toList(),
        request.getCreatedAt(),
        request.getUpdatedAt());
  }

  public ServiceRequestDriverResponseDTO toDriverResponse(
      ServiceRequestModel request,
      Map<Long, String> dependentTokens,
      LocalDate today,
      ProposalSummaryResponseDTO currentProposal,
      String conversationToken) {
    return new ServiceRequestDriverResponseDTO(
        request.getToken(),
        request.getClientDriver().getToken(),
        conversationToken,
        firstNameOf(request.getClientDriver().getClient().getUser().getName()),
        request.getStatus(),
        request.getDesiredStartDate(),
        request.getNotes(),
        request.getDeclineReason(),
        request.getExpiresAt(),
        currentProposal,
        itemsInOrder(request).stream()
            .map(item -> toDriverItemResponse(item, dependentTokens, today))
            .toList(),
        request.getCreatedAt(),
        request.getUpdatedAt());
  }

  ServiceRequestClientItemResponseDTO toClientItemResponse(
      ServiceRequestItemModel item, Map<Long, String> dependentTokens) {
    ServiceRequestItemModel.Snapshot snapshot = item.getSnapshot();
    return new ServiceRequestClientItemResponseDTO(
        dependentTokens.get(item.getDependentId()),
        snapshot.dependent().name(),
        snapshot.dependent().birthDate(),
        snapshot.school().name(),
        snapshot.pickup().cityName(),
        snapshot.pickup().stateUf(),
        snapshot.pickup().neighborhood(),
        toDayResponses(item));
  }

  ServiceRequestDriverItemResponseDTO toDriverItemResponse(
      ServiceRequestItemModel item, Map<Long, String> dependentTokens, LocalDate today) {
    ServiceRequestItemModel.Snapshot snapshot = item.getSnapshot();
    return new ServiceRequestDriverItemResponseDTO(
        dependentTokens.get(item.getDependentId()),
        firstNameOf(snapshot.dependent().name()),
        ageOn(snapshot.dependent().birthDate(), today),
        snapshot.school().name(),
        snapshot.pickup().cityName(),
        snapshot.pickup().stateUf(),
        snapshot.pickup().neighborhood(),
        toDayResponses(item));
  }

  List<ServiceRequestItemModel> itemsInOrder(ServiceRequestModel request) {
    return request.getItems().stream().sorted(Comparator.comparing(item -> item.getId())).toList();
  }

  List<ServiceRequestDayResponseDTO> toDayResponses(ServiceRequestItemModel item) {
    return item.getDays().stream()
        .sorted(Comparator.comparing(day -> day.getWeekday()))
        .map(
            day ->
                new ServiceRequestDayResponseDTO(
                    day.getWeekday(),
                    day.getSchoolShift(),
                    day.isNeedsOutbound(),
                    day.isNeedsReturn()))
        .toList();
  }

  public String firstNameOf(String name) {
    return name.strip().split("\\s+", 2)[0];
  }

  Integer ageOn(LocalDate birthDate, LocalDate today) {
    return birthDate == null ? null : Period.between(birthDate, today).getYears();
  }
}
