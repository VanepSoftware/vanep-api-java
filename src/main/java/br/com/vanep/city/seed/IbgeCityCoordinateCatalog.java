package br.com.vanep.city.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IbgeCityCoordinateCatalog {
  private static final String RESOURCE = "db/data/ibge-city-coordinates-2022.csv";
  private static final int EXPECTED_LOCALITIES = 5571;

  private IbgeCityCoordinateCatalog() {}

  public static Map<String, Coordinate> load() {
    InputStream input =
        IbgeCityCoordinateCatalog.class.getClassLoader().getResourceAsStream(RESOURCE);
    if (input == null) {
      throw new IllegalStateException("IBGE city coordinate catalog is missing.");
    }
    try (input;
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      if (!"ibge_code,latitude,longitude".equals(reader.readLine())) {
        throw new IllegalStateException("IBGE city coordinate catalog has an invalid header.");
      }
      Map<String, Coordinate> coordinates = new LinkedHashMap<>();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] values = line.split(",", -1);
        if (values.length != 3) {
          throw new IllegalStateException("IBGE city coordinate catalog has an invalid row.");
        }
        Coordinate previous =
            coordinates.put(
                values[0], new Coordinate(new BigDecimal(values[1]), new BigDecimal(values[2])));
        if (previous != null) {
          throw new IllegalStateException("IBGE city coordinate catalog has a duplicate code.");
        }
      }
      if (coordinates.size() != EXPECTED_LOCALITIES) {
        throw new IllegalStateException("IBGE city coordinate catalog is incomplete.");
      }
      return coordinates;
    } catch (IOException | NumberFormatException ex) {
      throw new IllegalStateException("Failed to read IBGE city coordinate catalog.", ex);
    }
  }

  public record Coordinate(BigDecimal latitude, BigDecimal longitude) {}
}
