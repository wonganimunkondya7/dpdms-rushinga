package zw.ac.uz.dpdms.alert;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {
  private final AlertRepository logs;
  private final AlertDeliveryService delivery;
  private final String emailRecipient;
  private final String whatsappRecipient;

  public AlertController(AlertRepository logs, AlertDeliveryService delivery,
      @Value("${dpdms.alert.email-recipient}") String emailRecipient,
      @Value("${dpdms.alert.whatsapp-recipient}") String whatsappRecipient) {
    this.logs = logs; this.delivery = delivery;
    this.emailRecipient = emailRecipient; this.whatsappRecipient = whatsappRecipient;
  }

  @GetMapping public List<AlertLog> all(@AuthenticationPrincipal Jwt jwt) {
    String role = jwt.getClaimAsString("role");
    if ("NATIONAL".equals(role)) return logs.findAllByOrderByCreatedAtDesc();
    if (!"PROVINCIAL_ADMIN".equals(role)) {
      throw new AccessDeniedException("Only national users and provincial administrators may view alert logs");
    }
    String hazard = jwt.getClaimAsString("hazard");
    if (hazard == null || hazard.isBlank()) throw new AccessDeniedException("A provincial administrator must be scoped to a hazard");
    return logs.findByHazardOrderByCreatedAtDesc(hazard);
  }

  @PostMapping public AlertLog queue(@RequestBody AlertLog alert, @AuthenticationPrincipal Jwt jwt) {
    String role = jwt.getClaimAsString("role");
    String hazard = alert.hazard == null ? "" : alert.hazard.toUpperCase();
    if (!List.of("FLOOD", "DROUGHT", "FIRE", "ZOONOTIC", "MINING").contains(hazard)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown hazard");
    }
    boolean supervisorForHazard = "SUPERVISOR".equals(role)
        && hazard.equals(jwt.getClaimAsString("hazard"));
    boolean administratorForHazard = "PROVINCIAL_ADMIN".equals(role)
        && hazard.equals(jwt.getClaimAsString("hazard"));
    if (!supervisorForHazard && !administratorForHazard) {
      throw new AccessDeniedException("Only the relevant supervisor or a provincial administrator may queue alerts");
    }
    if (alert.message == null || alert.message.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
    if (!List.of("EMAIL", "WHATSAPP").contains(alert.channel == null ? "" : alert.channel.toUpperCase()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Channel must be EMAIL or WHATSAPP");
    alert.id = null;
    alert.hazard = hazard;
    alert.channel = alert.channel.toUpperCase();
    if (alert.recipient == null || alert.recipient.isBlank()) alert.recipient = "EMAIL".equals(alert.channel) ? emailRecipient : whatsappRecipient;
    alert.deliveryStatus = "QUEUED";
    alert.createdAt = Instant.now();
    AlertLog saved = logs.save(alert);
    delivery.deliver(saved.id);
    return saved;
  }
}
