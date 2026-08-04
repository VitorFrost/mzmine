/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoiMcrMemorySafeBuilderTest {

  private static final RoiMcrCore.RoiOptions OPTIONS = new RoiMcrCore.RoiOptions(0.01, 10, 10,
      20, 1, 3, 2, 20);

  @Test
  void reproducesPrototypeBuilderWhenLimitIsNotReached() {
    final RoiMcrCore.RoiBuilder prototype = new RoiMcrCore.RoiBuilder(OPTIONS);
    final RoiMcrMemorySafeBuilder bounded = new RoiMcrMemorySafeBuilder(OPTIONS, 100);
    for (int scan = 0; scan < 12; scan++) {
      final double first = 100 + scan * 0.0005;
      final double second = 200 - scan * 0.0004;
      final double[] mz = scan == 6 ? new double[]{first} : new double[]{first, second};
      final double profile = 100 * Math.exp(-0.5 * Math.pow((scan - 5.5) / 2.2, 2));
      final double[] intensity = scan == 6 ? new double[]{profile}
          : new double[]{profile, 0.7 * profile};
      prototype.acceptScan(scan, mz, intensity);
      bounded.acceptScan(scan, mz, intensity);
    }

    final List<RoiMcrCore.RoiTrace> expected = prototype.finish().stream()
        .sorted(Comparator.comparingDouble(RoiMcrCore.RoiTrace::meanMz)).toList();
    final RoiMcrMemorySafeBuilder.Result result = bounded.finish();

    assertEquals(expected.size(), result.traces().size());
    for (int index = 0; index < expected.size(); index++) {
      final RoiMcrCore.RoiTrace left = expected.get(index);
      final RoiMcrCore.RoiTrace right = result.traces().get(index);
      assertEquals(left.meanMz(), right.meanMz(), 1e-12);
      assertEquals(left.height(), right.height(), 1e-12);
      assertEquals(left.apexScan(), right.apexScan());
      assertEquals(left.maximumConsecutiveScans(), right.maximumConsecutiveScans());
      assertArrayEquals(left.scanIndices(), right.scanIndices());
      assertArrayEquals(left.mzValues(), right.mzValues(), 1e-12);
      assertArrayEquals(left.intensities(), right.intensities(), 1e-12);
    }
    assertEquals(0, result.statistics().discardedByLimit());
    assertEquals(2, result.statistics().retainedRois());
  }

  @Test
  void retainsStrongPersistentRoisWhenLimitIsReached() {
    final RoiMcrMemorySafeBuilder builder = new RoiMcrMemorySafeBuilder(OPTIONS, 3);
    for (int scan = 0; scan < 8; scan++) {
      builder.acceptScan(scan,
          new double[]{100, 200, 300, 400, 500},
          new double[]{100, 80, 60, 40, 25});
    }
    final RoiMcrMemorySafeBuilder.Result result = builder.finish();

    assertEquals(3, result.traces().size());
    assertEquals(List.of(100d, 200d, 300d),
        result.traces().stream().map(RoiMcrCore.RoiTrace::meanMz).toList());
    assertEquals(2, result.statistics().discardedByLimit());
    assertEquals(5, result.statistics().completedCandidateRois());
    assertEquals(3, result.statistics().retainedRois());
  }

  @Test
  void storesLongTraceWithoutBoxedPointLists() {
    final RoiMcrMemorySafeBuilder builder = new RoiMcrMemorySafeBuilder(OPTIONS, 10);
    for (int scan = 0; scan < 10_000; scan++) {
      builder.acceptScan(scan, new double[]{250 + scan * 1e-8}, new double[]{100});
    }
    final RoiMcrMemorySafeBuilder.Result result = builder.finish();

    assertEquals(1, result.traces().size());
    assertEquals(10_000, result.traces().get(0).scanIndices().length);
    assertEquals(10_000, result.statistics().maximumPointsInOneRoi());
    assertTrue(result.statistics().maximumActiveRois() <= 1);
  }
}
