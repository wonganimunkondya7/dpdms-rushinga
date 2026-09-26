package zw.ac.uz.dpdms.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfiguration {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  CommandLineRunner seed(UserRepository users, PasswordEncoder encoder) {
    return args -> {
      seed(users, encoder, "national@dpdms.local", "NATIONAL", "", "");
      for (String hazard : new String[] {"FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING"}) {
        String slug = hazard.toLowerCase().replace("_", "");
        seed(users, encoder, slug + ".recorder.ward1", "RECORDER", hazard, "Rushinga Ward 1");
        seed(users, encoder, slug + ".supervisor", "SUPERVISOR", hazard, "");
        seed(users, encoder, slug + ".provincial.admin", "PROVINCIAL_ADMIN", hazard, "");
      }
    };
  }

  private void seed(UserRepository users, PasswordEncoder encoder, String username, String role,
      String hazard, String ward) {
    if (users.findByUsername(username).isPresent()) return;
    AppUser user = new AppUser();
    user.username = username;
    user.passwordHash = encoder.encode("ChangeMe123!");
    user.role = role;
    user.hazard = hazard;
    user.ward = ward;
    users.save(user);
  }
}
