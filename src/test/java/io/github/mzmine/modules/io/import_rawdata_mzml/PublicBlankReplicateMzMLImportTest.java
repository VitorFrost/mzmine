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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
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

/** Characterizes a frozen public analytical blank and second technical replicate. */
class PublicBlankReplicateMzMLImportTest {

  private static final String REPLICATE_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final double NUMERIC_TOLERANCE = 0.00001;

  private static final ExpectedImport REPLICATE_EXPECTED = new ExpectedImport(
      86_540_418L, 5_232, 4_085, 1_147,
      0.77683449f, 27.61018562f, 50.00003433, 749.99383545,
      6_410_718L, 2_703);

  private static final ExpectedImport BLANK_EXPECTED = new ExpectedImport(
      86_111_130L, 5_207, 4_112, 1_095,
      0.77685571f, 27.61053658f, 50.00000000, 749.99389648,
      6_401_519L, 3_513);

  @Test
  void importsAndCharacterizesBlankAndSecondReplicate() throws Exception {
    final String replicatePath = System.getenv(REPLICATE_ENVIRONMENT);
    final String blankPath = System.getenv(BLANK_ENVIRONMENT);
    Assumptions.assumeTrue(replicatePath != null && !replicatePath.isBlank(),
        () -> "Set " + REPLICATE_ENVIRONMENT + "=<path> to run the public replicate test");
    Assumptions.assumeTrue(blankPath != null && !blankPath.isBlank(),
        () -> "Set " + BLANK_ENVIRONMENT + "=<path> to run the public blank test");

    final ImportSummary replicate = importAndSummarize(
        "Banane_30ngmL_002", Path.of(replicatePath), REPLICATE_EXPECTED.sizeBytes());
    final ImportSummary blank = importAndSummarize(
        "blank_001", Path.of(blankPath), BLANK_EXPECTED.sizeBytes());

    assertMatchesFrozenReference(replicate, REPLICATE_EXPECTED);
    assertMatchesFrozenReference(blank, BLANK_EXPECTED);

    // These runs belong to the same acquisition series and must remain metadata-compatible.
    assertArrayEquals(replicate.msLevels(), blank.msLevels());
    assertEquals(replicate.polarities(), blank.polarities());
    assertEquals(replicate.spectrumType(), blank.spectrumType());
    assertTrue(Math.abs(replicate.rtLower() - blank.rtLower()) < 0.1f);
    assertTrue(Math.abs(replicate.rtUpper() - blank.rtUpper()) < 0.1f);
    assertTrue(Math.abs(replicate.mzLower() - blank.mzLower()) < 0.01);
    assertTrue(Math.abs(replicate.mzUpper() - blank.mzUpper()) < 0.01);

    final Path output = Path.of("build", "public_validation",
        "zenodo_14001110_blank_replicate_import_summary.json");
    Files.createDirectories(output.getParent());
    Files.writeString(output, createCombinedSummary(replicate, blank), StandardCharsets.UTF_8);

    System.out.println("PUBLIC_BLANK_REPLICATE_IMPORT_SUMMARY=" + output.toAbsolutePath());
    System.out.println(Files.readString(output));
  }

  private static void assertMatchesFrozenReference(final ImportSummary actual,
      final ExpectedImport expected) {
    assertEquals(expected.sizeBytes(), actual.sizeBytes());
    assertEquals(expected.scanCount(), actual.scanCount());
    assertArrayEquals(new int[]{1, 2}, actual.msLevels());
    assertEquals(expected.ms1Scans(), actual.ms1Scans());
    assertEquals(expected.ms2Scans(), actual.ms2Scans());
    assertEquals(expected.rtLower(), actual.rtLower(), NUMERIC_TOLERANCE);
    assertEquals(expected.rtUpper(), actual.rtUpper(), NUMERIC_TOLERANCE);
    assertEquals(expected.mzLower(), actual.mzLower(), NUMERIC_TOLERANCE);
    assertEquals(expected.mzUpper(), actual.mzUpper(), NUMERIC_TOLERANCE);
    assertEquals(List.of(PolarityType.NEGATIVE), actual.polarities());
    assertEquals(MassSpectrumType.CENTROIDED, actual.spectrumType());
    assertEquals(expected.totalDataPoints(), actual.totalDataPoints());
    assertEquals(expected.maxRawDataPoints(), actual.maxRawDataPoints());
    assertFalse(actual.containsEmptyScans());
    assertFalse(actual.containsZeroOrNegativeIntensity());
  }

