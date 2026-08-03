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
package io.github.mzmine.modules.dataprocessing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyCSVExportTask;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowCommonElement;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowDataFileElement;
import io.github.mzmine.modules.io.export_features_gnps.fbmn.FeatureListRowsFilter;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Validates complete deterministic legacy CSV header and selected feature values. */
class OpenOfflineLegacyCsvExportTest {

  private static final double DOUBLE_TOLERANCE = 0.0001;
  private static final float FLOAT_TOLERANCE = 0.0001f;
  private static final Instant MODULE_DATE = Instant.parse("2026-08-03T00:00:00Z");

  @TempDir
  Path tempDirectory;

  @Test
  void exportsSelectedFeatureColumnsDeterministically() throws IOException {
    Locale.setDefault(Locale.US);
    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);

    try {
      final RawDataFile rawA = createRawFile("export-A", Color.BLACK,
          new float[]{0.50f, 0.60f, 0.70f, 0.90f, 1.00f, 1.10f});
      final RawDataFile rawB = createRawFile("export-B", Color.BLUE,
          new float[]{0.53f, 0.63f, 0.73f, 0.93f, 1.03f, 1.13f});
      project.addFile(rawA);
      project.addFile(rawB);

      final ModularFeatureList featureList = new ModularFeatureList("deterministic export", null,
          rawA, rawB);
      addRow(featureList, 1,
          createFeature(featureList, rawA, 150.0, 0, new double[]{1000, 9000, 1000},
              FeatureStatus.DETECTED),
          createFeature(featureList, rawB, 150.0, 0, new double[]{1100, 9900, 1100},
              FeatureStatus.DETECTED));
      addRow(featureList, 2,
          createFeature(featureList, rawA, 300.0, 3, new double[]{500, 7000, 500},
              FeatureStatus.DETECTED),
          createFeature(featureList, rawB, 300.0, 3, new double[]{100, 420, 100},
              FeatureStatus.ESTIMATED));
      project.addFeatureList(featureList);

      final Path output = tempDirectory.resolve("deterministic_features.csv");
      final LegacyCSVExportTask task = new LegacyCSVExportTask(
          new ModularFeatureList[]{featureList}, output.toFile(), ",",
          new LegacyExportRowCommonElement[]{LegacyExportRowCommonElement.ROW_ID,
              LegacyExportRowCommonElement.ROW_MZ, LegacyExportRowCommonElement.ROW_RT,
              LegacyExportRowCommonElement.ROW_FEATURE_NUMBER},
          new LegacyExportRowDataFileElement[]{LegacyExportRowDataFileElement.FEATURE_STATUS,
              LegacyExportRowDataFileElement.FEATURE_MZ,
              LegacyExportRowDataFileElement.FEATURE_RT,
              LegacyExportRowDataFileElement.FEATURE_HEIGHT},
          false, ";", FeatureListRowsFilter.ALL, MODULE_DATE);

      task.run();

      assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
      assertEquals(2, task.getProcessedItems());
      assertEquals(1.0, task.getFinishedPercentage(), DOUBLE_TOLERANCE);
      assertTrue(Files.isRegularFile(output));

      final List<String> lines = Files.readAllLines(output);
      assertEquals(3, lines.size());
      assertHeader(lines.get(0));
      assertFirstRow(lines.get(1));
      assertSecondRow(lines.get(2));
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
    }
  }

  private static RawDataFile createRawFile(final String name, final Color color,
      final float[] retentionTimes) throws IOException {
    final RawDataFileImpl raw = new RawDataFileImpl(name, null, null, color);
    for (int i = 0; i < retentionTimes.length; i++) {
      raw.addScan(new SimpleScan(raw, i + 1, 1, retentionTimes[i], null,
          new double[]{150.0, 300.0}, new double[]{1.0, 1.0}, MassSpectrumType.CENTROIDED,
          PolarityType.POSITIVE, "synthetic export scan", Range.closed(100.0, 350.0)));
    }
    return raw;
  }

  private static ModularFeature createFeature(final ModularFeatureList featureList,
      final RawDataFile raw, final double mz, final int firstScan,
      final double[] intensities, final FeatureStatus status) {
    final List<Scan> scans = raw.getScans().subList(firstScan, firstScan + intensities.length);
    final double[] mzValues = new double[intensities.length];
    java.util.Arrays.fill(mzValues, mz);
    final SimpleIonTimeSeries series = new SimpleIonTimeSeries(null, mzValues, intensities, scans);
    return new ModularFeature(featureList, raw, series, status);
  }

  private static void addRow(final ModularFeatureList featureList, final int id,
      final ModularFeature featureA, final ModularFeature featureB) {
    final ModularFeatureListRow row = new ModularFeatureListRow(featureList, id);
    row.addFeature(featureA.getRawDataFile(), featureA);
    row.addFeature(featureB.getRawDataFile(), featureB);
    featureList.addRow(row);
  }

  private static void assertHeader(final String line) {
    assertArrayEquals(new String[]{"row ID", "row m/z", "row retention time",
            "row number of detected features", "export-A Feature status",
            "export-A Feature m/z", "export-A Feature RT", "export-A Peak height",
            "export-B Feature status", "export-B Feature m/z", "export-B Feature RT",
            "export-B Peak height", ""},
        split(line));
  }

  private static void assertFirstRow(final String line) {
    final String[] values = split(line);
    assertEquals(13, values.length);
    assertEquals("1", values[0]);
    assertNumeric(values[1], 150.0);
    assertNumeric(values[2], 0.615);
    assertEquals("2", values[3]);
    assertEquals("DETECTED", values[4]);
    assertNumeric(values[5], 150.0);
    assertNumeric(values[6], 0.60);
    assertNumeric(values[7], 9000.0);
    assertEquals("DETECTED", values[8]);
    assertNumeric(values[9], 150.0);
    assertNumeric(values[10], 0.63);
    assertNumeric(values[11], 9900.0);
    assertEquals("", values[12]);
  }

  private static void assertSecondRow(final String line) {
    final String[] values = split(line);
    assertEquals(13, values.length);
    assertEquals("2", values[0]);
    assertNumeric(values[1], 300.0);
    assertNumeric(values[2], 1.015);
    assertEquals("1", values[3]);
    assertEquals("DETECTED", values[4]);
    assertNumeric(values[5], 300.0);
    assertNumeric(values[6], 1.00);
    assertNumeric(values[7], 7000.0);
    assertEquals("ESTIMATED", values[8]);
    assertNumeric(values[9], 300.0);
    assertNumeric(values[10], 1.03);
    assertNumeric(values[11], 420.0);
    assertEquals("", values[12]);
  }

  private static String[] split(final String line) {
    return line.split(",", -1);
  }

  private static void assertNumeric(final String actual, final double expected) {
    assertEquals(expected, Double.parseDouble(actual), DOUBLE_TOLERANCE);
  }
}
