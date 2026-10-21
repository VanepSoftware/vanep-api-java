package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HibernateJsonFormatMapperTest {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Snapshot(int version, String name, LocalDate birthDate) {}

  private final HibernateJsonFormatMapper mapper =
      new HibernateJsonFormatMapper(JsonMapper.builder().build());

  @Test
  void writesDatesAsIsoText() {
    String json =
        mapper.toString(new Snapshot(1, "Lucas", LocalDate.of(2016, 4, 2)), Snapshot.class);

    assertThat(json).contains("\"birthDate\":\"2016-04-02\"");
  }

  @Test
  void readsBackWhatItWrote() {
    Snapshot snapshot = new Snapshot(1, "Lucas", LocalDate.of(2016, 4, 2));

    Snapshot read = mapper.fromString(mapper.toString(snapshot, Snapshot.class), Snapshot.class);

    assertThat(read).isEqualTo(snapshot);
  }

  @Test
  void readsASnapshotWrittenByALaterVersionWithAnExtraField() {
    Snapshot read =
        mapper.fromString(
            "{\"version\":2,\"name\":\"Lucas\",\"birthDate\":\"2016-04-02\",\"nickname\":\"Lu\"}",
            Snapshot.class);

    assertThat(read).isEqualTo(new Snapshot(2, "Lucas", LocalDate.of(2016, 4, 2)));
  }

  @Test
  void keepsTheShapeOfTheListsAlreadyStoredAsJson() {
    List<String> workDays = List.of("MONDAY", "FRIDAY");

    assertThat(mapper.toString(workDays, List.class)).isEqualTo("[\"MONDAY\",\"FRIDAY\"]");
  }
}