  private static ImportSummary importAndSummarize(final String label, final Path configuredPath,
      final long expectedSize) throws Exception {
    final Path fixturePath = configuredPath.toAbsolutePath().normalize();
    final File fixture = fixturePath.toFile();
    assertTrue(fixture.isFile(), () -> "Missing public mzML fixture: " + fixturePath);
    assertEquals(expectedSize, Files.size(fixturePath), "Frozen fixture size changed: " + label);

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
      final Range<Float> rtRange = raw.getDataRTRange();
      final Range<Double> mzRange = raw.getDataMZRange();
      return new ImportSummary(
          label,
          fixturePath,
          expectedSize,
          elapsedMillis,
          raw.getNumOfScans(),
          raw.getMSLevels(),
          raw.getNumOfScans(1),
          raw.getNumOfScans(2),
          rtRange.lowerEndpoint(),
          rtRange.upperEndpoint(),
          mzRange.lowerEndpoint(),
          mzRange.upperEndpoint(),
          List.copyOf(raw.getDataPolarity()),
          raw.getSpectraType(),
          raw.stream().mapToLong(Scan::getNumberOfDataPoints).sum(),
          raw.getMaxRawDataPoints(),
          raw.isContainsEmptyScans(),
          raw.isContainsZeroIntensity());
    } finally {
      raw.close();
    }
  }

  private static String createCombinedSummary(final ImportSummary replicate,
      final ImportSummary blank) {
    return "{\n"
        + "  \"schema_version\": 1,\n"
        + "  \"fixtures\": [\n"
        + indent(replicate.toJson(), 4) + ",\n"
        + indent(blank.toJson(), 4) + "\n"
        + "  ]\n"
        + "}\n";
  }

  private static String indent(final String value, final int spaces) {
    final String prefix = " ".repeat(spaces);
    return Arrays.stream(value.split("\\R", -1))
        .map(line -> prefix + line)
        .collect(Collectors.joining("\n"));
  }

  private static String escape(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private record ExpectedImport(long sizeBytes, int scanCount, int ms1Scans, int ms2Scans,
                                float rtLower, float rtUpper, double mzLower, double mzUpper,
                                long totalDataPoints, int maxRawDataPoints) {
  }

  private record ImportSummary(String label, Path fixturePath, long sizeBytes,
                               long importElapsedMillis, int scanCount, int[] msLevels,
                               int ms1Scans, int ms2Scans, float rtLower, float rtUpper,
                               double mzLower, double mzUpper, List<PolarityType> polarities,
                               MassSpectrumType spectrumType, long totalDataPoints,
                               int maxRawDataPoints, boolean containsEmptyScans,
                               boolean containsZeroOrNegativeIntensity) {

    private String toJson() {
      final String levels = Arrays.stream(msLevels)
          .mapToObj(Integer::toString)
          .collect(Collectors.joining(", "));
      final String polarityValues = polarities.stream()
          .map(value -> "\"" + escape(value.toString()) + "\"")
          .collect(Collectors.joining(", "));
      return String.format(Locale.ROOT, """
          {
            "label": "%s",
            "fixture": "%s",
            "size_bytes": %d,
            "import_elapsed_ms": %d,
            "scan_count": %d,
            "ms_levels": [%s],
            "ms1_scans": %d,
            "ms2_scans": %d,
            "retention_time_range_minutes": [%.8f, %.8f],
            "mz_range": [%.8f, %.8f],
            "polarities": [%s],
            "spectrum_type": "%s",
            "total_data_points": %d,
            "max_raw_data_points_per_scan": %d,
            "contains_empty_scans": %s,
            "contains_zero_or_negative_intensity": %s
          }""",
          escape(label), escape(fixturePath.toString()), sizeBytes, importElapsedMillis,
          scanCount, levels, ms1Scans, ms2Scans, rtLower, rtUpper, mzLower, mzUpper,
          polarityValues, escape(spectrumType.toString()), totalDataPoints,
          maxRawDataPoints, containsEmptyScans, containsZeroOrNegativeIntensity);
    }
  }
}
