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
package io.github.mzmine.parity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Runs only when the governed differential environment is explicitly supplied by CI. */
class OpenOfflineDifferentialReportIntegrationTest {

  @Test
  void writesTheGovernedOpenOfflineReport() throws Exception {
    String input = System.getenv("OPEN_OFFLINE_DIFFERENTIAL_INPUT");
    String output = System.getenv("OPEN_OFFLINE_DIFFERENTIAL_OUTPUT");
    Assumptions.assumeTrue(input != null && output != null,
        "Governed differential paths were not supplied");

    OpenOfflineDifferentialReportMain.main(new String[]{
        "--input", input,
        "--output", output,
        "--dataset-id", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_DATASET_ID"),
        "--relative-path", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_RELATIVE_PATH"),
        "--expected-size", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_EXPECTED_SIZE"),
        "--expected-sha256", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_EXPECTED_SHA256"),
        "--source-ms-level", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_SOURCE_MS_LEVEL"),
        "--noise-level", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_NOISE_LEVEL"),
        "--producer-ref", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_PRODUCER_REF"),
        "--producer-commit", requireEnvironment("OPEN_OFFLINE_DIFFERENTIAL_PRODUCER_COMMIT")
    });

    Path report = Path.of(output);
    assertTrue(Files.isRegularFile(report), "The open-offline report was not written");
    assertTrue(Files.size(report) > 0, "The open-offline report is empty");
  }

  private static String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing governed environment variable " + name);
    }
    return value;
  }
}
