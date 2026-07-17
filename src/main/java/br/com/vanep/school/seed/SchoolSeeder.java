package br.com.vanep.school.seed;

import br.com.vanep.school.model.SchoolModel;
import br.com.vanep.school.repository.SchoolRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SchoolSeeder {

  private static final Logger log = LoggerFactory.getLogger(SchoolSeeder.class);

  private final SchoolRepository schools;

  public SchoolSeeder(SchoolRepository schools) {
    this.schools = schools;
  }

  public void seed() {
    createIfMissing(
        "Escola Municipal Monteiro Lobato",
        "11222333000181",
        "11999990001",
        "contato@monteirolobato.seed.vanep.com.br");
    createIfMissing(
        "Colégio Santa Clara",
        "44555666000172",
        "11999990002",
        "contato@santaclara.seed.vanep.com.br");
  }

  private void createIfMissing(String name, String cnpj, String phone, String email) {
    if (schools.existsByCnpj(cnpj)) {
      return;
    }
    SchoolModel school = new SchoolModel();
    school.setName(name);
    school.setCnpj(cnpj);
    school.setPhone(phone);
    school.setEmail(email);
    schools.save(school);
    log.info("Seed: school created ({}).", name);
  }
}
