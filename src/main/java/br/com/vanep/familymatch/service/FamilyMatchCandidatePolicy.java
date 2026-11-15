package br.com.vanep.familymatch.service;

import br.com.vanep.unlinkedpassenger.model.UnlinkedPassengerModel;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class FamilyMatchCandidatePolicy {

  public Optional<UnlinkedPassengerModel> pickCandidate(
      String dependentName, List<UnlinkedPassengerModel> unmergedAtSameSchool) {
    String firstName = normalizeFirstName(dependentName);
    if (firstName.isEmpty()) {
      return Optional.empty();
    }
    List<UnlinkedPassengerModel> sameFirstName =
        unmergedAtSameSchool.stream()
            .filter(passenger -> normalizeFirstName(passenger.getName()).equals(firstName))
            .toList();
    return sameFirstName.size() == 1 ? Optional.of(sameFirstName.getFirst()) : Optional.empty();
  }

  public String normalizeFirstName(String name) {
    if (name == null) {
      return "";
    }
    String trimmed = name.strip();
    if (trimmed.isEmpty()) {
      return "";
    }
    String firstName = trimmed.split("\\s+")[0];
    return Normalizer.normalize(firstName, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}
