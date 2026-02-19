package ca.rddigitech.rd_dashboard_api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;
import com.google.analytics.data.v1beta.DateRange;
import com.google.analytics.data.v1beta.Dimension;
import com.google.analytics.data.v1beta.Filter;
import com.google.analytics.data.v1beta.FilterExpression;
import com.google.analytics.data.v1beta.Metric;
import com.google.analytics.data.v1beta.OrderBy;
import com.google.analytics.data.v1beta.RunReportRequest;
import com.google.analytics.data.v1beta.RunReportResponse;
import com.google.analytics.data.v1beta.Row;

@Service
public class Ga4Service {

  private final BetaAnalyticsDataClient client;

  public Ga4Service(BetaAnalyticsDataClient client) {
    this.client = client;
  }

  private static String normalizeClientKey(String v) {
    return Optional.ofNullable(v).orElse("")
        .trim()
        .toLowerCase(Locale.ROOT);
  }

  private String property(String clientKey) {
    String key = normalizeClientKey(clientKey);

    // ✅ Map clientKey -> env var name
    String envName = switch (key) {
      case "stepbystep", "stepxstep", "stepbystepclub" -> "GA4_PROPERTY_ID_STEPBYSTEP";
      case "ksnap", "ksnapstudio", "k-snap" -> "GA4_PROPERTY_ID_KSNAPSTUDIO";
      case "rddigitech", "rd", "rd-digitech", "rddigitaltech" -> "GA4_PROPERTY_ID_RDDIGITECH";
      default -> null;
    };

    if (envName == null) {
      throw new IllegalArgumentException("Unknown client key: " + clientKey);
    }

    String id = System.getenv(envName);
    if (id == null || id.isBlank()) {
      throw new IllegalStateException(envName + " env var is missing.");
    }

    return "properties/" + id.trim();
  }

  // ✅ supports days selector in UI
  public Map<String, Object> getResults(String clientKey, int days) {
    String prop = property(clientKey);
    int safeDays = Math.max(1, Math.min(days, 365));
    String start = safeDays + "daysAgo";

    Kpis kpis = fetchKpis(prop, start);

    List<Map<String, Object>> topSources = fetchTopSources(prop, start, 8);
    String topTrafficSource = topSources.isEmpty()
        ? "(not set)"
        : String.valueOf(topSources.get(0).get("source"));

    List<Map<String, Object>> topPages = fetchTopPages(prop, start, 12);

    int contactSubmits = fetchEventCount(prop, start, "contact_submit");
    int bookingClicks = fetchEventCount(prop, start, "booking_click");

    Map<String, Object> payload = new HashMap<>();
    payload.put("rangeLabel", "Last " + safeDays + " days");

    // Keep same keys your UI expects
    payload.put("users30d", kpis.users);
    payload.put("newUsers30d", kpis.newUsers);
    payload.put("avgEngagementTime", kpis.avgSessionDurationPretty);

    payload.put("contactSubmits", contactSubmits);
    payload.put("bookingClicks", bookingClicks);

    payload.put("topTrafficSource", topTrafficSource);
    payload.put("topSources", topSources);
    payload.put("topPages", topPages);

    payload.put("client", normalizeClientKey(clientKey));
    return payload;
  }

  private Kpis fetchKpis(String prop, String startDate) {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(prop)
        .addDateRanges(DateRange.newBuilder().setStartDate(startDate).setEndDate("today"))
        .addMetrics(Metric.newBuilder().setName("activeUsers"))
        .addMetrics(Metric.newBuilder().setName("newUsers"))
        .addMetrics(Metric.newBuilder().setName("averageSessionDuration"))
        .build();

    RunReportResponse response = client.runReport(request);

    int users = 0;
    int newUsers = 0;
    double avgSessionDurationSeconds = 0;

    if (response.getRowsCount() > 0) {
      Row row = response.getRows(0);
      users = parseIntSafe(row.getMetricValues(0).getValue());
      newUsers = parseIntSafe(row.getMetricValues(1).getValue());
      avgSessionDurationSeconds = parseDoubleSafe(row.getMetricValues(2).getValue());
    }

    return new Kpis(users, newUsers, secondsToPretty(avgSessionDurationSeconds));
  }

