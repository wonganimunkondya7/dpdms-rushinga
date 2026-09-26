package zw.ac.uz.dpdms.alert;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

  @BeforeEach void setUp() {
    logs = mock(AlertRepository.class);
    delivery = mock(AlertDeliveryService.class);
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
            Instant now = Instant.now();
            return new Jwt("test", now, now.plusSeconds(60), Map.of("alg", "HS256"),
                Map.of("sub", "flood.supervisor", "role", "SUPERVISOR", "hazard", "FLOOD"));
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

  @Test void rejectsUnknownChannel() throws Exception {
    api.perform(post("/api/alerts").contentType("application/json")
        .content("{\"hazard\":\"FLOOD\",\"channel\":\"SMS\",\"message\":\"Alert\"}"))
        .andExpect(status().isBadRequest());
  }
}
