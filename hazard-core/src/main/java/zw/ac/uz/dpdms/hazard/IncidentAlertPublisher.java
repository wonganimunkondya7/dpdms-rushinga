package zw.ac.uz.dpdms.hazard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IncidentAlertPublisher {
  private final RestClient client;
  private final ObjectMapper json;
  private final HazardProperties hazard;
  private final double floodDangerLevel;

  public IncidentAlertPublisher(@org.springframework.cloud.client.loadbalancer.LoadBalanced RestClient.Builder builder,
      ObjectMapper json, HazardProperties hazard,
      @Value("${DPDMS_FLOOD_DANGER_LEVEL_METRES:2.0}") double floodDangerLevel) {
    this.client = builder.build(); this.json = json; this.hazard = hazard; this.floodDangerLevel = floodDangerLevel;
  }

  @Async
  public void publishIfCritical(Incident incident, String authorization) {
    try {
      Map<String, Object> indicators = json.readValue(incident.indicatorsJson, new TypeReference<>() {});
      String reason = reason(indicators);
      if (reason == null) return;
      String message = hazard.code() + " incident " + incident.id + " in " + incident.ward + ": " + reason;
      for (String channel : new String[] {"EMAIL", "WHATSAPP"}) {
        client.post().uri("http://alert-service/api/alerts")
            .header("Authorization", authorization)
            .body(Map.of("hazard", hazard.code(), "channel", channel, "message", message))
            .retrieve().toBodilessEntity();
      }
    } catch (Exception ignored) {
      // Alerting is best-effort and must not affect incident approval.
    }
  }

  private String reason(Map<String, Object> indicators) {
    return switch (hazard.code()) {
      case "FLOOD" -> number(indicators.get("peakWaterLevelMetres")) >= floodDangerLevel
          ? "peak water level reached the configured danger threshold" : null;
      case "FIRE" -> contains(indicators.get("fireStatus"), "active", "burning") ? "fire remains active" : null;
      case "ZOONOTIC" -> contains(indicators.get("classification"), "cluster", "outbreak") ? "event classified as a cluster or outbreak" : null;
      case "MINING" -> number(indicators.get("fatalities")) > 0 || number(indicators.get("trappedOrInjuredMiners")) > 0
          ? "miners are reported trapped, injured, or deceased" : null;
      default -> null;
    };
  }

  private boolean contains(Object value, String... expected) {
    String text = String.valueOf(value).toLowerCase();
    for (String item : expected) if (text.contains(item)) return true;
    return false;
  }

  private double number(Object value) {
    try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
  }
}
