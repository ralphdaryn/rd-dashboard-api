package ca.rddigitech.rd_dashboard_api;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@CrossOrigin(origins = {
    "http://localhost:8888",
    "http://localhost:5173",
    "http://localhost:5174",
    "https://stepbystepclub.ca",
    "https://www.stepbystepclub.ca"
})
@RestController
@RequestMapping("/api")
public class ApiController {

  private final Ga4Service ga4Service;

  // ✅ Spring will inject this automatically
  public ApiController(Ga4Service ga4Service) {
    this.ga4Service = ga4Service;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of(
        "status", "ok",
        "version", "DEPLOY_TEST_2026_02_09");
  }

  // ✅ This is what your React Dashboard fetches
  @GetMapping("/dashboard/ga4Results")
  public ResponseEntity<Map<String, Object>> ga4Results() {
    return ResponseEntity
        .ok()
        .header("X-API-VERSION", "ga4Results-controller-hit-v1")
        .body(ga4Service.getLast30DaysResults());
  }
}