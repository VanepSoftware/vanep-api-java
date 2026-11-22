package br.com.vanep.vehicledocument.controller;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vanep.driver.model.DriverModel;
import br.com.vanep.schooltransportcredential.SchoolTransportCredentialFixtures;
import br.com.vanep.shared.document.enums.DocumentStatus;
import br.com.vanep.vehicledocument.enums.VehicleDocumentType;
import br.com.vanep.vehicledocument.model.VehicleDocumentModel;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// MockMvc skips the servlet container's multipart limit, so only a real server shows that a file
// up to the document limit reaches the media rule.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/db/clean.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VehicleDocumentUploadLimitIntegrationTest {

  private static final long BYTES_IN_MB = 1024L * 1024L;

  @LocalServerPort private int port;
  @Autowired private JWKSource<SecurityContext> jwkSource;
  @Autowired private ApplicationContext context;

  private String ownerUid;
  private String documentToken;

  @BeforeEach
  void setUp() {
    SchoolTransportCredentialFixtures fixtures = new SchoolTransportCredentialFixtures(context);
    DriverModel driver = fixtures.createDriver("carlos@vanep.com", "52998224725");
    ownerUid = driver.getUser().getToken();
    VehicleDocumentModel document =
        fixtures.createVehicleDocument(
            fixtures.createVehicle(driver, "ABC1D23"),
            VehicleDocumentType.CRLV,
            null,
            DocumentStatus.PENDING);
    documentToken = document.getToken();
  }

  @Test
  void aFileJustUnderTheDocumentLimitIsStored() {
    Upload upload = uploadPdfOf((long) (9.9 * BYTES_IN_MB));

    assertThat(upload.status()).isEqualTo(201);
  }

  @Test
  void aFileJustOverTheDocumentLimitReachesTheMediaRule() {
    Upload upload = uploadPdfOf((long) (10.1 * BYTES_IN_MB));

    assertThat(upload.status()).isEqualTo(413);
    assertThat(upload.code()).isEqualTo("media.file.too_large");
  }

  private Upload uploadPdfOf(long size) {
    byte[] content = new byte[(int) size];
    Arrays.fill(content, (byte) ' ');
    byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(header, 0, content, 0, header.length);
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add(
        "file",
        new ByteArrayResource(content) {
          @Override
          public String getFilename() {
            return "crlv.pdf";
          }
        });
    return RestClient.create("http://localhost:" + port)
        .post()
        .uri("/api/vehicle-documents/{token}/file", documentToken)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(ownerUid))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .exchange(
            (request, response) -> {
              String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
              String code = body.contains("\"code\"") ? JsonPath.read(body, "$.code") : null;
              return new Upload(response.getStatusCode().value(), code);
            });
  }

  private String tokenFor(String uid) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(uid)
            .claim("uid", uid)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
    return new NimbusJwtEncoder(jwkSource)
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }

  private record Upload(int status, String code) {}
}
