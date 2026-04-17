package com.skyflux.kiln;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.ServerPortInfoApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Boots the full application (with {@link TestcontainersConfiguration}'s
 * Postgres + Redis containers), fetches {@code /v3/api-docs}, pretty-prints
 * the JSON, and writes it to {@code docs/openapi-snapshot.json}.
 *
 * <p>Kept out of the main test suite because it mutates a tracked file.
 * Invoked via:
 * <pre>
 *     ./gradlew :app:updateOpenApiSnapshot
 * </pre>
 *
 * <p>Workflow: after a deliberate API change, run this task and commit the
 * refreshed snapshot in the same commit as the code change — that way the
 * API delta shows up in the PR diff alongside the code that caused it.
 *
 * <p>Note: we use the classic Jackson 2 {@code ObjectMapper} here purely as
 * a pretty-printer — not as an autowired bean. Boot 4 ships Jackson 3
 * ({@code tools.jackson.databind.JsonMapper}), but Jackson 2 is still on the
 * test classpath transitively (swagger-core, jackson-datatype-jsr310, etc.),
 * so {@code new ObjectMapper()} is fine for one-off utility use.
 *
 * <p>The {@code servers} field is stripped before saving the snapshot —
 * its {@code url} contains a random Tomcat port, which would otherwise churn
 * the file on every refresh and create noisy git diffs. The compare-time
 * normalize in {@link OpenApiSnapshotTest#normalize(String)} also strips
 * {@code servers} so the comparison is symmetric.
 */
public final class OpenApiSnapshotRefresh {

    private OpenApiSnapshotRefresh() {
        // Utility main — no instantiation.
    }

    public static void main(String[] args) throws Exception {
        SpringApplication app = new SpringApplication(
                KilnApplication.class,
                TestcontainersConfiguration.class);
        // Force a random server port. `setDefaultProperties` loses to application.yml
        // (which pins `server.port: ${SERVER_PORT:8080}`), so we stuff it into
        // the high-priority command-line property source via args instead.
        app.addInitializers(new ServerPortInfoApplicationContextInitializer());

        try (ConfigurableApplicationContext ctx = app.run("--server.port=0")) {
            Environment env = ctx.getEnvironment();
            int port = Integer.parseInt(env.getProperty("local.server.port"));

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(
                                    URI.create("http://localhost:" + port + "/v3/api-docs"))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                throw new IllegalStateException(
                        "Failed to fetch /v3/api-docs: HTTP " + resp.statusCode()
                                + " — body: " + resp.body());
            }

            Path out = Path.of("..", "docs", "openapi-snapshot.json");
            Files.writeString(out, prettify(resp.body()));
            System.out.println("[OpenApiSnapshotRefresh] wrote " + out.toAbsolutePath());
        }
    }

    /**
     * Pretty-prints JSON with 2-space indent for meaningful git diffs,
     * stripping the volatile {@code servers} array in the process.
     *
     * <p>I2: fails LOUD on parse error. Earlier version silently fell back
     * to the raw body, which would:
     * <ul>
     *   <li>Write a snapshot still containing the volatile {@code servers}
     *       array (→ churning git diffs).</li>
     *   <li>Commit a malformed snapshot if the body wasn't valid JSON.</li>
     *   <li>Defer the error surface to the next test run, two steps removed
     *       from its root cause.</li>
     * </ul>
     * Refreshes are infrequent one-offs — failing loudly is the right default.
     */
    static String prettify(String json) {
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            if (root instanceof ObjectNode obj) {
                obj.remove("servers");
            }
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to pretty-print /v3/api-docs response — refusing to overwrite snapshot."
                            + " Root cause: " + e.getMessage()
                            + ". First 200 chars of body: "
                            + (json == null ? "<null>" : json.substring(0, Math.min(200, json.length()))),
                    e);
        }
    }
}
