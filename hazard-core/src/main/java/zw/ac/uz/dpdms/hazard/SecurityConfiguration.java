package zw.ac.uz.dpdms.hazard;
import javax.crypto.spec.SecretKeySpec; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity; import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.oauth2.jwt.*;
@Configuration public class SecurityConfiguration {
 @Bean SecurityFilterChain security(HttpSecurity h) throws Exception { return h.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/info","/swagger-ui/**","/v3/api-docs/**").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt()).build(); }
 @Bean JwtDecoder jwtDecoder(@Value("${DPDMS_JWT_SECRET:change-this-demo-secret-to-at-least-32-characters}") String secret){return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(),"HmacSHA256")).build();}
}
