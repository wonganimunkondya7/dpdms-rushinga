package zw.ac.uz.dpdms.dashboard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
  private final String apiBaseUrl;

  public DashboardController(@Value("${dpdms.api-base-url:http://localhost:8080}") String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute("apiBaseUrl", apiBaseUrl);
    return "dashboard";
  }
}
