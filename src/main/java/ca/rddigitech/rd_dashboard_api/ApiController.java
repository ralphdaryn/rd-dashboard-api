package ca.rddigitech.rd_dashboard_api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

  private final Ga4Service ga4Service;

  public ApiController(Ga4Service ga4Service) {
    this.ga4Service = ga4Service;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
      "status", "ok",
      "version", System.getenv().getOrDefault("APP_VERSION", "unknown"),
      "hasPropertyId", System.getenv("GA4_PROPERTY_ID") != null,
      "hasServiceJson", System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON") != null,
      "serviceJsonLength", System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON") == null ? 0 : System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON").length()
    );
  }

  @GetMapping("/dashboard/ga4Results")
  public Object ga4Results() {
    try {
      return ga4Service.getLast30DaysResults();
    } catch (Exception e) {
      return Map.of(
        "error", "ga4Results failed",
        "type", e.getClass().getName(),
        "message", String.valueOf(e.getMessage())
      );
    }
  }
}