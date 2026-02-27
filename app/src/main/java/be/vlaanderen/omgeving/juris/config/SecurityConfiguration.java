package be.vlaanderen.omgeving.juris.config;

import be.cumuli.security.api.CumuliSecurityConfigurer;
import be.cumuli.security.boot.config.CumuliSecurityProperties;
import be.cumuli.security.boot.config.OAuth2WebSecurityConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;

@Import(OAuth2WebSecurityConfiguration.class)
@EnableConfigurationProperties({CumuliSecurityProperties.class})
@Configuration
public class SecurityConfiguration {

  private static final String JURIS_GEBRUIKER = "JurisGebruiker";

  @Bean
  @Order(1)
  public CumuliSecurityConfigurer securityConfig() {
    return new CumuliSecurityConfigurer() {
      @Override
      public void configure(WebSecurity web) {
        configureWebSecurity(web);
      }

      @Override
      public void configure(HttpSecurity http) {
        configureEndpointSecurity(http);
      }

      @Override
      public void configure(AuthenticationManagerBuilder auth) throws Exception {}
    };
  }

  private static void configureWebSecurity(WebSecurity webSecurity) {
  }

  private static void configureEndpointSecurity(HttpSecurity http) {
    try {
      http.csrf().disable();
      http.authorizeRequests()
          .antMatchers("/**").hasAuthority(JURIS_GEBRUIKER);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }
}
