package zw.ac.uz.dpdms.alert;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AlertSecurityConfiguration {
  @Bean
  SecurityFilterChain alertSecurity(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info", "/swagger-ui/**", "/v3/api-docs/**",
                "/api/webhooks/whatsapp").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt())
        .build();
  }

  @Bean
  JwtDecoder alertJwtDecoder(@Value("${DPDMS_JWT_SECRET:change-this-demo-secret-to-at-least-32-characters}") String secret) {
    return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256")).build();
  }
}
