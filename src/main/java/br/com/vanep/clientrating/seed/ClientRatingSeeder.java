package br.com.vanep.clientrating.seed;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.clientrating.model.ClientRatingModel;
import br.com.vanep.clientrating.repository.ClientRatingRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientRatingSeeder {

  private static final Logger log = LoggerFactory.getLogger(ClientRatingSeeder.class);

  private final ClientRatingRepository clientRatingRepository;
  private final ClientDriverRepository linkRepository;

  public ClientRatingSeeder(
      ClientRatingRepository clientRatingRepository, ClientDriverRepository linkRepository) {
    this.clientRatingRepository = clientRatingRepository;
    this.linkRepository = linkRepository;
  }

  public void seed() {
    ClientDriverModel link =
        linkRepository.findPage(PageRequest.of(0, 1)).stream().findFirst().orElse(null);

    if (link == null || clientRatingRepository.existsByLinkId(link.getId())) {
      return;
    }

    ClientRatingModel rating = new ClientRatingModel();
    rating.setLink(link);
    rating.setRating(BigDecimal.valueOf(5.00));
    rating.setComment("Cliente pontual e educado.");

    clientRatingRepository.save(rating);
    log.info("Seed: client rating created for link {}.", link.getToken());
  }
}
