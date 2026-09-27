package zw.ac.uz.dpdms.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class AlertApiTest {
  private AlertRepository logs;
  private AlertDeliveryService delivery;
  private MockMvc api;
  private Jwt authJwt;

  @BeforeEach void setUp() {
    logs = mock(AlertRepository.class);
    delivery = mock(AlertDeliveryService.class);
    authJwt = jwt("flood.supervisor", "SUPERVISOR", "FLOOD");
    when(logs.save(any(AlertLog.class))).thenAnswer(invocation -> { AlertLog alert = invocation.getArgument(0); alert.id = 123L; return alert; });
    api = MockMvcBuilders.standaloneSetup(new AlertController(logs, delivery,
        "groupof5pple@yahoo.com", "+263781330055"))
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          @Override public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && parameter.getParameterType().equals(Jwt.class);
          }
          @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
              NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return authJwt;
          }
        }).build();
  }

  @Test void emailAlertUsesConfiguredDefaultRecipientAndQueuesDelivery() throws Exception {
    api.perform(post("/api/alerts").contentType("application/json")
        .content("{\"hazard\":\"FLOOD\",\"channel\":\"EMAIL\",\"message\":\"High water level\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recipient").value("groupof5pple@yahoo.com"))
        .andExpect(jsonPath("$.deliveryStatus").value("QUEUED"));
    verify(delivery).deliver(123L);
  }

  @Test void allHazardSupervisorCanQueueAlertsForAnyHazard() throws Exception {
    authJwt = jwt("supervisor", "SUPERVISOR", "ALL");
    for (String hazard : java.util.List.of("FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING")) {
      api.perform(post("/api/alerts").contentType("application/json")
          .content("{\"hazard\":\"" + hazard + "\",\"channel\":\"EMAIL\",\"message\":\"Test conditions\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.hazard").value(hazard));
    }
    verify(delivery, times(5)).deliver(123L);
  }

  @Test void scopedSupervisorCanViewOnlyScopedHazardAlertHistory() throws Exception {
    when(logs.findByHazardOrderByCreatedAtDesc("FLOOD")).thenReturn(java.util.List.of());
    api.perform(get("/api/alerts"))
        .andExpect(status().isOk());
    verify(logs).findByHazardOrderByCreatedAtDesc("FLOOD");
  }

  @Test void rejectsUnknownChannel() throws Exception {
    api.perform(post("/api/alerts").contentType("application/json")
        .content("{\"hazard\":\"FLOOD\",\"channel\":\"SMS\",\"message\":\"Alert\"}"))
        .andExpect(status().isBadRequest());
  }

  private Jwt jwt(String subject, String role, String hazard) {
    Instant now = Instant.now();
    return new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "HS256"),
        Map.of("sub", subject, "role", role, "hazard", hazard));
  }
}