  private List<Map<String, Object>> fetchTopSources(String prop, String startDate, int limit) {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(prop)
        .addDateRanges(DateRange.newBuilder().setStartDate(startDate).setEndDate("today"))
        .addDimensions(Dimension.newBuilder().setName("sessionSourceMedium"))
        .addMetrics(Metric.newBuilder().setName("sessions"))
        .addOrderBys(OrderBy.newBuilder()
            .setMetric(OrderBy.MetricOrderBy.newBuilder().setMetricName("sessions"))
            .setDesc(true))
        .setLimit(limit)
        .build();

    RunReportResponse response = client.runReport(request);

    List<Map<String, Object>> out = new ArrayList<>();
    for (Row r : response.getRowsList()) {
      String source = r.getDimensionValues(0).getValue();
      int sessions = parseIntSafe(r.getMetricValues(0).getValue());
      out.add(Map.of("source", source, "sessions", sessions));
    }

    out.sort(Comparator.comparingInt(m -> -((Number) m.get("sessions")).intValue()));
    return out;
  }

  private List<Map<String, Object>> fetchTopPages(String prop, String startDate, int limit) {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(prop)
        .addDateRanges(DateRange.newBuilder().setStartDate(startDate).setEndDate("today"))
        .addDimensions(Dimension.newBuilder().setName("pagePath"))
        .addMetrics(Metric.newBuilder().setName("screenPageViews"))
        .addOrderBys(OrderBy.newBuilder()
            .setMetric(OrderBy.MetricOrderBy.newBuilder().setMetricName("screenPageViews"))
            .setDesc(true))
        .setLimit(limit)
        .build();

    RunReportResponse response = client.runReport(request);

    List<Map<String, Object>> out = new ArrayList<>();
    for (Row r : response.getRowsList()) {
      String path = r.getDimensionValues(0).getValue();
      int views = parseIntSafe(r.getMetricValues(0).getValue());
      out.add(Map.of("path", path, "views", views));
    }
    return out;
  }

  private int fetchEventCount(String prop, String startDate, String eventName) {
    Filter filter = Filter.newBuilder()
        .setFieldName("eventName")
        .setStringFilter(Filter.StringFilter.newBuilder()
            .setMatchType(Filter.StringFilter.MatchType.EXACT)
            .setValue(eventName))
        .build();

    FilterExpression exp = FilterExpression.newBuilder().setFilter(filter).build();

    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(prop)
        .addDateRanges(DateRange.newBuilder().setStartDate(startDate).setEndDate("today"))
        .addMetrics(Metric.newBuilder().setName("eventCount"))
        .setDimensionFilter(exp)
        .build();

    RunReportResponse response = client.runReport(request);
    if (response.getRowsCount() == 0) return 0;
    return parseIntSafe(response.getRows(0).getMetricValues(0).getValue());
  }

  private static int parseIntSafe(String v) {
    try {
      return (int) Math.round(Double.parseDouble(Optional.ofNullable(v).orElse("0")));
    } catch (Exception e) {
      return 0;
    }
  }

  private static double parseDoubleSafe(String v) {
    try {
      return Double.parseDouble(Optional.ofNullable(v).orElse("0"));
    } catch (Exception e) {
      return 0.0;
    }
  }

  private static String secondsToPretty(double seconds) {
    if (seconds <= 0) return "—";
    int s = (int) Math.round(seconds);
    int mins = s / 60;
    int rem = s % 60;
    if (mins <= 0) return rem + "s";
    return mins + "m " + rem + "s";
  }

  private static class Kpis {
    final int users;
    final int newUsers;
    final String avgSessionDurationPretty;

    Kpis(int users, int newUsers, String avgSessionDurationPretty) {
      this.users = users;
      this.newUsers = newUsers;
      this.avgSessionDurationPretty = avgSessionDurationPretty;
    }
  }
}
