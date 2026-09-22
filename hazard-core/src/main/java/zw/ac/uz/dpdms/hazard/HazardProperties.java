package zw.ac.uz.dpdms.hazard;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dpdms.hazard")
public record HazardProperties(String code, List<String> requiredIndicators) { }
