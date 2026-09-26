package zw.ac.uz.dpdms.report;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;

class ReportApiTest {
  private ReportDataProvider data;
  private MockMvc api;

  @BeforeEach void setUp() {
    data = mock(ReportDataProvider.class);
    api = MockMvcBuilders.standaloneSetup(new ReportController(new ObjectMapper(), data)).build();
  }

  @Test void csvHonorsStatusAndInclusiveDateRange() throws Exception {
    when(data.load("Bearer test", "flood")).thenReturn(List.of(
        row(1, "2026-09-10T10:00:00Z", "HIGH", "APPROVED"),
        row(2, "2026-09-11T10:00:00Z", "LOW", "PENDING"),
        row(3, "2026-09-01T10:00:00Z", "HIGH", "APPROVED")));

    api.perform(get("/api/reports/csv").header("Authorization", "Bearer test")
        .param("hazard", "flood").param("fromDate", "2026-09-10").param("toDate", "2026-09-10")
        .param("status", "APPROVED"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"FLOOD\",\"1\"")))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"FLOOD\",\"2\""))))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"FLOOD\",\"3\""))));
  }

  @Test void rejectsInvertedDateRange() throws Exception {
    api.perform(get("/api/reports/csv").header("Authorization", "Bearer test")
        .param("fromDate", "2026-09-12").param("toDate", "2026-09-10"))
        .andExpect(status().isBadRequest());
  }

  private Map<String, Object> row(int id, String occurredAt, String severity, String status) {
    return Map.ofEntries(Map.entry("hazard", "FLOOD"), Map.entry("id", id), Map.entry("ward", "Ward 1"),
        Map.entry("district", "Rushinga"), Map.entry("province", "Mashonaland Central"),
        Map.entry("occurredAt", occurredAt), Map.entry("severity", severity), Map.entry("status", status),
        Map.entry("latitude", -16.7), Map.entry("longitude", 32.3), Map.entry("reporter", "recorder"));
  }
}
