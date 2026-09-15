package br.com.vanep.city.seed;

import br.com.vanep.city.model.CityModel;
import br.com.vanep.city.repository.CityRepository;
import br.com.vanep.state.model.StateModel;
import br.com.vanep.state.repository.StateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class CitySeeder {
  private static final Logger log = LoggerFactory.getLogger(CitySeeder.class);

  private final CityRepository cities;
  private final StateRepository states;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Resource municipalitiesDump;

  public CitySeeder(
      CityRepository cities,
      StateRepository states,
      @Value("${vanep.seed.ibge-municipalities:classpath:seed/ibge-municipalities.json}")
          Resource municipalitiesDump) {
    this.cities = cities;
    this.states = states;
    this.municipalitiesDump = municipalitiesDump;
  }

  public void seed() {
    JsonNode dump = readDump();
    int created = 0;
    for (JsonNode municipality : dump) {
      String ibgeCode = municipality.path("id").asText();
      String name = municipality.path("nome").asText();
      String uf = readUf(municipality);
      if (ibgeCode.isBlank() || name.isBlank() || uf.isBlank()) {
        log.warn(
            "Seed: skipping IBGE municipality missing id, nome, or UF (id={}, nome={}).",
            ibgeCode,
            name);
        continue;
      }
      if (cities.findByIbgeCode(ibgeCode).isPresent()) {
        continue;
      }
      StateModel state =
          states
              .findByUf(uf)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Seed: no curated state for UF %s (ibge_code %s)."
                              .formatted(uf, ibgeCode)));
      CityModel city = new CityModel();
      city.setIbgeCode(ibgeCode);
      city.setName(name);
      city.setState(state);
      cities.save(city);
      created++;
    }
    if (created > 0) {
      log.info("Seed: {} cities created from IBGE dump.", created);
    }
  }

  private String readUf(JsonNode municipality) {
    String uf =
        municipality.path("microrregiao").path("mesorregiao").path("UF").path("sigla").asText();
    if (!uf.isBlank()) {
      return uf;
    }
    return municipality
        .path("regiao-imediata")
        .path("regiao-intermediaria")
        .path("UF")
        .path("sigla")
        .asText();
  }

  private JsonNode readDump() {
    if (!municipalitiesDump.exists()) {
      throw new IllegalStateException("Seed: IBGE municipality dump is missing.");
    }
    try (InputStream input = municipalitiesDump.getInputStream()) {
      JsonNode dump = objectMapper.readTree(input);
      if (dump == null || !dump.isArray()) {
        throw new IllegalStateException("Seed: IBGE municipality dump must be a JSON array.");
      }
      return dump;
    } catch (IOException ex) {
      throw new IllegalStateException("Seed: failed to read IBGE municipality dump.", ex);
    }
  }
}
