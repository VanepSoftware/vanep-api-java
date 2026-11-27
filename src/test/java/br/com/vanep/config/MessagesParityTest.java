package br.com.vanep.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MessagesParityTest {

  @Test
  void englishAndPortugueseBundlesHaveIdenticalKeysAndNoDuplicates() throws IOException {
    Bundle en = readBundle("messages.properties");
    Bundle ptBr = readBundle("messages_pt_BR.properties");

    assertThat(en.duplicates()).as("Duplicate keys in messages.properties").isEmpty();
    assertThat(ptBr.duplicates()).as("Duplicate keys in messages_pt_BR.properties").isEmpty();
    assertThat(ptBr.keys())
        .as("Keys in messages_pt_BR.properties vs messages.properties")
        .containsExactlyInAnyOrderElementsOf(en.keys());
  }

  private static Bundle readBundle(String path) throws IOException {
    String content = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    Map<String, Integer> counts = new LinkedHashMap<>();
    Set<String> keys = new LinkedHashSet<>();
    for (String rawLine : content.split("\\R")) {
      String line = rawLine.strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      int eq = line.indexOf('=');
      if (eq > 0) {
        String key = line.substring(0, eq).strip();
        keys.add(key);
        counts.merge(key, 1, Integer::sum);
      }
    }
    List<String> duplicates =
        counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    return new Bundle(keys, duplicates);
  }

  private record Bundle(Set<String> keys, List<String> duplicates) {}
}
