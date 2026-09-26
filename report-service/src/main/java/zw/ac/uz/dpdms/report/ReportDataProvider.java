package zw.ac.uz.dpdms.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ReportDataProvider {
  private static final List<String> HAZARDS = List.of("flood", "drought", "fire", "zoonotic", "mining");
  private final ObjectMapper json;
  private final RestClient client;

  public ReportDataProvider(ObjectMapper json, @LoadBalanced RestClient.Builder builder) {
    this.json = json;
    this.client = builder.build();
  }

  public List<Map<String, Object>> load(String auth, String requestedHazard) {
    List<String> hazards = requestedHazard == null ? HAZARDS : List.of(requestedHazard.toLowerCase());
    return hazards.stream().flatMap(hazard -> {
      try {
        String body = client.get().uri("http://" + hazard + "-service/api/incidents")
            .header("Authorization", auth).retrieve().body(String.class);
        return json.readValue(body, new TypeReference<List<Map<String, Object>>>() {}).stream()
            .map(row -> {
              row.put("hazard", hazard.toUpperCase());
              Object indicators = row.remove("indicatorsJson");
              if (indicators != null) row.put("indicators", indicators);
              return row;
            });
      } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve " + hazard + " incidents", e);
      }
    }).toList();
  }
}
