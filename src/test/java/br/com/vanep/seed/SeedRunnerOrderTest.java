package br.com.vanep.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.test.context.ActiveProfiles;

// Exception to rule 54: this guards runner wiring, not seed data. Without the @Order on
// GeographicDataSeeder, DataSeeder runs first and the seed school is skipped with only a log.
@SpringBootTest
@ActiveProfiles("test")
class SeedRunnerOrderTest {

  @Autowired private ApplicationContext context;

  @Test
  void theGeographicCatalogRunsBeforeTheDemoDataThatReferencesItsCities() {
    List<Object> runners =
        new ArrayList<>(context.getBeansOfType(ApplicationRunner.class).values());
    AnnotationAwareOrderComparator.sort(runners);

    assertThat(indexOf(runners, GeographicDataSeeder.class))
        .isLessThan(indexOf(runners, DataSeeder.class));
  }

  private int indexOf(List<Object> runners, Class<?> type) {
    return runners.stream()
        .filter(runner -> type.isInstance(runner))
        .map(runner -> runners.indexOf(runner))
        .findFirst()
        .orElseThrow();
  }
}
