package zw.ac.uz.dpdms.hazard;
import jakarta.validation.constraints.NotNull;
public record TransitionRequest(@NotNull IncidentStatus status, String reason) {}
