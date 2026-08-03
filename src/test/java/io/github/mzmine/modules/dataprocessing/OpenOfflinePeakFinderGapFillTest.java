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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.impl.SimpleScan;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.PeakFinderModule;
import io.github.mzmine.modules.dataprocessing.gapfill_peakfinder.PeakFinderParameters;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.project.impl.RawDataFileImpl;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.ExitCode;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

/** Validates deterministic recovery of a deliberately missing aligned feature. */
class OpenOfflinePeakFinderGapFillTest {

  private static final double DOUBLE_TOLERANCE = 0.0001;
  private static final float FLOAT_TOLERANCE = 0.0001f;
  private static final Instant MODULE_DATE = Instant.parse("2026-08-03T00:00:00Z");
  private static final double TARGET_MZ = 300.0;

  @Test
  void recoversWeakRawSignalAsEstimatedFeature() throws IOException {
    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);

    try {
      final RawDataFile rawA = createRawFile("synthetic-gap-A", Color.BLACK,
          new float[]{0.80f, 0.90f, 1.00f, 1.10f, 1.20f},
          new double[]{500, 3000, 7000, 3000, 500});
      final RawDataFile rawB = createRawFile("synthetic-gap-B", Color.BLUE,
          new float[]{0.83f, 0.93f, 1.03f, 1.13f, 1.23f},
          new double[]{100, 220, 420, 220, 100});
      project.addFile(rawA);
      project.addFile(rawB);

      final ModularFeatureList aligned = new ModularFeatureList("synthetic aligned gap fixture",
          null, rawA, rawB);
      final Feature detectedA = createDetectedFeature(aligned, rawA);
      final ModularFeatureListRow row = new ModularFeatureListRow(aligned, 1, detectedA);
      aligned.addRow(row);
      project.addFeatureList(aligned);

      assertEquals(1, aligned.getNumberOfRows());
      assertNotNull(row.getFeature(rawA));
      assertNull(row.getFeature(rawB));
      assertEquals(TARGET_MZ, row.getAverageMZ(), DOUBLE_TOLERANCE);
      assertEquals(1.00f, row.getAverageRT(), FLOAT_TOLERANCE);

      final FeatureList gapFilled = runGapFiller(project, aligned);
      final FeatureListRow filledRow = gapFilled.getRow(0);
      final Feature recovered = filledRow.getFeature(rawB);

      assertNotNull(recovered);
      assertEquals(FeatureStatus.ESTIMATED, recovered.getFeatureStatus());
      assertEquals(TARGET_MZ, recovered.getMZ(), DOUBLE_TOLERANCE);
      assertEquals(1.03f, recovered.getRT(), FLOAT_TOLERANCE);
      assertEquals(420.0f, recovered.getHeight(), FLOAT_TOLERANCE);
      assertTrue(recovered.getArea() > 0.0f);
      assertEquals(2, filledRow.getNumberOfFeatures());
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
    }
  }

  private static RawDataFile createRawFile(final String name, final Color color,
      final float[] retentionTimes, final double[] intensities) throws IOException {
    final RawDataFileImpl raw = new RawDataFileImpl(name, null, null, color);
    for (int i = 0; i < retentionTimes.length; i++) {
      final Scan scan = new SimpleScan(raw, i + 1, 1, retentionTimes[i], null,
          new double[]{TARGET_MZ}, new double[]{intensities[i]}, MassSpectrumType.CENTROIDED,
          PolarityType.POSITIVE, "synthetic gap-fill scan", Range.closed(250.0, 350.0));
      raw.addScan(scan);
    }
    return raw;
  }

  private static Feature createDetectedFeature(final ModularFeatureList featureList,
      final RawDataFile raw) {
    final List<Scan> scans = raw.getScans();
    final double[] mzs = new double[scans.size()];
    final double[] intensities = new double[scans.size()];
    for (int i = 0; i < scans.size(); i++) {
      mzs[i] = TARGET_MZ;
      intensities[i] = scans.get(i).getIntensityValue(0);
    }

    final SimpleIonTimeSeries series = new SimpleIonTimeSeries(null, mzs, intensities, scans);
    return new ModularFeature(featureList, raw, series, FeatureStatus.DETECTED);
  }

  private static FeatureList runGapFiller(final MZmineProject project,
      final ModularFeatureList aligned) {
    final int before = project.getNumberOfFeatureLists();
    final PeakFinderParameters parameters = new PeakFinderParameters();
    parameters.setParameter(PeakFinderParameters.peakLists, new FeatureListsSelection(aligned));
    parameters.setParameter(PeakFinderParameters.suffix, "gap-filled");
    parameters.setParameter(PeakFinderParameters.intTolerance, 0.30);
    parameters.setParameter(PeakFinderParameters.MZTolerance, new MZTolerance(0.005, 10.0));
    parameters.setParameter(PeakFinderParameters.RTTolerance,
        new RTTolerance(0.25f, Unit.MINUTES));
    parameters.setParameter(PeakFinderParameters.RTCorrection, false);
    parameters.setParameter(PeakFinderParameters.useParallel, false);
    parameters.setParameter(PeakFinderParameters.handleOriginal, OriginalFeatureListOption.KEEP);

    final Collection<Task> tasks = new ArrayList<>();
    final ExitCode setup = new PeakFinderModule().runModule(project, parameters, tasks, MODULE_DATE);
    assertEquals(ExitCode.OK, setup);
    assertEquals(1, tasks.size());

    final Task task = tasks.iterator().next();
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(before + 1, project.getNumberOfFeatureLists());
    return project.getCurrentFeatureLists().get(before);
  }
}
