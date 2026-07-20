package br.com.vanep.state.seed;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class StateSeeder {

  private static final Logger log = LoggerFactory.getLogger(StateSeeder.class);

  private static final Map<String, String> BRAZILIAN_STATES =
      Map.ofEntries(
          Map.entry("AC", "Acre"),
          Map.entry("AL", "Alagoas"),
          Map.entry("AP", "Amapá"),
          Map.entry("AM", "Amazonas"),
          Map.entry("BA", "Bahia"),
          Map.entry("CE", "Ceará"),
          Map.entry("DF", "Distrito Federal"),
          Map.entry("ES", "Espírito Santo"),
          Map.entry("GO", "Goiás"),
          Map.entry("MA", "Maranhão"),
          Map.entry("MT", "Mato Grosso"),
          Map.entry("MS", "Mato Grosso do Sul"),
          Map.entry("MG", "Minas Gerais"),
          Map.entry("PA", "Pará"),
          Map.entry("PB", "Paraíba"),
          Map.entry("PR", "Paraná"),
          Map.entry("PE", "Pernambuco"),
          Map.entry("PI", "Piauí"),
          Map.entry("RJ", "Rio de Janeiro"),
          Map.entry("RN", "Rio Grande do Norte"),
          Map.entry("RS", "Rio Grande do Sul"),
          Map.entry("RO", "Rondônia"),
          Map.entry("RR", "Roraima"),
          Map.entry("SC", "Santa Catarina"),
          Map.entry("SP", "São Paulo"),
          Map.entry("SE", "Sergipe"),
          Map.entry("TO", "Tocantins"));

  private final StateRepository states;
  private final CountryRepository countryRepository;

  public StateSeeder(StateRepository states, CountryRepository countryRepository) {
    this.states = states;
    this.countryRepository = countryRepository;
  }

  public void seed() {
    int created = 0;
    CountryModel country =
        countryRepository
            .findByName("Brasil")
            .orElseThrow(
                () -> new IllegalStateException("Seed: default country (Brasil) not found."));

    for (Map.Entry<String, String> entry : BRAZILIAN_STATES.entrySet()) {
      if (states.existsByUf(entry.getKey())) {
        continue;
      }
      StateModel state = new StateModel();
      state.setUf(entry.getKey());
      state.setName(entry.getValue());
      state.setCountry(country);
      states.save(state);
      created++;
    }
    if (created > 0) {
      log.info("Seed: {} states created.", created);
    }
  }
}
