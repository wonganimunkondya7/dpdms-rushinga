package zw.ac.uz.dpdms.auth;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth/users")
public class AuthUserController {
  private static final Set<String> HAZARDS = Set.of("FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING");
  private static final Set<String> ROLES = Set.of("NATIONAL", "PROVINCIAL_ADMIN", "SUPERVISOR", "RECORDER");
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public AuthUserController(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
    if (!"NATIONAL".equals(jwt.getClaimAsString("role"))) {
      throw new AccessDeniedException("Only National administrators may create users");
    }
    String username = request.username().trim().toLowerCase(Locale.ROOT);
    String role = request.role().trim().toUpperCase(Locale.ROOT);
    String hazard = request.hazard() == null ? "" : request.hazard().trim().toUpperCase(Locale.ROOT);
    String ward = request.ward() == null ? "" : request.ward().trim();
    if (!ROLES.contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role");
    if ("NATIONAL".equals(role)) {
      if (!hazard.isBlank() || !ward.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "National accounts cannot have a hazard or ward scope");
    } else {
      if (!HAZARDS.contains(hazard)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid hazard scope is required");
      if ("RECORDER".equals(role) && ward.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recorder accounts require a ward");
      if (!"RECORDER".equals(role) && !ward.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only recorders may have a ward scope");
    }
    if (users.findByUsername(username).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
    AppUser user = new AppUser();
    user.username = username;
    user.passwordHash = encoder.encode(request.password());
    user.role = role;
    user.hazard = hazard;
    user.ward = ward;
    users.save(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username, "role", role, "hazard", hazard, "ward", ward));
  }
}
