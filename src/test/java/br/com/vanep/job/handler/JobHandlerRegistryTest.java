package br.com.vanep.job.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vanep.job.enums.JobType;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobHandlerRegistryTest {

  @Test
  void findsTheHandlerOfAType() {
    StubHandler push = new StubHandler();

    JobHandlerRegistry registry = new JobHandlerRegistry(List.of(push));

    assertThat(registry.findHandler(JobType.SEND_PUSH)).containsSame(push);
  }

  @Test
  void aTypeWithoutHandlerIsEmpty() {
    JobHandlerRegistry registry = new JobHandlerRegistry(List.of());

    assertThat(registry.findHandler(JobType.SEND_PUSH)).isEmpty();
  }

  @Test
  void twoHandlersForOneTypeFailAtStartup() {
    List<JobHandler<?>> duplicated = List.of(new StubHandler(), new StubHandler());

    assertThatThrownBy(() -> new JobHandlerRegistry(duplicated))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SEND_PUSH");
  }

  static class StubHandler implements JobHandler<String> {

    @Override
    public JobType type() {
      return JobType.SEND_PUSH;
    }

    @Override
    public Class<String> payloadType() {
      return String.class;
    }

    @Override
    public void perform(String payload) {
      // Intentionally empty: only the registration is under test.
    }
  }
}
