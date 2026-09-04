package br.com.vanep.state.seed;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The 27 Brazilian federative units, curated in one place.
 *
 * <p>This list is the single source of truth for {@code requires_district} (D8) as well. It used to
 * live in two places — an {@code UPDATE} inside the district migration and a {@code Set.of("DF",
 * "SP")} in the resolver — which disagreed by construction: the migration only reaches rows that
 * already exist, so a state row created after it ran was born with the flag false. The migration
 * now only creates the columns; the values come from here.
 *
 * <p>Opening a new market is a one-line change here, and it applies to existing rows too — the seed
 * re-asserts the curated flag on every run instead of skipping states it has already created.
 */
@Component
public class StateSeeder {

  private static final Logger log = LoggerFactory.getLogger(StateSeeder.class);

  /**
   * @param requiresDistrict D8: the city level is useless in this UF, so a driver's service area
   *     has to be declared below it. True for the Distrito Federal (a single municipality covering
   *     5,800 km², from Gama to Sobradinho) and for São Paulo.
   */
  private record CuratedState(String uf, String name, boolean requiresDistrict) {}

  private static final List<CuratedState> BRAZILIAN_STATES =
      List.of(
          new CuratedState("AC", "Acre", false),
          new CuratedState("AL", "Alagoas", false),
          new CuratedState("AP", "Amapá", false),
          new CuratedState("AM", "Amazonas", false),
          new CuratedState("BA", "Bahia", false),
          new CuratedState("CE", "Ceará", false),
          new CuratedState("DF", "Distrito Federal", true),
          new CuratedState("ES", "Espírito Santo", false),
          new CuratedState("GO", "Goiás", false),
          new CuratedState("MA", "Maranhão", false),
          new CuratedState("MT", "Mato Grosso", false),
          new CuratedState("MS", "Mato Grosso do Sul", false),
          new CuratedState("MG", "Minas Gerais", false),
          new CuratedState("PA", "Pará", false),
          new CuratedState("PB", "Paraíba", false),
          new CuratedState("PR", "Paraná", false),
          new CuratedState("PE", "Pernambuco", false),
          new CuratedState("PI", "Piauí", false),
          new CuratedState("RJ", "Rio de Janeiro", false),
          new CuratedState("RN", "Rio Grande do Norte", false),
          new CuratedState("RS", "Rio Grande do Sul", false),
          new CuratedState("RO", "Rondônia", false),
          new CuratedState("RR", "Roraima", false),
          new CuratedState("SC", "Santa Catarina", false),
          new CuratedState("SP", "São Paulo", true),
          new CuratedState("SE", "Sergipe", false),
          new CuratedState("TO", "Tocantins", false));

  private final StateRepository states;
  private final CountryRepository countryRepository;

  public StateSeeder(StateRepository states, CountryRepository countryRepository) {
    this.states = states;
    this.countryRepository = countryRepository;
  }

  public void seed() {
    int created = 0;
    int updated = 0;
    CountryModel country =
        countryRepository
            .findByName("Brasil")
            .orElseThrow(
                () -> new IllegalStateException("Seed: default country (Brasil) not found."));

    for (CuratedState curated : BRAZILIAN_STATES) {
      StateModel state = states.findByUf(curated.uf()).orElse(null);
      if (state == null) {
        state = new StateModel();
        state.setUf(curated.uf());
        state.setName(curated.name());
        state.setCountry(country);
        state.setRequiresDistrict(curated.requiresDistrict());
        states.save(state);
        created++;
      } else if (state.isRequiresDistrict() != curated.requiresDistrict()) {
        state.setRequiresDistrict(curated.requiresDistrict());
        states.save(state);
        updated++;
      }
    }
    if (created > 0) {
      log.info("Seed: {} states created.", created);
    }
    if (updated > 0) {
      log.info("Seed: requires_district re-asserted on {} states.", updated);
    }
  }
}
