package zw.ac.uz.dpdms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthConfiguration {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  CommandLineRunner seedUsers(UserRepository users, PasswordEncoder encoder,
      @Value("${dpdms.auth.seed-demo-users:true}") boolean seedDemoUsers,
      @Value("${DPDMS_DEMO_PASSWORD:ChangeMe123!}") String demoPassword,
      @Value("${DPDMS_BOOTSTRAP_ADMIN_USERNAME:}") String bootstrapUsername,
      @Value("${DPDMS_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
    return args -> {
      if (seedDemoUsers) seedDemoAccounts(users, encoder, demoPassword);
      if (bootstrapUsername.isBlank() != bootstrapPassword.isBlank()) {
        throw new IllegalStateException("Set both DPDMS_BOOTSTRAP_ADMIN_USERNAME and DPDMS_BOOTSTRAP_ADMIN_PASSWORD");
      }
      if (!bootstrapUsername.isBlank()) {
        if (bootstrapPassword.length() < 12) throw new IllegalStateException("DPDMS_BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
        seed(users, encoder, bootstrapUsername, bootstrapPassword, "NATIONAL", "", "");
      }
      if (!seedDemoUsers && bootstrapUsername.isBlank() && users.count() == 0) {
        throw new IllegalStateException("Configure a bootstrap National account or enable demo users");
      }
    };
  }

  private void seedDemoAccounts(UserRepository users, PasswordEncoder encoder, String demoPassword) {
    if (demoPassword.length() < 12) throw new IllegalStateException("DPDMS_DEMO_PASSWORD must be at least 12 characters");
    seed(users, encoder, "national@dpdms.local", demoPassword, "NATIONAL", "", "");
    for (String hazard : new String[] {"FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING"}) {
      String slug = hazard.toLowerCase().replace("_", "");
      seed(users, encoder, slug + ".recorder.ward1", demoPassword, "RECORDER", hazard, "Rushinga Ward 1");
      seed(users, encoder, slug + ".supervisor", demoPassword, "SUPERVISOR", hazard, "");
      seed(users, encoder, slug + ".provincial.admin", demoPassword, "PROVINCIAL_ADMIN", hazard, "");
    }
  }

  private void seed(UserRepository users, PasswordEncoder encoder, String username, String password,
      String role, String hazard, String ward) {
    if (users.findByUsername(username).isPresent()) return;
    AppUser user = new AppUser();
    user.username = username;
    user.passwordHash = encoder.encode(password);
    user.role = role;
    user.hazard = hazard;
    user.ward = ward;
    users.save(user);
  }
}
