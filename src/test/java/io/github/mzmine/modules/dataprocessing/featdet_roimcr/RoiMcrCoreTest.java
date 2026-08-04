/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class RoiMcrCoreTest {

  private static RoiMcrCore.McrOptions options() {
    return new RoiMcrCore.McrOptions(4, 6, 400, 60, 1e-7, 0.01, 0.7, 1, 0.05,
        0.001, 2, 0.0005, 0.9995, 42);
  }

  @Test
  void tracksLowResolutionMzDriftAndOneMissingScan() {
    final RoiMcrCore.RoiBuilder builder = new RoiMcrCore.RoiBuilder(
        new RoiMcrCore.RoiOptions(0.08, 0, 10, 50, 1, 4, 3, 100));
    for (int scan = 0; scan < 8; scan++) {
      final double drift = (scan - 4) * 0.01;
      if (scan == 3) {
        builder.acceptScan(scan, new double[]{150 - drift}, new double[]{300});
      } else {
        builder.acceptScan(scan, new double[]{100 + drift, 150 - drift},
            new double[]{120, scan >= 2 && scan <= 6 ? 300 : 5});
      }
    }
    final List<RoiMcrCore.RoiTrace> rois = builder.finish();
    assertEquals(2, rois.size());
    assertTrue(Math.abs(rois.get(0).meanMz() - 100) < 0.05);
    assertTrue(rois.get(0).maximumConsecutiveScans() >= 3);
  }

  @Test
  void selectsRankOneForOneChemicalProfile() {
    final double[][] matrix = new double[61][5];
    for (int scan = 0; scan < matrix.length; scan++) {
      final double profile = gaussian(scan, 30, 6);
      for (int ion = 0; ion < matrix[0].length; ion++) {
        matrix[scan][ion] = profile * (ion + 1) * 1000;
      }
    }
    final RoiMcrCore.McrResult result = RoiMcrCore.selectRank(matrix, options(), () -> false);
    assertEquals(1, result.rank());
    assertTrue(result.explainedVariance() > 0.995);
  }

  @Test
  void resolvesTwoPartiallyOverlappingSources() {
    final double[][] matrix = new double[91][7];
    final double[][] spectra = {
        {1000, 720, 430, 50, 0, 0, 0},
        {0, 0, 35, 900, 650, 410, 210}
    };
    for (int scan = 0; scan < matrix.length; scan++) {
      final double first = gaussian(scan, 37, 8);
      final double second = gaussian(scan, 49, 9);
      for (int ion = 0; ion < matrix[0].length; ion++) {
        matrix[scan][ion] = first * spectra[0][ion] + second * spectra[1][ion];
      }
    }
    final RoiMcrCore.McrResult result = RoiMcrCore.selectRank(matrix, options(), () -> false);
    assertEquals(2, result.rank());
    assertTrue(result.explainedVariance() > 0.98);
  }

  @Test
  void exactProportionalCoelutionRemainsRankOne() {
    final double[][] matrix = new double[61][6];
    final double[] combined = {1000, 700, 400, 900, 500, 250};
    for (int scan = 0; scan < matrix.length; scan++) {
      final double profile = gaussian(scan, 30, 7);
      for (int ion = 0; ion < combined.length; ion++) {
        matrix[scan][ion] = profile * combined[ion];
      }
    }
    assertEquals(1, RoiMcrCore.selectRank(matrix, options(), () -> false).rank());
  }

  @Test
  void cancellationIsCooperative() {
    assertThrows(CancellationException.class,
        () -> RoiMcrCore.selectRank(new double[][]{{1, 2}, {2, 1}}, options(), () -> true));
  }

  private static double gaussian(int scan, double center, double width) {
    return Math.exp(-0.5 * Math.pow((scan - center) / width, 2));
  }
}
