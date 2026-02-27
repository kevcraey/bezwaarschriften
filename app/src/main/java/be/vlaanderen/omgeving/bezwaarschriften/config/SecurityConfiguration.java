package be.vlaanderen.omgeving.bezwaarschriften.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            // TODO: API-authenticatie herstellen zodra OAuth opnieuw geconfigureerd is.
            // Tijdelijk permitAll voor dev-fase; vorige config vereiste BezwaarschriftenGebruiker.
            .requestMatchers("/admin/health/**", "/admin/info", "/api/v1/**").permitAll()
            .anyRequest().authenticated());
    return http.build();
  }
}
