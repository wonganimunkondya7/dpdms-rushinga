package zw.ac.uz.dpdms.hazard;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HazardProperties.class)
@EnableAsync
public class HazardCoreConfiguration {
  @Bean @LoadBalanced RestClient.Builder loadBalancedRestClientBuilder() { return RestClient.builder(); }
}
