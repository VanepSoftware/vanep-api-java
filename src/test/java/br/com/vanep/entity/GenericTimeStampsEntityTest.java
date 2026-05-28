package br.com.vanep.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GenericTimeStampsEntityTest {

  @Test
  void onCreate_setsTimestamps() {
    Fixture f = new Fixture();
    f.runCreate();

    assertThat(f.getCreatedAt()).isNotNull();
    assertThat(f.getUpdatedAt()).isNotNull();
    assertThat(f.getUpdatedAt()).isEqualTo(f.getCreatedAt());
  }

  @Test
  void onCreate_preservesExistingCreatedAt() {
    Fixture f = new Fixture();
    LocalDateTime past = LocalDateTime.now().minusDays(1);
    f.setCreatedAt(past);
    f.runCreate();

    assertThat(f.getCreatedAt()).isEqualTo(past);
    assertThat(f.getUpdatedAt()).isNotNull();
  }

  @Test
  void onUpdate_setsUpdatedAt() throws InterruptedException {
    Fixture f = new Fixture();
    f.runCreate();
    LocalDateTime firstUpdate = f.getUpdatedAt();
    Thread.sleep(2);
    f.runUpdate();

    assertThat(f.getUpdatedAt()).isAfter(firstUpdate);
  }

  private static final class Fixture extends GenericTimeStampsEntity {
    void runCreate() {
      onCreate();
    }

    void runUpdate() {
      onUpdate();
    }
  }
}
