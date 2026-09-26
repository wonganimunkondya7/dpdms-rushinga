package zw.ac.uz.dpdms.auth;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class AuthTokenConfiguration {
  @Bean
  JwtDecoder jwtDecoder(@Value("${DPDMS_JWT_SECRET:change-this-demo-secret-to-at-least-32-characters}") String secret) {
    return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(), "HmacSHA256")).build();
  }
}
