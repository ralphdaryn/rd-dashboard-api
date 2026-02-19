package ca.rddigitech.rd_dashboard_api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    String serviceJson = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON");
    return Map.of(
      "status", "ok",
      "version", System.getenv().getOrDefault("APP_VERSION", "unknown"),
      "hasServiceJson", serviceJson != null && !serviceJson.isBlank(),
      "serviceJsonLength", serviceJson == null ? 0 : serviceJson.length(),
      "hasStepByStepPropertyId", System.getenv("GA4_PROPERTY_ID_STEPBYSTEP") != null,
      "hasKsnapStudioPropertyId", System.getenv("GA4_PROPERTY_ID_KSNAPSTUDIO") != null,
      "hasRdDigitechPropertyId", System.getenv("GA4_PROPERTY_ID_RDDIGITECH") != null
    );
  }

  @GetMapping("/dashboard/stepbystep/ga4Results")
  public Object ga4ResultsStepByStep(@RequestParam(defaultValue = "30") int days) {
    try {
      return ga4Service.getResults("stepbystep", days);
    } catch (Exception e) {
      return Map.of(
        "error", "ga4Results failed",
        "client", "stepbystep",
        "type", e.getClass().getName(),
        "message", String.valueOf(e.getMessage())
      );
    }
  }

  @GetMapping("/dashboard/ksnapstudio/ga4Results")
  public Object ga4ResultsKsnapStudio(@RequestParam(defaultValue = "30") int days) {
    try {
      return ga4Service.getResults("ksnapstudio", days);
    } catch (Exception e) {
      return Map.of(
        "error", "ga4Results failed",
        "client", "ksnapstudio",
        "type", e.getClass().getName(),
        "message", String.valueOf(e.getMessage())
      );
    }
  }

  // ✅ RD Digitech
  @GetMapping("/dashboard/rddigitech/ga4Results")
  public Object ga4ResultsRdDigitech(@RequestParam(defaultValue = "30") int days) {
    try {
      return ga4Service.getResults("rddigitech", days);
    } catch (Exception e) {
      return Map.of(
        "error", "ga4Results failed",
        "client", "rddigitech",
        "type", e.getClass().getName(),
        "message", String.valueOf(e.getMessage())
      );
    }
  }
}