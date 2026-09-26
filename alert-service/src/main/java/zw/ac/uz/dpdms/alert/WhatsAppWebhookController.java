package zw.ac.uz.dpdms.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WhatsAppWebhookController {
  private final AlertRepository alerts;
  private final WhatsAppMessageReceiptRepository messageReceipts;
  private final ObjectMapper json;
  private final String appSecret;
  private final String verifyToken;

  public WhatsAppWebhookController(AlertRepository alerts, WhatsAppMessageReceiptRepository messageReceipts,
      ObjectMapper json, @Value("${dpdms.alert.whatsapp-app-secret:}") String appSecret,
      @Value("${dpdms.alert.whatsapp-webhook-verify-token:}") String verifyToken) {
    this.alerts = alerts;
    this.messageReceipts = messageReceipts;
    this.json = json;
    this.appSecret = appSecret;
    this.verifyToken = verifyToken;
  }

  @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
      @RequestParam(name = "hub.verify_token", required = false) String suppliedToken,
      @RequestParam(name = "hub.challenge", required = false) String challenge) {
    if (!"subscribe".equals(mode) || verifyToken.isBlank() || challenge == null
        || !constantTimeEquals(verifyToken, suppliedToken)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
    }
    return ResponseEntity.ok(challenge);
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
      @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
    if (appSecret.isBlank() || !validSignature(rawBody, signature)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid WhatsApp webhook signature");
    }
    try {
      JsonNode statuses = json.readTree(rawBody).path("entry");
      for (JsonNode entry : statuses) {
        for (JsonNode change : entry.path("changes")) {
          for (JsonNode status : change.path("value").path("statuses")) {
            updateStatus(status);
          }
        }
      }
      return ResponseEntity.ok().build();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed WhatsApp webhook payload", e);
    }
  }

  private void updateStatus(JsonNode status) {
    String messageId = status.path("id").asText();
    String providerStatus = status.path("status").asText();
    if (messageId.isBlank() || providerStatus.isBlank()) return;
    messageReceipts.findById(messageId).flatMap(receipt -> alerts.findById(receipt.alertId)).ifPresent(alert -> {
      switch (providerStatus.toLowerCase()) {
        case "sent" -> { if ("QUEUED".equals(alert.deliveryStatus)) alert.deliveryStatus = "SENT"; }
        case "delivered" -> {
          if (!"READ".equals(alert.deliveryStatus)) alert.deliveryStatus = "DELIVERED";
          if (alert.deliveredAt == null) alert.deliveredAt = providerTimestamp(status);
        }
        case "read" -> {
          alert.deliveryStatus = "READ";
          if (alert.deliveredAt == null) alert.deliveredAt = providerTimestamp(status);
        }
        case "failed" -> alert.deliveryStatus = "DELIVERY_FAILED";
        default -> { return; }
      }
      alerts.save(alert);
    });
  }

  private Instant providerTimestamp(JsonNode status) {
    try { return Instant.ofEpochSecond(Long.parseLong(status.path("timestamp").asText())); }
    catch (RuntimeException ignored) { return Instant.now(); }
  }

  private boolean validSignature(byte[] body, String signature) {
    if (signature == null || !signature.startsWith("sha256=")) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expected = mac.doFinal(body);
      byte[] supplied = HexFormat.of().parseHex(signature.substring("sha256=".length()));
      return MessageDigest.isEqual(expected, supplied);
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean constantTimeEquals(String expected, String actual) {
    return actual != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
  }
}
