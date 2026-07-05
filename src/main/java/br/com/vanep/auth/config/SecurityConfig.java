package br.com.vanep.auth.config;

import br.com.vanep.auth.oauth.OAuthLoginSuccessHandler;
import br.com.vanep.auth.oauth.VanepOidcUserService;
import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
          Object roles = jwt.getClaim("roles");
          if (roles instanceof Collection<?> values) {
            values.forEach(role -> authorities.add(new SimpleGrantedAuthority(role.toString())));
          }
          Object permissions = jwt.getClaim("permissions");
          if (permissions instanceof Collection<?> values) {
            values.forEach(
                permission -> authorities.add(new SimpleGrantedAuthority(permission.toString())));
          }
          return authorities;
        });
    return converter;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain authorizationServerSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServer =
        new OAuth2AuthorizationServerConfigurer();
    RequestMatcher endpointsMatcher = authorizationServer.getEndpointsMatcher();

    http.securityMatcher(endpointsMatcher)
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
        .with(authorizationServer, Customizer.withDefaults())
        .exceptionHandling(
            exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain apiSecurityFilterChain(
      HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  @Order(3)
  public SecurityFilterChain defaultSecurityFilterChain(
      HttpSecurity http,
      @Value("${vanep.remember-me.key}") String rememberMeKey,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
      VanepOidcUserService oidcUserService,
      OAuthLoginSuccessHandler oauthLoginSuccessHandler)
      throws Exception {
    http.authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/login", "/error", "/css/**", "/images/**", "/webjars/**")
                    .permitAll()
                    .requestMatchers("/signup", "/signup/client", "/signup/driver")
                    .permitAll()
                    .requestMatchers(
                        "/verify-email",
                        "/verify-email/resend",
                        "/forgot-password",
                        "/reset-password")
                    .permitAll()
                    .requestMatchers("/auth/sso-logout")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info", "/actuator/mappings")
                    .permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(form -> form.loginPage("/login").usernameParameter("email").permitAll())
        .rememberMe(remember -> remember.key(rememberMeKey).rememberMeParameter("remember-me"))
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll());

    if (clientRegistrationRepository.getIfAvailable() != null) {
      http.oauth2Login(
          oauth ->
              oauth
                  .loginPage("/login")
                  .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                  .successHandler(oauthLoginSuccessHandler));
    }

    return http.build();
  }
}
