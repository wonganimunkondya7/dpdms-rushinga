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
      @Value("${DPDMS_ALLOW_ALL_HAZARD_DEMO_SCOPES:false}") boolean allowAllHazardDemoScopes,
      @Value("${DPDMS_BOOTSTRAP_ADMIN_USERNAME:}") String bootstrapUsername,
      @Value("${DPDMS_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword) {
    return args -> {
      if (seedDemoUsers) seedDemoAccounts(users, encoder, demoPassword, allowAllHazardDemoScopes);
      migrateBroadHazardAccounts(users);
      migrateProvincialAccounts(users);
      if (bootstrapUsername.isBlank() != bootstrapPassword.isBlank()) {
        throw new IllegalStateException("Set both DPDMS_BOOTSTRAP_ADMIN_USERNAME and DPDMS_BOOTSTRAP_ADMIN_PASSWORD");
      }
      if (!bootstrapUsername.isBlank()) {
        if (bootstrapPassword.length() < 12) throw new IllegalStateException("DPDMS_BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
        seed(users, encoder, bootstrapUsername, bootstrapPassword, "NATIONAL", "", "", "");
      }
      if (!seedDemoUsers && bootstrapUsername.isBlank() && users.count() == 0) {
        throw new IllegalStateException("Configure a bootstrap National account or enable demo users");
      }
    };
  }

  private void seedDemoAccounts(UserRepository users, PasswordEncoder encoder, String demoPassword, boolean allowAllHazardDemoScopes) {
    if (demoPassword.length() < 12) throw new IllegalStateException("DPDMS_DEMO_PASSWORD must be at least 12 characters");
    seed(users, encoder, "national@dpdms.local", demoPassword, "NATIONAL", "", "", "");
    String commonDemoScope = allowAllHazardDemoScopes ? "ALL" : "FLOOD";
    seed(users, encoder, "recorder.ward1", demoPassword, "RECORDER", commonDemoScope, "Rushinga Ward 1", "");
    seed(users, encoder, "supervisor", demoPassword, "SUPERVISOR", commonDemoScope, "", "");
    seed(users, encoder, "provincial.admin", demoPassword, "PROVINCIAL_ADMIN", "ALL", "", "Mashonaland Central");
    for (String hazard : new String[] {"FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING"}) {
      String slug = hazard.toLowerCase().replace("_", "");
      seed(users, encoder, slug + ".recorder.ward1", demoPassword, "RECORDER", hazard, "Rushinga Ward 1", "");
      seed(users, encoder, slug + ".supervisor", demoPassword, "SUPERVISOR", hazard, "", "");
    }
  }

  private void migrateProvincialAccounts(UserRepository users) {
    for (AppUser user : users.findAll()) {
      if (!"PROVINCIAL_ADMIN".equals(user.role)) continue;
      user.hazard = "ALL";
      user.ward = "";
      if (user.province == null || user.province.isBlank()) user.province = "Mashonaland Central";
      users.save(user);
    }
  }

  private void migrateBroadHazardAccounts(UserRepository users) {
    for (AppUser user : users.findAll()) {
      if ("ALL".equals(user.hazard)
          && ("RECORDER".equals(user.role) || "SUPERVISOR".equals(user.role))) {
        user.hazard = "FLOOD";
        users.save(user);
      }
    }
  }

  private void seed(UserRepository users, PasswordEncoder encoder, String username, String password,
      String role, String hazard, String ward, String province) {
    AppUser existing = users.findByUsername(username).orElse(null);
    if (existing != null) {
      if (("RECORDER".equals(role) || "SUPERVISOR".equals(role)) && existing.role.equals(role)) {
        existing.hazard = hazard;
        existing.ward = ward;
        existing.province = province;
        users.save(existing);
      }
      if ("PROVINCIAL_ADMIN".equals(role) && "PROVINCIAL_ADMIN".equals(existing.role)) {
        existing.hazard = "ALL";
        existing.ward = "";
        existing.province = province;
        users.save(existing);
      }
      return;
    }
    AppUser user = new AppUser();
    user.username = username;
    user.passwordHash = encoder.encode(password);
    user.role = role;
    user.hazard = hazard;
    user.ward = ward;
    user.province = province;
    users.save(user);
  }
}
