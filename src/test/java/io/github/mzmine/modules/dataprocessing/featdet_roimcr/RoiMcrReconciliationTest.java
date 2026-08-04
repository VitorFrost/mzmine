/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoiMcrReconciliationTest {

  @Test
  void mergesSameComponentRecoveredFromOverlappingWindows() {
    final RoiMcrReconciliation.Candidate first = candidate(1, 1, 20, 60, 42,
        signature(0, 0.55, new double[]{100, 200, 300}, new double[]{1000, 600, 200}),
        0.86, 1.0, 0.91, 0.98);
    final RoiMcrReconciliation.Candidate second = candidate(2, 1, 35, 75, 43,
        signature(0, 0.20, new double[]{100.002, 200.002, 300.002},
            new double[]{980, 620, 190}), 0.92, 1.0, 0.94, 0.97);

    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(
        List.of(first, second), 4, 0.75, 0.01, 10);

    assertEquals(1, groups.size());
    assertTrue(groups.get(0).isReconciledDuplicate());
    assertEquals(second, groups.get(0).representative());
    assertTrue(groups.get(0).minimumRepresentativeSimilarity() > 0.85);
  }

  @Test
  void preservesDistinctSpectraAtSimilarRetentionTime() {
    final RoiMcrReconciliation.Candidate first = candidate(1, 1, 20, 60, 42,
        signature(0, 0.55, new double[]{100, 200}, new double[]{1000, 700}),
        0.9, 1, 0.9, 0.98);
    final RoiMcrReconciliation.Candidate second = candidate(2, 1, 35, 75, 43,
        signature(0, 0.20, new double[]{500, 600}, new double[]{900, 650}),
        0.9, 1, 0.9, 0.98);

    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(
        List.of(first, second), 4, 0.75, 0.01, 10);

    assertEquals(2, groups.size());
    groups.forEach(group -> assertFalse(group.isReconciledDuplicate()));
  }

  @Test
  void doesNotMergeSameSpectrumFromSeparatedChromatographicRegions() {
    final RoiMcrRobustness.Signature common = signature(0, 0.5,
        new double[]{100, 200, 300}, new double[]{1000, 600, 200});
    final RoiMcrReconciliation.Candidate first = candidate(1, 1, 10, 40, 25, common,
        0.9, 1, 0.9, 0.98);
    final RoiMcrReconciliation.Candidate second = candidate(2, 1, 70, 100, 85, common,
        0.9, 1, 0.9, 0.98);

    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(
        List.of(first, second), 5, 0.75, 0.01, 10);

    assertEquals(2, groups.size());
  }

  @Test
  void neverMergesComponentsFromTheSameWindow() {
    final RoiMcrRobustness.Signature common = signature(0, 0.5,
        new double[]{100, 200, 300}, new double[]{1000, 600, 200});
    final RoiMcrReconciliation.Candidate first = candidate(1, 1, 20, 60, 40, common,
        0.9, 1, 0.9, 0.98);
    final RoiMcrReconciliation.Candidate second = candidate(1, 2, 20, 60, 41, common,
        0.9, 1, 0.9, 0.98);

    assertEquals(2, RoiMcrReconciliation.reconcile(List.of(first, second), 5, 0.75, 0.01, 10)
        .size());
  }

  private static RoiMcrReconciliation.Candidate candidate(int window, int component, int start,
      int end, int apex, RoiMcrRobustness.Signature signature, double stability, double support,
      double restart, double explained) {
    return new RoiMcrReconciliation.Candidate(window, component, start, end, apex, signature,
        stability, support, restart, explained);
  }

  private static RoiMcrRobustness.Signature signature(int component, double apexFraction,
      double[] mz, double[] loadings) {
    final double[] profile = new double[101];
    final double center = apexFraction * 100;
    for (int index = 0; index < profile.length; index++) {
      profile[index] = Math.exp(-0.5 * Math.pow((index - center) / 8d, 2));
    }
    double maximum = 0;
    double sum = 0;
    for (double loading : loadings) {
      maximum = Math.max(maximum, loading);
      sum += loading;
    }
    double profileArea = 0;
    for (double value : profile) {
      profileArea += value;
    }
    return new RoiMcrRobustness.Signature(component, apexFraction, profile, mz, loadings, maximum,
        profileArea * sum);
  }
}
