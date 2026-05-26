package br.com.vanep;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "vanep.security.permit-all=true")
class VanepApplicationTests {

  @Test
  void contextLoads() {}
}
