package ca.rddigitech.rd_dashboard_api;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;
import com.google.analytics.data.v1beta.BetaAnalyticsDataSettings;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

@Configuration
public class AnalyticsConfig {

  @Bean
  public BetaAnalyticsDataClient analyticsDataClient() throws Exception {
    String json = System.getenv("GOOGLE_SERVICE_ACCOUNT_JSON");

    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
        "GOOGLE_SERVICE_ACCOUNT_JSON env var is missing"
      );
    }

    GoogleCredentials credentials =
        ServiceAccountCredentials.fromStream(
            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        ).createScoped("https://www.googleapis.com/auth/analytics.readonly");

    BetaAnalyticsDataSettings settings =
        BetaAnalyticsDataSettings.newBuilder()
            .setCredentialsProvider(
                FixedCredentialsProvider.create(credentials)
            )
            .build();

    return BetaAnalyticsDataClient.create(settings);
  }
}