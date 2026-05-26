package br.com.vanep.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String HTTP_BASIC_SCHEME = "httpBasic";

  @Bean
  public OpenAPI vanepOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Vanep API")
                .description(
                    "API REST do Vanep. Rotas em /api/** exigem autenticação HTTP Basic até haver JWT ou outro fluxo.")
                .version("0.0.1-SNAPSHOT"))
        .components(
            new Components()
                .addSecuritySchemes(
                    HTTP_BASIC_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description(
                            "Usuário e senha em vanep.security.http-basic.* (valores por defeito no SecurityConfig).")))
        .addSecurityItem(new SecurityRequirement().addList(HTTP_BASIC_SCHEME));
  }
}
