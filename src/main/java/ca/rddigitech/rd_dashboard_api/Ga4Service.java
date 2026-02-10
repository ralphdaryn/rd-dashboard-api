package ca.rddigitech.rd_dashboard_api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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

  private String property() {
    String id = System.getenv("GA4_PROPERTY_ID");
    if (id == null || id.isBlank()) {
      throw new IllegalStateException("GA4_PROPERTY_ID env var is missing.");
    }
    return "properties/" + id.trim();
  }

  // ✅ SAME JSON shape your Dashboard.js expects
  public Map<String, Object> getLast30DaysResults() {
    Kpis kpis = fetchKpis();

    List<Map<String, Object>> topSources = fetchTopSources(8);
    String topTrafficSource = topSources.isEmpty()
        ? "(not set)"
        : String.valueOf(topSources.get(0).get("source"));

    List<Map<String, Object>> topPages = fetchTopPages(12);

    // These will be 0 unless you created these GA4 events
    int contactSubmits = fetchEventCount("contact_submit");
    int bookingClicks = fetchEventCount("booking_click");

    Map<String, Object> payload = new HashMap<>();
    payload.put("rangeLabel", "Last 30 days");

    payload.put("users30d", kpis.users30d);
    payload.put("newUsers30d", kpis.newUsers30d);

    // ✅ Keep your existing key name so your React UI doesn’t break
    payload.put("avgEngagementTime", kpis.avgEngagementTimePretty);

    payload.put("contactSubmits", contactSubmits);
    payload.put("bookingClicks", bookingClicks);

    payload.put("topTrafficSource", topTrafficSource);
    payload.put("topSources", topSources);
    payload.put("topPages", topPages);

    return payload;
  }

  private Kpis fetchKpis() {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(property())
        .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
        .addMetrics(Metric.newBuilder().setName("activeUsers"))
        .addMetrics(Metric.newBuilder().setName("newUsers"))

        // ✅ FIX: valid GA4 Data API metric (seconds)
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

  private List<Map<String, Object>> fetchTopSources(int limit) {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(property())
        .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
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

  private List<Map<String, Object>> fetchTopPages(int limit) {
    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(property())
        .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
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

  private int fetchEventCount(String eventName) {
    Filter filter = Filter.newBuilder()
        .setFieldName("eventName")
        .setStringFilter(Filter.StringFilter.newBuilder()
            .setMatchType(Filter.StringFilter.MatchType.EXACT)
            .setValue(eventName))
        .build();

    FilterExpression exp = FilterExpression.newBuilder().setFilter(filter).build();

    RunReportRequest request = RunReportRequest.newBuilder()
        .setProperty(property())
        .addDateRanges(DateRange.newBuilder().setStartDate("30daysAgo").setEndDate("today"))
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
    final int users30d;
    final int newUsers30d;
    final String avgEngagementTimePretty;

    Kpis(int users30d, int newUsers30d, String avgEngagementTimePretty) {
      this.users30d = users30d;
      this.newUsers30d = newUsers30d;
      this.avgEngagementTimePretty = avgEngagementTimePretty;
    }
  }
}