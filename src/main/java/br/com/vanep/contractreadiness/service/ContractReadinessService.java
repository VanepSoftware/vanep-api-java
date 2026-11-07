package br.com.vanep.contractreadiness.service;

import br.com.vanep.client.repository.ClientRepository;
import br.com.vanep.contractreadiness.dto.ContractReadinessClientDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessDependentDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessMissingDTO;
import br.com.vanep.contractreadiness.dto.ContractReadinessResponseDTO;
import br.com.vanep.dependent.model.DependentModel;
import br.com.vanep.dependent.repository.DependentRepository;
import br.com.vanep.dependent.repository.DependentSchoolDayRepository;
import br.com.vanep.user.enums.UserType;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContractReadinessService {

  private final UserService users;
  private final ClientRepository clients;
  private final DependentRepository dependents;
  private final DependentSchoolDayRepository schoolDays;
  private final ContractReadinessPolicy policy;
  private final MessageSource messages;

  public ContractReadinessService(
      UserService users,
      ClientRepository clients,
      DependentRepository dependents,
      DependentSchoolDayRepository schoolDays,
      ContractReadinessPolicy policy,
      MessageSource messages) {
    this.users = users;
    this.clients = clients;
    this.dependents = dependents;
    this.schoolDays = schoolDays;
    this.policy = policy;
    this.messages = messages;
  }

  @Transactional(readOnly = true)
  public ContractReadinessResponseDTO findReadiness(
      String callerUid, List<String> dependentTokens) {
    UserModel caller = users.requireByTokenAndType(callerUid, UserType.CLIENT);
    List<String> tokens =
        dependentTokens == null ? List.of() : dependentTokens.stream().distinct().toList();
    List<ContractReadinessMissingDTO> missing =
        policy.findMissing(toClientData(caller), toDependentData(caller, tokens));
    return new ContractReadinessResponseDTO(missing.isEmpty(), missing);
  }

  private ContractReadinessClientDTO toClientData(UserModel caller) {
    return new ContractReadinessClientDTO(
        caller.getDocument(), caller.getPhone(), caller.getAddressId());
  }

  private List<ContractReadinessDependentDTO> toDependentData(
      UserModel caller, List<String> tokens) {
    if (tokens.isEmpty()) {
      return List.of();
    }
    List<DependentModel> chosen = requireOwnDependents(caller, tokens);
    Map<Long, List<DayOfWeek>> weekdaysByDependent =
        schoolDays
            .findByDependentIdIn(chosen.stream().map(dependent -> dependent.getId()).toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    day -> day.getDependentId(),
                    Collectors.mapping(day -> day.getWeekday(), Collectors.toList())));
    return chosen.stream()
        .map(
            dependent ->
                new ContractReadinessDependentDTO(
                    dependent.getToken(),
                    dependent.getBirthDate(),
                    dependent.getSchoolId(),
                    dependent.getAddressId(),
                    weekdaysByDependent.getOrDefault(dependent.getId(), List.of())))
        .toList();
  }

  private List<DependentModel> requireOwnDependents(UserModel caller, List<String> tokens) {
    Long clientId =
        clients
            .findByUserId(caller.getId())
            .map(client -> client.getId())
            .orElseThrow(() -> notFound("client.profile.not_found"));
    Map<String, DependentModel> byToken =
        dependents.findByTokenInAndClientId(tokens, clientId).stream()
            .collect(Collectors.toMap(dependent -> dependent.getToken(), dependent -> dependent));
    if (byToken.size() < tokens.size()) {
      throw notFound("dependent.not_found");
    }
    return tokens.stream().map(token -> byToken.get(token)).toList();
  }

  private ResponseStatusException notFound(String key) {
    return new ResponseStatusException(
        HttpStatus.NOT_FOUND, messages.getMessage(key, null, LocaleContextHolder.getLocale()));
  }
}
