package zw.ac.uz.dpdms.hazard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
  private final IncidentRepository incidents;
  private final AuditRepository audits;
  private final HazardAccess access;
  private final HazardProperties props;
  private final ObjectMapper json;
  private final IncidentAlertPublisher alerts;

  public IncidentController(IncidentRepository incidents, AuditRepository audits, HazardAccess access,
      HazardProperties props, ObjectMapper json, IncidentAlertPublisher alerts) {
    this.incidents = incidents;
    this.audits = audits;
    this.access = access;
    this.props = props;
    this.json = json;
    this.alerts = alerts;
  }

  @GetMapping
  public List<Incident> list(@AuthenticationPrincipal Jwt jwt) {
    access.read(jwt);
    if (access.national(jwt)) return incidents.findByStatus(IncidentStatus.APPROVED);
    if ("RECORDER".equals(jwt.getClaimAsString("role"))) {
      return incidents.findByReporterAndWard(jwt.getSubject(), jwt.getClaimAsString("ward"));
    }
    return incidents.findAll();
  }

  @GetMapping("/{id}")
  public Incident one(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
    access.read(jwt);
    Incident incident = get(id);
    requireVisible(incident, jwt);
    return incident;
  }

  @PostMapping
  public ResponseEntity<Incident> create(@Valid @RequestBody IncidentRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    access.recorder(jwt, request.ward());
    validate(request);
    Incident incident = apply(new Incident(), request, jwt.getSubject());
    incident.status = IncidentStatus.PENDING;
    incidents.save(incident);
    audit(incident, jwt, "CREATED", "Submitted for approval");
    return ResponseEntity.status(HttpStatus.CREATED).body(incident);
  }

  @PutMapping("/{id}")
  public Incident update(@PathVariable("id") Long id, @Valid @RequestBody IncidentRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    access.recorder(jwt, request.ward());
    Incident incident = get(id);
    if (!incident.reporter.equals(jwt.getSubject())
        || (incident.status != IncidentStatus.PENDING && incident.status != IncidentStatus.CORRECTIONS_REQUESTED)) {
      throw new AccessDeniedException("403: only your pending or correction-requested incident can be edited");
    }
    validate(request);
    boolean resubmission = incident.status == IncidentStatus.CORRECTIONS_REQUESTED;
    apply(incident, request, jwt.getSubject());
    if (resubmission) incident.status = IncidentStatus.PENDING;
    incidents.save(incident);
    audit(incident, jwt, resubmission ? "RESUBMITTED" : "UPDATED",
        resubmission ? "Corrections submitted for approval" : "Recorder updated pending incident");
    return incident;
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
    Incident incident = get(id);
    access.recorder(jwt, incident.ward);
    if (!incident.reporter.equals(jwt.getSubject())
        || (incident.status != IncidentStatus.PENDING && incident.status != IncidentStatus.CORRECTIONS_REQUESTED)) {
      throw new AccessDeniedException("403: only your unapproved incident can be deleted");
    }
    audit(incident, jwt, "DELETED", "Recorder deleted unapproved incident");
    incidents.delete(incident);
  }

  @PostMapping("/{id}/transition")
  public Incident transition(@PathVariable("id") Long id, @Valid @RequestBody TransitionRequest request,
      @AuthenticationPrincipal Jwt jwt, @RequestHeader("Authorization") String authorization) {
    access.supervisor(jwt);
    Incident incident = get(id);
    if (incident.status != IncidentStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending incidents can be reviewed");
    }
    if (request.status() == IncidentStatus.PENDING) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot transition to PENDING");
    }
    if (request.status() == IncidentStatus.REJECTED && (request.reason() == null || request.reason().isBlank())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
    }
    incident.status = request.status();
    incidents.save(incident);
    audit(incident, jwt, request.status().name(), request.reason());
    if (incident.status == IncidentStatus.APPROVED) alerts.publishIfCritical(incident, authorization);
    return incident;
  }

  @GetMapping("/{id}/audit")
  public List<AuditEntry> audit(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
    access.read(jwt);
    requireVisible(get(id), jwt);
    return audits.findByIncidentIdOrderByActedAtAsc(id);
  }

  private void requireVisible(Incident incident, Jwt jwt) {
    String role = jwt.getClaimAsString("role");
    if (access.national(jwt) && incident.status != IncidentStatus.APPROVED) {
      throw new AccessDeniedException("403: national users may only view approved incidents");
    }
    if ("RECORDER".equals(role)
        && (!incident.reporter.equals(jwt.getSubject())
            || !incident.ward.equals(jwt.getClaimAsString("ward")))) {
      throw new AccessDeniedException("403: recorders may only view their own ward's incidents");
    }
  }

  private Incident get(Long id) {
    return incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private Incident apply(Incident incident, IncidentRequest request, String authenticatedReporter) {
    incident.ward = request.ward();
    incident.district = request.district();
    incident.province = request.province();
    incident.occurredAt = request.occurredAt();
    incident.reporter = authenticatedReporter;
    incident.severity = request.severity();
    incident.latitude = request.latitude();
    incident.longitude = request.longitude();
    try {
      incident.indicatorsJson = json.writeValueAsString(request.indicators());
    } catch (JsonProcessingException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid indicators");
    }
    return incident;
  }

  private void validate(IncidentRequest request) {
    if (request.indicators() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicators are required");
    }
    for (String key : props.requiredIndicators()) {
      Object value = request.indicators().get(key);
      if (value == null || (value instanceof String text && text.isBlank())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required indicator: " + key);
      }
    }
    for (String key : List.of("peakWaterLevelMetres", "householdsDisplaced", "areaFloodedHectares",
        "inundationDurationDays", "rainfallDeficitMm", "consecutiveDryDays", "cropFailurePercentage",
        "peopleWaterShortage", "livestockMortalityCount", "areaBurnedHectares", "casualties",
        "structuresDestroyed", "confirmedHumanCases", "confirmedAnimalCases", "trappedOrInjuredMiners", "fatalities")) {
      if (request.indicators().containsKey(key)) {
        double number = numericValue(key, request.indicators().get(key));
        boolean count = List.of("householdsDisplaced", "inundationDurationDays", "consecutiveDryDays",
            "peopleWaterShortage", "livestockMortalityCount", "casualties", "structuresDestroyed",
            "confirmedHumanCases", "confirmedAnimalCases", "trappedOrInjuredMiners", "fatalities").contains(key);
        if (!Double.isFinite(number) || number < 0 || (count && number != Math.rint(number))
            || ("cropFailurePercentage".equals(key) && number > 100)) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric indicator: " + key);
        }
      }
    }
    validateChoice(request, "suspectedCause", "natural", "accidental", "deliberate");
    validateChoice(request, "fireStatus", "active", "burning", "contained");
    validateChoice(request, "classification", "cluster", "outbreak");
    validateChoice(request, "accidentType", "collapse", "gas explosion", "flooding", "fall of ground");
    if (request.indicators().containsKey("mineNameAndType")) {
      String mine = String.valueOf(request.indicators().get("mineNameAndType")).toLowerCase();
      if (!mine.contains("formal") && !mine.contains("artisanal")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mineNameAndType must include formal or artisanal");
      }
    }
    if (request.indicators().containsKey("rescueOperationsOngoing")) {
      Object value = request.indicators().get("rescueOperationsOngoing");
      if (!(value instanceof Boolean) && !List.of("true", "false").contains(String.valueOf(value).toLowerCase())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rescueOperationsOngoing must be true or false");
      }
    }
  }

  private double numericValue(String key, Object value) {
    try { return Double.parseDouble(String.valueOf(value)); }
    catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicator must be numeric: " + key); }
  }

  private void validateChoice(IncidentRequest request, String key, String... options) {
    if (!request.indicators().containsKey(key)) return;
    String value = String.valueOf(request.indicators().get(key)).trim().toLowerCase();
    if (!List.of(options).contains(value)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid value for indicator: " + key);
    }
  }

  private void audit(Incident incident, Jwt jwt, String action, String detail) {
    AuditEntry entry = new AuditEntry();
    entry.incidentId = incident.id;
    entry.actor = jwt.getSubject();
    entry.action = action;
    entry.detail = detail;
    audits.save(entry);
  }
}
