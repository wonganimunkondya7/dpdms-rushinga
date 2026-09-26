package zw.ac.uz.dpdms.flood;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import zw.ac.uz.dpdms.hazard.AuditRepository;
import zw.ac.uz.dpdms.hazard.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = FloodServiceApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:dpdms_flood_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.sql.init.mode=always",
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "DPDMS_JWT_SECRET=integration-test-signing-secret-32-bytes"
})
@AutoConfigureMockMvc
class IncidentApiIntegrationTest {
  private static final String SECRET = "integration-test-signing-secret-32-bytes";

  @Autowired MockMvc api;
  @Autowired ObjectMapper json;
  @Autowired IncidentRepository incidents;
  @Autowired AuditRepository audits;

  @BeforeEach void clearDatabase() {
    audits.deleteAll();
    incidents.deleteAll();
  }

  @Test void recorderCanReadOwnWardButNotAnotherWardAndCannotSpoofReporter() throws Exception {
    long id = createPendingIncident("Rushinga Ward 1");
    approve(id);
    api.perform(get("/api/incidents/" + id).header("Authorization", token("RECORDER", "FLOOD", "Rushinga Ward 1", "recorder.one")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.reporter").value("recorder.one"));
    api.perform(get("/api/incidents/" + id).header("Authorization", token("RECORDER", "FLOOD", "Rushinga Ward 2", "recorder.two")))
        .andExpect(status().isForbidden());
    api.perform(get("/api/incidents/" + id).header("Authorization", token("RECORDER", "DROUGHT", "Rushinga Ward 1", "recorder.one")))
        .andExpect(status().isForbidden());
  }

  @Test void correctionsAreResubmittedAndRejectionRequiresAReason() throws Exception {
    long id = createPendingIncident("Rushinga Ward 1");
    String supervisor = token("SUPERVISOR", "FLOOD", "", "flood.supervisor");
    api.perform(post("/api/incidents/{id}/transition", id).header("Authorization", supervisor)
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CORRECTIONS_REQUESTED\",\"reason\":\"Check level\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CORRECTIONS_REQUESTED"));

    api.perform(put("/api/incidents/{id}", id).header("Authorization", token("RECORDER", "FLOOD", "Rushinga Ward 1", "recorder.one"))
        .contentType(MediaType.APPLICATION_JSON).content(request("Rushinga Ward 1")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

    api.perform(post("/api/incidents/{id}/transition", id).header("Authorization", supervisor)
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"reason\":\" \"}"))
        .andExpect(status().isBadRequest());
    api.perform(post("/api/incidents/{id}/transition", id).header("Authorization", supervisor)
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"reason\":\"Unsafe location\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    api.perform(get("/api/incidents/{id}/audit", id).header("Authorization", supervisor))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.action == 'RESUBMITTED')]").exists());
  }

  @Test void nationalUsersSeeOnlyApprovedRecordsAndCannotCreate() throws Exception {
    long id = createPendingIncident("Rushinga Ward 1");
    String national = token("NATIONAL", "", "", "national@dpdms.local");
    api.perform(get("/api/incidents/" + id).header("Authorization", national)).andExpect(status().isForbidden());
    api.perform(post("/api/incidents").header("Authorization", national)
        .contentType(MediaType.APPLICATION_JSON).content(request("Rushinga Ward 1")))
        .andExpect(status().isForbidden());
    approve(id);
    api.perform(get("/api/incidents/" + id).header("Authorization", national)).andExpect(status().isOk());
    api.perform(get("/api/incidents").header("Authorization", national))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("APPROVED"));
    api.perform(post("/api/incidents/{id}/transition", id).header("Authorization", national)
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\",\"reason\":\"Not allowed\"}"))
        .andExpect(status().isForbidden());
  }

  @Test void recorderCanDeleteOwnUnapprovedIncidentAndDeletionIsAudited() throws Exception {
    long id = createPendingIncident("Rushinga Ward 1");
    String recorder = token("RECORDER", "FLOOD", "Rushinga Ward 1", "recorder.one");
    api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/incidents/{id}", id)
        .header("Authorization", recorder)).andExpect(status().isNoContent());
    org.junit.jupiter.api.Assertions.assertEquals("DELETED", audits.findByIncidentIdOrderByActedAtAsc(id).get(1).action);
    api.perform(get("/api/incidents/{id}", id).header("Authorization", recorder)).andExpect(status().isNotFound());
  }

  private long createPendingIncident(String ward) throws Exception {
    MvcResult result = api.perform(post("/api/incidents")
        .header("Authorization", token("RECORDER", "FLOOD", ward, "recorder.one"))
        .contentType(MediaType.APPLICATION_JSON).content(request(ward)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  private void approve(long id) throws Exception {
    api.perform(post("/api/incidents/{id}/transition", id)
        .header("Authorization", token("SUPERVISOR", "FLOOD", "", "flood.supervisor"))
        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\",\"reason\":\"Verified\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
  }

  private String request(String ward) throws Exception {
    return json.writeValueAsString(Map.of(
        "ward", ward, "district", "Rushinga", "province", "Mashonaland Central",
        "occurredAt", "2026-09-25T10:00:00Z", "reporter", "spoofed-user", "severity", "HIGH",
        "latitude", -16.7, "longitude", 32.3,
        "indicators", Map.of("peakWaterLevelMetres", 1.4, "riverBasin", "Mazowe",
            "householdsDisplaced", 3, "areaFloodedHectares", 2.5, "inundationDurationDays", 1)));
  }

  private String token(String role, String hazard, String ward, String subject) throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject)
        .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(600)))
        .claim("role", role).claim("hazard", hazard).claim("ward", ward).build();
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
    jwt.sign(new MACSigner(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    return "Bearer " + jwt.serialize();
  }
}
