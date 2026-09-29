package br.com.vanep.driverrating.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.vanep.clientdriver.model.ClientDriverModel;
import br.com.vanep.clientdriver.repository.ClientDriverRepository;
import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.driverrating.repository.DriverRatingRepository;
import br.com.vanep.driverrating.service.DriverRatingService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DriverRatingSeederTest {

  @Mock private DriverRatingRepository driverRatingRepository;
  @Mock private ClientDriverRepository linkRepository;
  @Mock private DriverRatingService driverRatingService;

  private DriverRatingSeeder seeder;
  private ClientDriverModel link;
  private DriverModel driver;

  @BeforeEach
  void setUp() {
    seeder = new DriverRatingSeeder(driverRatingRepository, linkRepository, driverRatingService);

    driver = new DriverModel();
    driver.setId(2L);
    link = new ClientDriverModel();
    link.setId(7L);
    link.setDriver(driver);
  }

  @Test
  void seedingARatingRecalculatesTheDriverAverage() {
    when(linkRepository.findPage(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(link)));
    when(driverRatingRepository.existsByLinkId(7L)).thenReturn(false);

    seeder.seed();

    verify(driverRatingRepository).save(any());
    verify(driverRatingService).recalculateDriverAverage(driver);
  }

  @Test
  void anAlreadyRatedLinkIsLeftAlone() {
    when(linkRepository.findPage(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(link)));
    when(driverRatingRepository.existsByLinkId(7L)).thenReturn(true);

    seeder.seed();

    verify(driverRatingRepository, never()).save(any());
    verify(driverRatingService, never()).recalculateDriverAverage(any());
  }
}
