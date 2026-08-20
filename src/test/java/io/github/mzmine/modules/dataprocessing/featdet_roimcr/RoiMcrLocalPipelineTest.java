/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoiMcrLocalPipelineTest {

  private static final RoiMcrCore.McrOptions MCR_OPTIONS = new RoiMcrCore.McrOptions(3, 4, 250,
      60, 1e-7, 0.01, 0.65, 1, 0.05, 0.001, 2, 0.001, 0.999, 20260804L);

  @Test
  void resolvesSeparatedLocalRegionsWithoutCrossRegionMerging() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        gaussianRoi(1, 12, 38, 25, 4, 1_000, 100),
        gaussianRoi(2, 12, 38, 25, 4, 650, 200),
        gaussianRoi(3, 62, 92, 77, 5, 900, 300),
        gaussianRoi(4, 62, 92, 77, 5, 500, 400));
    final RoiMcrWindows.Options windowOptions = new RoiMcrWindows.Options(2, 3, 12, 40, 2,
        0.05);
    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(110, rois, windowOptions);

    assertEquals(2, windows.size());
    final List<RoiMcrReconciliation.Candidate> candidates = new ArrayList<>();
    for (RoiMcrWindows.Window window : windows) {
      candidates.addAll(fit(window, rois));
    }

    assertEquals(2, candidates.size());
    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(candidates, 4,
        0.75, 0.01, 10);
    assertEquals(2, groups.size());
  }

  @Test
  void reconcilesSameSourceFittedInTwoOverlappingWindows() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        gaussianRoi(1, 18, 62, 40, 6, 1_000, 100),
        gaussianRoi(2, 18, 62, 40, 6, 700, 200),
        gaussianRoi(3, 18, 62, 40, 6, 350, 300));
    final RoiMcrWindows.Window first = new RoiMcrWindows.Window(1, 18, 50, 40,
        List.of(0, 1, 2), 1_000);
    final RoiMcrWindows.Window second = new RoiMcrWindows.Window(2, 30, 62, 40,
        List.of(0, 1, 2), 1_000);

    final List<RoiMcrReconciliation.Candidate> candidates = new ArrayList<>();
    candidates.addAll(fit(first, rois));
    candidates.addAll(fit(second, rois));
    assertEquals(2, candidates.size());

    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(candidates, 4,
        0.75, 0.01, 10);
    assertEquals(1, groups.size());
    assertEquals(2, groups.get(0).members().size());
  }

  private static List<RoiMcrReconciliation.Candidate> fit(RoiMcrWindows.Window window,
      List<RoiMcrCore.RoiTrace> allRois) {
    final List<RoiMcrCore.RoiTrace> selected = window.roiIndices().stream().map(allRois::get)
        .toList();
    final double[][] matrix = new double[window.scanCount()][selected.size()];
    for (int row = 0; row < matrix.length; row++) {
      final int globalScan = window.startScan() + row;
      for (int column = 0; column < selected.size(); column++) {
        matrix[row][column] = selected.get(column).intensityAt(globalScan);
      }
    }
    final RoiMcrCore.McrResult result = RoiMcrCore.selectRank(matrix, MCR_OPTIONS, () -> false);
    assertNotNull(result);
    assertEquals(1, result.rank());
    final List<RoiMcrRobustness.Signature> signatures = RoiMcrRobustness.signatures(result,
        result.spectra(), selected);
    final List<RoiMcrReconciliation.Candidate> candidates = new ArrayList<>();
    for (int component = 0; component < result.rank(); component++) {
      final int globalApex = window.startScan() + argmax(result.concentrations(), component);
      candidates.add(new RoiMcrReconciliation.Candidate(window.id(), component + 1,
          window.startScan(), window.endScan(), globalApex, signatures.get(component), 0.9, 1.0,
          result.restartStability(), result.explainedVariance()));
    }
    return candidates;
  }

  private static int argmax(double[][] concentrations, int component) {
    int best = 0;
    for (int row = 1; row < concentrations.length; row++) {
      if (concentrations[row][component] > concentrations[best][component]) {
        best = row;
      }
    }
    return best;
  }

  private static RoiMcrCore.RoiTrace gaussianRoi(int id, int start, int end, int apex,
      double width, double height, double mz) {
    final int length = end - start + 1;
    final int[] scans = new int[length];
    final double[] mzs = new double[length];
    final double[] intensities = new double[length];
    for (int index = 0; index < length; index++) {
      final int scan = start + index;
      scans[index] = scan;
      mzs[index] = mz;
      intensities[index] = height * Math.exp(-0.5 * Math.pow((scan - apex) / width, 2));
    }
    return new RoiMcrCore.RoiTrace(id, start, end, scans, mzs, intensities, mz, height, apex,
        length);
  }
}
