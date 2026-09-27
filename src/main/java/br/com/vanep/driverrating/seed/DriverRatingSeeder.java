package br.com.vanep.driverrating.seed;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driverrating.model.DriverRatingModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class DriverRatingSeeder {

  private static final Logger log = LoggerFactory.getLogger(DriverRatingSeeder.class);

  private final DriverRatingRepository driverRatingRepository;
  private final ClientDriverRepository linkRepository;

  public DriverRatingSeeder(
      DriverRatingRepository driverRatingRepository, ClientDriverRepository linkRepository) {
    this.driverRatingRepository = driverRatingRepository;
    this.linkRepository = linkRepository;
  }

  public void seed() {
    ClientDriverModel link =
        linkRepository.findPage(PageRequest.of(0, 1)).stream().findFirst().orElse(null);

    if (link == null || driverRatingRepository.existsByLinkId(link.getId())) {
      return;
    }

    DriverRatingModel rating = new DriverRatingModel();
    rating.setLink(link);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Excelente motorista! Muito pontual e atencioso.");

    driverRatingRepository.save(rating);
    log.info("Seed: driver rating created for link {}.", link.getToken());
  }
}
