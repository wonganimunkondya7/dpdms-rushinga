package zw.ac.uz.dpdms.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

class WhatsAppWebhookControllerTest {
  private AlertRepository alerts;
  private WhatsAppMessageReceiptRepository receipts;
  private WhatsAppWebhookController controller;
  private final ObjectMapper json = new ObjectMapper();
  private final String appSecret = "test-meta-app-secret";

  @BeforeEach void setUp() {
    alerts = Mockito.mock(AlertRepository.class);
    receipts = Mockito.mock(WhatsAppMessageReceiptRepository.class);
    controller = new WhatsAppWebhookController(alerts, receipts, json, appSecret, "local-verify-token");
  }

  @Test void verifiesMetaSubscriptionChallenge() {
    var response = controller.verify("subscribe", "local-verify-token", "challenge-value");
    assertEquals(200, response.getStatusCode().value());
    assertEquals("challenge-value", response.getBody());
  }

  @Test void authenticatesAndStoresDeliveredReceipt() throws Exception {
    String body = "{\"entry\":[{\"changes\":[{\"value\":{\"statuses\":[{\"id\":\"wamid.123\",\"status\":\"delivered\",\"timestamp\":\"1790431200\"}]}}]}]}";
    AlertLog alert = new AlertLog();
    alert.id = 42L;
    alert.deliveryStatus = "SENT";
    when(receipts.findById("wamid.123")).thenReturn(Optional.of(new WhatsAppMessageReceipt("wamid.123", 42L)));
    when(alerts.findById(42L)).thenReturn(Optional.of(alert));

    var response = controller.receive(body.getBytes(StandardCharsets.UTF_8), signature(body));

    assertEquals(200, response.getStatusCode().value());
    assertEquals("DELIVERED", alert.deliveryStatus);
    assertEquals(Instant.ofEpochSecond(1790431200L), alert.deliveredAt);
    verify(alerts).save(alert);
  }

  @Test void rejectsInvalidSignature() {
    assertThrows(ResponseStatusException.class,
        () -> controller.receive("{}".getBytes(StandardCharsets.UTF_8), "sha256=00"));
  }

  private String signature(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}
