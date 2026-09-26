package zw.ac.uz.dpdms.report;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
public class ReportSecurityConfiguration {
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/actuator/info", "/swagger-ui/**", "/v3/api-docs/**").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt()).build();
  }

  @Bean JwtDecoder jwtDecoder(@Value("${DPDMS_JWT_SECRET:change-this-demo-secret-to-at-least-32-characters}") String secret) {
    return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256")).build();
  }

  @Bean @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder() { return RestClient.builder(); }
}
