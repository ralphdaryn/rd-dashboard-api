package ca.rddigitech.rd_dashboard_api;

import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:8888")
@RestController
@RequestMapping("/api")
public class ApiController {

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/dashboard/metrics")
  public Map<String, Object> metrics() {
    return Map.of(
      "visitors", 1240,
      "leads", 38,
      "conversionRate", 3.1
    );
  }
}