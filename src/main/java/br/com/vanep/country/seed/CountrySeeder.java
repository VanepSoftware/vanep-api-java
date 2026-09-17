package br.com.vanep.country.seed;

import br.com.vanep.country.model.CountryModel;
import br.com.vanep.country.repository.CountryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CountrySeeder {

  private static final Logger log = LoggerFactory.getLogger(CountrySeeder.class);

  private final CountryRepository countryRepository;

  public CountrySeeder(CountryRepository countryRepository) {
    this.countryRepository = countryRepository;
  }

  public CountryModel seed() {
    return countryRepository
        .findByName("Brasil")
        .orElseGet(
            () -> {
              CountryModel country = new CountryModel();
              country.setName("Brasil");
              country.setIsoCode("BR");
              country.setPhoneCode("+55");
              country.setCurrency("BRL");
              country.setLocale("pt-BR");
              CountryModel saved = countryRepository.save(country);
              log.info("Seed: default country (Brasil) created.");
              return saved;
            });
  }
}
