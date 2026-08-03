/*
 * Copyright (c) 2026 Contributors to the open offline fork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.mzmine.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards startup components against accidental reintroduction of outbound network access. */
class OfflineStartupNetworkPolicyTest {

  private static final List<Path> STARTUP_POLICY_FILES = List.of(
      Path.of("src/main/java/io/github/mzmine/main/MZmineCore.java"),
      Path.of("src/main/java/io/github/mzmine/main/GoogleAnalyticsTracker.java"),
      Path.of("src/main/java/io/github/mzmine/gui/NewVersionCheck.java")
  );

  private static final List<String> FORBIDDEN_TOKENS = List.of(
      "HttpClient",
      "HttpRequest",
      "HttpResponse",
      "HttpURLConnection",
      "URLConnection",
      ".openConnection(",
      "new URL(",
      "InetUtils.retrieveData",
      "google-analytics.com",
      "mzmine.github.io/version",
      "auth.mzio.io"
  );

  @Test
  void startupComponentsDoNotContainNetworkClientsOrEndpoints() throws IOException {
    for (Path path : STARTUP_POLICY_FILES) {
      assertTrue(Files.isRegularFile(path), () -> "Missing startup policy file: " + path);
      final String source = Files.readString(path);

      for (String token : FORBIDDEN_TOKENS) {
        assertFalse(source.contains(token),
            () -> "Forbidden startup network token '" + token + "' found in " + path);
      }
    }
  }

  @Test
  void compatibilityFacadesRemainExplicitlyInert() throws IOException {
    final String analytics = Files.readString(
        Path.of("src/main/java/io/github/mzmine/main/GoogleAnalyticsTracker.java"));
    final String updates = Files.readString(
        Path.of("src/main/java/io/github/mzmine/gui/NewVersionCheck.java"));

    assertTrue(analytics.contains("does not transmit usage telemetry"));
    assertTrue(updates.contains("Automatic update checks are disabled"));
  }
}
