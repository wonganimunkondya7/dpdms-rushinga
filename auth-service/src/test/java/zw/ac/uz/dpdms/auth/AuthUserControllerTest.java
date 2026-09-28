package zw.ac.uz.dpdms.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AuthUserControllerTest {
  private UserRepository users;
  private PasswordEncoder encoder;
  private AuthUserController controller;

  @BeforeEach void setUp() {
    users = mock(UserRepository.class);
    encoder = mock(PasswordEncoder.class);
    controller = new AuthUserController(users, encoder, false);
  }

  @Test void nationalCreatesHazardScopedRecorderWithHashedPassword() {
    when(users.findByUsername("recorder@example.com")).thenReturn(Optional.empty());
    when(encoder.encode("a-strong-password")).thenReturn("bcrypt-hash");
    Jwt national = jwt("NATIONAL");

    var response = controller.create(
        new CreateUserRequest("Recorder@Example.com", "a-strong-password", "RECORDER", "FLOOD", "Ward 4", null), national);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    var saved = org.mockito.ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertEquals("recorder@example.com", saved.getValue().username);
    assertEquals("bcrypt-hash", saved.getValue().passwordHash);
    assertEquals("FLOOD", saved.getValue().hazard);
    assertEquals("Ward 4", saved.getValue().ward);
  }

  @Test void rejectsAllHazardRecorderAndSupervisorScopesByDefault() {
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> controller.create(new CreateUserRequest("recorder.all", "a-strong-password", "RECORDER", "ALL", "Ward 1", null), jwt("NATIONAL"))).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
        () -> controller.create(new CreateUserRequest("supervisor.all", "a-strong-password", "SUPERVISOR", "ALL", "", null), jwt("NATIONAL"))).getStatusCode());
  }

  @Test void allowsAllHazardScopesOnlyWhenDemoOverrideIsEnabled() {
    controller = new AuthUserController(users, encoder, true);
    when(users.findByUsername("supervisor")).thenReturn(Optional.empty());
    when(encoder.encode("a-strong-password")).thenReturn("bcrypt-hash");
    var response = controller.create(new CreateUserRequest("supervisor", "a-strong-password", "SUPERVISOR", "ALL", "", null), jwt("NATIONAL"));
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    var saved = org.mockito.ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(saved.capture());
    assertEquals("ALL", saved.getValue().hazard);
  }

  @Test void nonNationalCannotCreateUsers() {
    assertThrows(AccessDeniedException.class,
        () -> controller.create(new CreateUserRequest("user", "a-strong-password", "RECORDER", "FLOOD", "Ward 1", null), jwt("SUPERVISOR")));
  }

  @Test void rejectsInvalidHazardScope() {
    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> controller.create(new CreateUserRequest("user", "a-strong-password", "SUPERVISOR", "OTHER", "", null), jwt("NATIONAL")));
    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
  }

  private Jwt jwt(String role) {
    Instant now = Instant.now();
    return new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "HS256"), Map.of("role", role));
  }
}
