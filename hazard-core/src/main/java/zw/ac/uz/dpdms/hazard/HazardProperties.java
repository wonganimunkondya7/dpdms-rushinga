package zw.ac.uz.dpdms.hazard;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("dpdms.hazard")
public record HazardProperties(String code, List<String> requiredIndicators, boolean allowAllHazardDemoScopes) {
  @ConstructorBinding
  public HazardProperties { }

  public HazardProperties(String code, List<String> requiredIndicators) {
    this(code, requiredIndicators, false);
  }
}
