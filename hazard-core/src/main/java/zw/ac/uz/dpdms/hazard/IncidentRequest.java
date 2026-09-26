package zw.ac.uz.dpdms.hazard;
import jakarta.validation.constraints.*; import java.time.*; import java.util.*;
public record IncidentRequest(@NotBlank String ward,@NotBlank String district,@NotBlank String province,@NotNull Instant occurredAt,String reporter,@NotNull Severity severity,@DecimalMin("-90") @DecimalMax("90") double latitude,@DecimalMin("-180") @DecimalMax("180") double longitude,@NotNull Map<String,Object> indicators) {}
