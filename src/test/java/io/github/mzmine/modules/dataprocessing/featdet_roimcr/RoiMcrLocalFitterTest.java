/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class RoiMcrLocalFitterTest {

  @Test
  void fitsIndependentWindowsAndBoundsMatrixWidth() {
    final List<RoiMcrCore.RoiTrace> rois = new ArrayList<>();
    int id = 1;
    for (int ion = 0; ion < 20; ion++) {
      rois.add(trace(id++, 100 + ion, 20, 8, 1_000 - ion * 10));
    }
    for (int ion = 0; ion < 18; ion++) {
      rois.add(trace(id++, 200 + ion, 70, 9, 900 - ion * 8));
    }
    final RoiMcrWindows.Options windowOptions = new RoiMcrWindows.Options(2, 3, 12, 35, 2,
        0.05);
    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(110, rois, windowOptions);
    assertEquals(2, windows.size());

    final RoiMcrLocalFitter.Summary summary = RoiMcrLocalFitter.fit(windows, rois,
        mcrOptions(), new RoiMcrLocalFitter.Options(10, 0.5), () -> false);

    assertEquals(2, summary.fits().size());
    assertEquals(0, summary.failedWindows());
    assertEquals(10, summary.maximumSelectedRois());
    assertTrue(summary.maximumInputRois() > summary.maximumSelectedRois());
    for (RoiMcrLocalFitter.WindowFit fit : summary.fits()) {
      assertEquals(1, fit.result().rank());
      assertTrue(fit.originalExplained() > 0.98);
      assertEquals(fit.result().rank(), fit.signatures().size());
    }
  }

  @Test
  void roiSelectionIsDeterministicAndRetainsStrongPersistentSignals() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        trace(1, 100, 20, 8, 100),
        trace(2, 101, 20, 8, 500),
        trace(3, 102, 20, 8, 300),
        trace(4, 103, 20, 8, 700));
    final RoiMcrWindows.Window window = new RoiMcrWindows.Window(1, 8, 32, 20,
        List.of(0, 1, 2, 3), 1);

    final RoiMcrLocalFitter.WindowFit first = RoiMcrLocalFitter.fitWindow(window, rois,
        mcrOptions(), new RoiMcrLocalFitter.Options(2, 0.5), () -> false);
    final RoiMcrLocalFitter.WindowFit second = RoiMcrLocalFitter.fitWindow(window, rois,
        mcrOptions(), new RoiMcrLocalFitter.Options(2, 0.5), () -> false);

    assertEquals(List.of(1, 3), first.globalRoiIndices());
    assertEquals(first.globalRoiIndices(), second.globalRoiIndices());
    assertEquals(first.result().rank(), second.result().rank());
    assertEquals(first.originalExplained(), second.originalExplained(), 1e-12);
  }

  @Test
  void propagatesCancellation() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        trace(1, 100, 20, 8, 500), trace(2, 101, 20, 8, 400));
    final RoiMcrWindows.Window window = new RoiMcrWindows.Window(1, 8, 32, 20,
        List.of(0, 1), 1);
    assertThrows(CancellationException.class,
        () -> RoiMcrLocalFitter.fit(List.of(window), rois, mcrOptions(),
            new RoiMcrLocalFitter.Options(10, 0.5), () -> true));
  }

  private static RoiMcrCore.McrOptions mcrOptions() {
    return new RoiMcrCore.McrOptions(3, 4, 250, 40, 1e-7, 0.02, 0.70, 1, 0.05,
        0.001, 2, 0.001, 0.9995, 20260805L);
  }

  private static RoiMcrCore.RoiTrace trace(int id, double mz, int apex, int halfWidth,
      double height) {
    final int start = apex - halfWidth;
    final int end = apex + halfWidth;
    final int[] scans = new int[end - start + 1];
    final double[] mzs = new double[scans.length];
    final double[] intensities = new double[scans.length];
    for (int i = 0; i < scans.length; i++) {
      scans[i] = start + i;
      mzs[i] = mz;
      intensities[i] = height * Math.exp(-0.5 * Math.pow((scans[i] - apex) / 3.0, 2));
    }
    return new RoiMcrCore.RoiTrace(id, start, end, scans, mzs, intensities, mz, height, apex,
        scans.length);
  }
}
