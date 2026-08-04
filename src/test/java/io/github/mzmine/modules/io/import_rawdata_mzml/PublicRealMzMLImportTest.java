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
package io.github.mzmine.modules.io.import_rawdata_mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Characterizes a frozen, externally downloaded public LC-MS mzML fixture. */
class PublicRealMzMLImportTest {

  private static final String FIXTURE_PROPERTY = "openOffline.publicMzML";
  private static final long EXPECTED_SIZE = 87_090_777L;

  @Test
  void importsAndCharacterizesFrozenPublicMzML() throws Exception {
    final String configuredPath = System.getProperty(FIXTURE_PROPERTY);
    Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
        () -> "Set -D" + FIXTURE_PROPERTY + "=<path> to run the public real-data test");

    final Path fixturePath = Path.of(configuredPath).toAbsolutePath().normalize();
    final File fixture = fixturePath.toFile();
    assertTrue(fixture.isFile(), () -> "Missing public mzML fixture: " + fixturePath);
    assertEquals(EXPECTED_SIZE, Files.size(fixturePath), "Frozen fixture size changed");

    final MZmineProject project = new MZmineProjectImpl();
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.getParameter(MSDKmzMLImportParameters.fileNames).setValue(new File[]{fixture});

    final Instant started = Instant.now();
    final MSDKmzMLImportTask task = new MSDKmzMLImportTask(project, fixture,
        MSDKmzMLImportModule.class, parameters, Instant.parse("2026-08-04T00:00:00Z"), null);
    task.run();
    final long elapsedMillis = Duration.between(started, Instant.now()).toMillis();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    final RawDataFile[] files = project.getDataFiles();
    assertEquals(1, files.length);

    final RawDataFile raw = files[0];
    try {
      final int scanCount = raw.getNumOfScans();
      final int[] msLevels = raw.getMSLevels();
      final Range<Float> rtRange = raw.getDataRTRange();
      final Range<Double> mzRange = raw.getDataMZRange();
      final List<PolarityType> polarity = raw.getDataPolarity();
      final long totalDataPoints = raw.stream().mapToLong(Scan::getNumberOfDataPoints).sum();

      assertTrue(scanCount > 100, "Real LC-MS file should contain more than 100 scans");
      assertTrue(msLevels.length >= 1, "At least one MS level is required");
      assertTrue(rtRange.upperEndpoint() > rtRange.lowerEndpoint(), "RT range must be positive");
      assertTrue(mzRange.upperEndpoint() > mzRange.lowerEndpoint(), "m/z range must be positive");
      assertFalse(polarity.isEmpty(), "At least one polarity must be reported");
      assertTrue(totalDataPoints > scanCount, "Spectra must contain data points");
      assertTrue(raw.getMaxRawDataPoints() > 0, "At least one scan must contain raw data points");

      final Path output = Path.of("build", "public_validation",
          "zenodo_14001110_Banane_30ngmL_001_import_summary.json");
      Files.createDirectories(output.getParent());
      Files.writeString(output, createSummary(raw, fixturePath, elapsedMillis, totalDataPoints),
          StandardCharsets.UTF_8);

      System.out.println("PUBLIC_MZML_IMPORT_SUMMARY=" + output.toAbsolutePath());
      System.out.println(Files.readString(output));
    } finally {
      raw.close();
    }
  }

  private static String createSummary(final RawDataFile raw, final Path fixturePath,
      final long elapsedMillis, final long totalDataPoints) {
    final int[] levels = raw.getMSLevels();
    final String levelCounts = Arrays.stream(levels)
        .mapToObj(level -> "    \"" + level + "\": " + raw.getNumOfScans(level))
        .collect(Collectors.joining(",\n"));
    final String polarities = raw.getDataPolarity().stream()
        .map(value -> "\"" + escape(value.toString()) + "\"")
        .collect(Collectors.joining(", "));
    final Range<Float> rt = raw.getDataRTRange();
    final Range<Double> mz = raw.getDataMZRange();

    return String.format(Locale.ROOT, """
        {
          "schema_version": 1,
          "fixture": "%s",
          "size_bytes": %d,
          "import_elapsed_ms": %d,
          "scan_count": %d,
          "ms_level_counts": {
        %s
          },
          "retention_time_range_minutes": [%.8f, %.8f],
          "mz_range": [%.8f, %.8f],
          "polarities": [%s],
          "spectrum_type": "%s",
          "total_data_points": %d,
          "max_raw_data_points_per_scan": %d,
          "contains_empty_scans": %s,
          "contains_zero_or_negative_intensity": %s
        }
        """, escape(fixturePath.toString()), EXPECTED_SIZE, elapsedMillis, raw.getNumOfScans(),
        levelCounts, rt.lowerEndpoint(), rt.upperEndpoint(), mz.lowerEndpoint(), mz.upperEndpoint(),
        polarities, escape(raw.getSpectraType().toString()), totalDataPoints,
        raw.getMaxRawDataPoints(), raw.isContainsEmptyScans(), raw.isContainsZeroIntensity());
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
