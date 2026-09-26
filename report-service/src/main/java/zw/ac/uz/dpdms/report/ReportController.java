package zw.ac.uz.dpdms.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private static final List<String> HAZARDS = List.of("flood", "drought", "fire", "zoonotic", "mining");
  private static final List<String> COLUMNS = List.of("hazard", "id", "ward", "district", "province", "occurredAt", "severity", "status", "latitude", "longitude", "reporter", "indicators");
  private final ObjectMapper json;
  private final ReportDataProvider dataProvider;

  public ReportController(ObjectMapper json, ReportDataProvider dataProvider) { this.json = json; this.dataProvider = dataProvider; }

  @GetMapping("/{format}")
  public ResponseEntity<byte[]> get(@PathVariable("format") String format, @RequestHeader("Authorization") String auth,
      @RequestParam(value = "hazard", required = false) String hazard, @RequestParam(value = "ward", required = false) String ward,
      @RequestParam(value = "district", required = false) String district, @RequestParam(value = "severity", required = false) String severity,
      @RequestParam(value = "fromDate", required = false) LocalDate fromDate, @RequestParam(value = "toDate", required = false) LocalDate toDate,
      @RequestParam(value = "status", defaultValue = "APPROVED") String status) {
    if (!List.of("csv", "xlsx", "docx", "pdf").contains(format)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported report format");
    if (hazard != null && !HAZARDS.contains(hazard.toLowerCase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown hazard");
    if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromDate must not be after toDate");
    if (!List.of("PENDING", "APPROVED", "REJECTED", "CORRECTIONS_REQUESTED").contains(status.toUpperCase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown approval status");
    Instant from = fromDate == null ? null : fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant until = toDate == null ? null : toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var data = dataProvider.load(auth, hazard).stream().filter(x -> status.equalsIgnoreCase(String.valueOf(x.get("status"))))
        .filter(x -> matches(x, "ward", ward) && matches(x, "district", district) && matches(x, "severity", severity))
        .filter(x -> dateMatches(x, from, until)).toList();
    try {
      byte[] content = switch (format) { case "csv" -> csv(data).getBytes(StandardCharsets.UTF_8); case "xlsx" -> xlsx(data); case "docx" -> docx(data); case "pdf" -> pdf(data); default -> throw new IllegalStateException(); };
      String type = Map.of("csv", "text/csv", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "pdf", "application/pdf").get(format);
      return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dpdms-report." + format).contentType(MediaType.parseMediaType(type)).body(content);
    } catch (ResponseStatusException e) { throw e;
    } catch (Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report generation failed", e); }
  }

  private boolean matches(Map<String, Object> row, String key, String value) { return value == null || value.isBlank() || value.equalsIgnoreCase(String.valueOf(row.get(key))); }
  private boolean dateMatches(Map<String, Object> row, Instant from, Instant until) {
    if (from == null && until == null) return true;
    try { Instant occurred = Instant.parse(String.valueOf(row.get("occurredAt"))); return (from == null || !occurred.isBefore(from)) && (until == null || occurred.isBefore(until)); }
    catch (Exception e) { return false; }
  }
  private String csv(List<Map<String, Object>> data) { return String.join(",", COLUMNS) + "\n" + data.stream().map(row -> COLUMNS.stream().map(key -> '"' + String.valueOf(row.getOrDefault(key, "")).replace("\"", "\"\"") + '"').collect(Collectors.joining(","))).collect(Collectors.joining("\n")); }
  private byte[] xlsx(List<Map<String, Object>> data) throws Exception { try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) { var sheet = workbook.createSheet("Approved incidents"); for (int rowIndex = 0; rowIndex <= data.size(); rowIndex++) { var row = sheet.createRow(rowIndex); for (int column = 0; column < COLUMNS.size(); column++) row.createCell(column).setCellValue(rowIndex == 0 ? COLUMNS.get(column) : String.valueOf(data.get(rowIndex - 1).getOrDefault(COLUMNS.get(column), ""))); } workbook.write(output); return output.toByteArray(); } }
  private byte[] docx(List<Map<String, Object>> data) throws Exception { try (var document = new XWPFDocument(); var output = new ByteArrayOutputStream()) { document.createParagraph().createRun().setText("DPDMS Approved Incident Report"); var table = document.createTable(data.size() + 1, COLUMNS.size()); for (int row = 0; row <= data.size(); row++) for (int column = 0; column < COLUMNS.size(); column++) table.getRow(row).getCell(column).setText(row == 0 ? COLUMNS.get(column) : String.valueOf(data.get(row - 1).getOrDefault(COLUMNS.get(column), ""))); document.write(output); return output.toByteArray(); } }
  private byte[] pdf(List<Map<String, Object>> data) throws Exception { var output = new ByteArrayOutputStream(); var document = new Document(PageSize.A4.rotate()); PdfWriter.getInstance(document, output); document.open(); document.add(new Paragraph("DPDMS Incident Report")); for (var row : data) document.add(new Paragraph(row.get("hazard") + " #" + row.get("id") + " | " + row.get("ward") + ", " + row.get("district") + " | " + row.get("occurredAt") + " | " + row.get("severity") + " | " + row.get("status") + " | indicators: " + row.get("indicators"))); document.close(); return output.toByteArray(); }
}
