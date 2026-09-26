package zw.ac.uz.dpdms.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AlertDeliveryService {
  private static final Logger log = LoggerFactory.getLogger(AlertDeliveryService.class);
  private final AlertRepository logs;
  private final WhatsAppMessageReceiptRepository messageReceipts;
  private final SmtpEmailClient mail;
  private final RestClient http;
  private final ObjectMapper json;
  private final String sender;
  private final String smtpHost;
  private final int smtpPort;
  private final String smtpUsername;
  private final String smtpPassword;
  private final boolean smtpAuth;
  private final boolean smtpStartTls;
  private final String whatsappToken;
  private final String whatsappPhoneId;
  private final String graphVersion;

  public AlertDeliveryService(AlertRepository logs, WhatsAppMessageReceiptRepository messageReceipts,
      SmtpEmailClient mail, RestClient.Builder builder,
      ObjectMapper json, @Value("${spring.mail.username:}") String sender,
      @Value("${spring.mail.host:localhost}") String smtpHost, @Value("${spring.mail.port:25}") int smtpPort,
      @Value("${spring.mail.username:}") String smtpUsername, @Value("${spring.mail.password:}") String smtpPassword,
      @Value("${spring.mail.properties.mail.smtp.auth:false}") boolean smtpAuth,
      @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}") boolean smtpStartTls,
      @Value("${dpdms.alert.whatsapp-token:}") String whatsappToken,
      @Value("${dpdms.alert.whatsapp-phone-number-id:}") String whatsappPhoneId,
      @Value("${dpdms.alert.whatsapp-graph-version:v23.0}") String graphVersion) {
    this.logs = logs; this.messageReceipts = messageReceipts; this.mail = mail; this.http = builder.build(); this.json = json; this.sender = sender;
    this.smtpHost = smtpHost; this.smtpPort = smtpPort; this.smtpUsername = smtpUsername; this.smtpPassword = smtpPassword;
    this.smtpAuth = smtpAuth; this.smtpStartTls = smtpStartTls;
    this.whatsappToken = whatsappToken; this.whatsappPhoneId = whatsappPhoneId; this.graphVersion = graphVersion;
  }

  @Async
  public void deliver(Long id) {
    logs.findById(id).ifPresent(alert -> {
      String outcome = "SENT";
      try {
        if ("EMAIL".equals(alert.channel)) sendEmail(alert);
        else sendWhatsApp(alert);
      } catch (Exception e) {
        if (e instanceof RestClientResponseException responseError) {
          log.warn("Alert {} delivery failed: provider returned HTTP {}: {}", id,
              responseError.getStatusCode().value(), responseError.getResponseBodyAsString());
        } else {
          log.warn("Alert {} delivery failed: {}: {}", id, e.getClass().getSimpleName(), e.getMessage());
        }
        outcome = "DELIVERY_FAILED";
      }
      AlertLog latest = logs.findById(id).orElse(alert);
      if (!"DELIVERED".equals(latest.deliveryStatus) && !"READ".equals(latest.deliveryStatus)) {
        latest.deliveryStatus = outcome;
      }
      logs.save(latest);
    });
  }

  private void sendEmail(AlertLog alert) throws Exception {
    if (sender.isBlank()) throw new IllegalStateException("SMTP sender is not configured");
    mail.send(smtpHost, smtpPort, smtpUsername, smtpPassword, smtpAuth, smtpStartTls,
        sender, alert.recipient, "DPDMS " + alert.hazard + " alert", alert.message);
  }

  private void sendWhatsApp(AlertLog alert) throws Exception {
    if (whatsappToken.isBlank() || whatsappPhoneId.isBlank()) throw new IllegalStateException("WhatsApp Cloud API is not configured");
    String body = json.writeValueAsString(Map.of("messaging_product", "whatsapp", "to", alert.recipient,
        "type", "text", "text", Map.of("body", alert.message)));
    String response = http.post().uri("https://graph.facebook.com/" + graphVersion + "/" + whatsappPhoneId + "/messages")
        .headers(h -> h.setBearerAuth(whatsappToken)).header("Content-Type", "application/json").body(body)
        .retrieve().body(String.class);
    String providerMessageId = json.readTree(response).path("messages").path(0).path("id").asText();
    if (!providerMessageId.isBlank()) messageReceipts.save(new WhatsAppMessageReceipt(providerMessageId, alert.id));
  }
}
