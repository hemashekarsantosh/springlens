package io.springlens.notification.delivery;

import io.springlens.notification.event.RecommendationsReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Posts startup analysis summary as a GitHub PR comment.
 * webhookUrl format: https://api.github.com/repos/{owner}/{repo}/issues/{pr_number}/comments
 * with Authorization header embedded in filter_config.
 */
@Component
public class GitHubPrCommentDelivery {

    private static final Logger log = LoggerFactory.getLogger(GitHubPrCommentDelivery.class);

    private final RestClient restClient;

    public GitHubPrCommentDelivery() {
        this.restClient = RestClient.builder()
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public int deliver(String apiUrl, String githubToken, RecommendationsReadyEvent event) {
        log.info("Posting GitHub PR comment snapshot={} url={}", event.snapshotId(), apiUrl);

        String commentBody = buildCommentBody(event);

        try {
            var response = restClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + githubToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", commentBody))
                    .retrieve()
                    .toBodilessEntity();

            int statusCode = response.getStatusCode().value();
            log.info("GitHub PR comment posted status={} snapshot={}", statusCode, event.snapshotId());
            return statusCode;
        } catch (Exception ex) {
            log.error("GitHub PR comment failed snapshot={} error={}", event.snapshotId(), ex.getMessage());
            throw ex;
        }
    }

    private String buildCommentBody(RecommendationsReadyEvent event) {
        String savingsSec = String.format("%.1f", event.totalPotentialSavingsMs() / 1000.0);

        String topRecs = event.topRecommendations().stream()
                .limit(3)
                .map(r -> String.format("| %s | %s | %dms | %s |",
                        r.title(), r.category(), r.estimatedSavingsMs(), r.effort()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                ## SpringLens Startup Analysis 🚀

                **Environment:** `%s`
                **Potential savings:** `%ss`
                **Recommendations:** %d

                ### Top Recommendations

                | Title | Category | Est. Savings | Effort |
                |-------|----------|-------------|--------|
                %s

                <sub>Snapshot ID: `%s` | [View in SpringLens Dashboard](https://app.springlens.io/projects/%s)</sub>
                """,
                event.environmentName(),
                savingsSec,
                event.recommendationCount(),
                topRecs.isEmpty() ? "| No recommendations | — | — | — |" : topRecs,
                event.snapshotId(),
                event.projectId());
    }
}
