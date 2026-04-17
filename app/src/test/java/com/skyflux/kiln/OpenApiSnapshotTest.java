package com.skyflux.kiln;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Contract test — asserts the live OpenAPI document matches the committed
 * snapshot at {@code docs/openapi-snapshot.json}. Any API change causes this
 * test to fail; developer must either:
 * <ul>
 *   <li>Back out the change (it was unintentional), or</li>
 *   <li>Refresh the snapshot via {@code ./gradlew :app:updateOpenApiSnapshot}
 *       and commit both the code change and the snapshot refresh in the same
 *       commit, so the API delta is visible in code review.</li>
 * </ul>
 *
 * <p>Strictness: {@link JSONCompareMode#NON_EXTENSIBLE}. Key order is ignored,
 * array order is strict, extra top-level fields are rejected, and nested
 * structural/semantic diffs fail. This catches:
 * <ul>
 *   <li>New or removed endpoints</li>
 *   <li>Changed request / response schemas</li>
 *   <li>Changed status codes, operationIds, tags</li>
 *   <li>Removed/renamed component schemas</li>
 * </ul>
 *
 * <p>The {@code servers} array is stripped from both documents before
 * comparison — its {@code url} contains a per-test random port, which is
 * environment-specific rather than part of the API contract.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class OpenApiSnapshotTest {

    /**
     * Relative path from the {@code app/} module working dir (where Gradle
     * runs the test task) to the snapshot file.
     */
    static final Path SNAPSHOT_PATH = Path.of("..", "docs", "openapi-snapshot.json");

    @Autowired RestTestClient client;

    @Test
    void openApiDocumentMatchesCommittedSnapshot() throws Exception {
        String live = client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        String snapshot = Files.readString(SNAPSHOT_PATH);

        JSONAssert.assertEquals(
                normalize(snapshot),
                normalize(live),
                JSONCompareMode.NON_EXTENSIBLE);
    }

    /**
     * Strips fields that are environment-specific rather than part of the
     * API contract. Currently: {@code servers[].url} carries the random
     * Tomcat port — varies per test run and per local/CI environment.
     *
     * <p>I3: wraps parse failures with the head of the offending JSON.
     * Without this, a malformed snapshot produces a generic
     * {@code JSONException at OpenApiSnapshotTest:80} that doesn't explain
     * whether the live body or the committed snapshot is broken, nor what
     * the first bytes look like.
     *
     * <p>Note: springdoc excludes {@code /actuator/**} from
     * {@code /v3/api-docs} by default — those endpoints are framework-supplied
     * and intentionally not part of the API contract this snapshot tracks (L2).
     */
    static String normalize(String json) throws JSONException {
        try {
            JSONObject doc = new JSONObject(json);
            doc.remove("servers");
            return doc.toString();
        } catch (JSONException e) {
            int len = (json == null) ? 0 : json.length();
            String head = (json == null) ? "<null>"
                    : json.substring(0, Math.min(120, len));
            JSONException wrapped = new JSONException(
                    "Failed to parse OpenAPI JSON (length=" + len
                            + ", head=" + head + "): " + e.getMessage());
            wrapped.initCause(e);
            throw wrapped;
        }
    }
}
