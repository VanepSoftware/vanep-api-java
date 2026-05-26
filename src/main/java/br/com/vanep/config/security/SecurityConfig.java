package br.com.vanep.config.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Proteção HTTP: {@code /api/**} com Basic auth (stateless, sem CSRF), exceto {@code POST /api/users}
 * (cadastro público). Documentação OpenAPI pública
 * ou bloqueada conforme {@code vanep.security.swagger-enabled}. Sobrescreva user/senha por
 * propriedades {@code vanep.security.http-basic.*} ou variáveis de ambiente equivalentes.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final boolean swaggerEnabled;
  private final boolean permitAll;
  private final String httpBasicUsername;
  private final String httpBasicPassword;
  private final String corsAllowedOrigins;
  private final PasswordEncoder passwordEncoder;

  public SecurityConfig(
      @Value("${vanep.security.swagger-enabled:true}") boolean swaggerEnabled,
      @Value("${vanep.security.permit-all:false}") boolean permitAll,
      @Value("${vanep.security.http-basic.username:api-user}") String httpBasicUsername,
      @Value("${vanep.security.http-basic.password:changeme}") String httpBasicPassword,
      @Value("${vanep.security.cors-allowed-origins:}") String corsAllowedOrigins,
      PasswordEncoder passwordEncoder) {
    this.swaggerEnabled = swaggerEnabled;
    this.permitAll = permitAll;
    this.httpBasicUsername = httpBasicUsername;
    this.httpBasicPassword = httpBasicPassword;
    this.corsAllowedOrigins = corsAllowedOrigins;
    this.passwordEncoder = passwordEncoder;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable());
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.httpBasic(Customizer.withDefaults());

    if (StringUtils.hasText(corsAllowedOrigins)) {
      http.cors(cors -> cors.configurationSource(corsConfigurationSource(corsAllowedOrigins)));
    }

    http.authorizeHttpRequests(
        auth -> {
          if (permitAll) {
            auth.anyRequest().permitAll();
            return;
          }

          auth.requestMatchers("/error").permitAll();

          if (swaggerEnabled) {
            auth.requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**")
                .permitAll();
          } else {
            auth.requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**")
                .denyAll();
          }

          auth.requestMatchers(HttpMethod.POST, "/api/users").permitAll();
          auth.requestMatchers("/api/**").authenticated();
          auth.anyRequest().denyAll();
        });

    return http.build();
  }

  @Bean
  public UserDetailsService userDetailsService() {
    UserDetails user =
        User.builder()
            .username(httpBasicUsername)
            .password(passwordEncoder.encode(httpBasicPassword))
            .roles("API")
            .build();
    return new InMemoryUserDetailsManager(user);
  }

  private static CorsConfigurationSource corsConfigurationSource(String corsAllowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    for (String origin : splitCsv(corsAllowedOrigins)) {
      configuration.addAllowedOriginPattern(origin);
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }

  private static List<String> splitCsv(String raw) {
    return Arrays.stream(raw.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
  }
}
