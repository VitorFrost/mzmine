/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.time.Instant;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

class RoiMcrTaskIntegrationTest {

  @Test
  void createsIonFeaturesDirectlyFromScanMassLists() {
    final MZmineProjectImpl project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);
    final RawDataFile raw = mock(RawDataFile.class);
    final ObservableList<Scan> scans = createScans(raw);
    when(raw.getName()).thenReturn("synthetic-roimcr");
    when(raw.getScans()).thenReturn(scans);
    when(raw.getAppliedMethods()).thenReturn(FXCollections.observableArrayList());
    project.addFile(raw);

    final RoiMcrParameters parameters = new RoiMcrParameters();
    parameters.setParameter(RoiMcrParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(RoiMcrParameters.mzTolerance, new MZTolerance(0.01, 10));
    parameters.setParameter(RoiMcrParameters.noiseLevel, 100d);
    parameters.setParameter(RoiMcrParameters.seedIntensity, 300d);
    parameters.setParameter(RoiMcrParameters.minimumFeatureHeight, 300d);
    parameters.setParameter(RoiMcrParameters.minimumDataPoints, 4);
    parameters.setParameter(RoiMcrParameters.minimumConsecutiveScans, 3);
    parameters.setParameter(RoiMcrParameters.maximumComponents, 3);
    parameters.setParameter(RoiMcrParameters.restarts, 4);
    parameters.setParameter(RoiMcrParameters.minimumComponentIons, 2);
    parameters.setParameter(RoiMcrParameters.weightingExponent, 0.5);
    parameters.setParameter(RoiMcrParameters.minimumAssignmentFraction, 0.5);

    final RoiMcrTask task = new RoiMcrTask(project, raw, parameters, null,
        Instant.parse("2026-08-04T00:00:00Z"));
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(1d, task.getFinishedPercentage(), 1e-9);
    assertEquals(1, project.getNumberOfFeatureLists());
    final FeatureList result = project.getCurrentFeatureLists().get(0);
    assertEquals(3, result.getNumberOfRows());
    result.getRows().forEach(row -> {
      assertTrue(row.getAverageMZ() >= 100 && row.getAverageMZ() <= 300);
      assertEquals(1, row.getNumberOfFeatures());
    });
  }

  private static ObservableList<Scan> createScans(RawDataFile raw) {
    final ObservableList<Scan> scans = FXCollections.observableArrayList();
    for (int index = 0; index < 21; index++) {
      final Scan scan = mock(Scan.class);
      final MassList massList = mock(MassList.class);
      final double profile = Math.exp(-0.5 * Math.pow((index - 10d) / 3d, 2));
      final double[] mzs = {100d, 200d, 300d};
      final double[] intensities = {1200d * profile, 800d * profile, 500d * profile};
      when(scan.getDataFile()).thenReturn(raw);
      when(scan.getScanNumber()).thenReturn(index + 1);
      when(scan.getMSLevel()).thenReturn(1);
      when(scan.getPolarity()).thenReturn(PolarityType.POSITIVE);
      when(scan.getRetentionTime()).thenReturn(index * 0.05f);
      when(scan.getMassList()).thenReturn(massList);
      when(massList.getNumberOfDataPoints()).thenReturn(3);
      when(massList.getMzValues(any(double[].class))).thenAnswer(invocation -> mzs.clone());
      when(massList.getIntensityValues(any(double[].class)))
          .thenAnswer(invocation -> intensities.clone());
      scans.add(scan);
    }
    return scans;
  }
}
