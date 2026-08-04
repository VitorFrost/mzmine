/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoiMcrWindowsTest {

  private static final RoiMcrWindows.Options OPTIONS = new RoiMcrWindows.Options(2, 3, 10, 30,
      2, 0.10);

  @Test
  void separatesTwoRegionsDespiteOneBroadBackgroundRoi() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        gaussianRoi(1, 18, 33, 25, 3, 1_000),
        gaussianRoi(2, 20, 35, 27, 3, 700),
        gaussianRoi(3, 72, 88, 80, 3, 900),
        gaussianRoi(4, 74, 91, 82, 3, 650),
        broadRoi(5, 8, 105, 55, 120));

    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(120, rois, OPTIONS);

    assertEquals(2, windows.size());
    assertTrue(windows.get(0).apexScan() < 40);
    assertTrue(windows.get(1).apexScan() > 65);
    assertTrue(windows.get(0).roiIndices().contains(4));
    assertTrue(windows.get(1).roiIndices().contains(4));
    windows.forEach(window -> {
      assertTrue(window.scanCount() >= OPTIONS.minimumWindowScans());
      assertTrue(window.scanCount() <= OPTIONS.maximumWindowScans());
    });
  }

  @Test
  void mergesNearbyCoelutingIntervals() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        gaussianRoi(1, 20, 29, 25, 2, 1_000),
        gaussianRoi(2, 30, 39, 34, 2, 800),
        gaussianRoi(3, 23, 37, 30, 4, 500));

    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(80, rois, OPTIONS);

    assertEquals(1, windows.size());
    assertEquals(3, windows.get(0).roiIndices().size());
    assertTrue(windows.get(0).startScan() <= 20);
    assertTrue(windows.get(0).endScan() >= 39);
  }

  @Test
  void boundsFallbackWindowsWhenOnlyBroadRoisRemain() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        broadRoi(1, 0, 99, 48, 200),
        broadRoi(2, 2, 98, 51, 160));

    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(100, rois, OPTIONS);

    assertEquals(1, windows.size());
    assertEquals(30, windows.get(0).scanCount());
    assertTrue(windows.get(0).apexScan() >= 45 && windows.get(0).apexScan() <= 55);
    assertEquals(List.of(0, 1), windows.get(0).roiIndices());
  }

  @Test
  void rejectsRegionsWithTooFewRois() {
    final List<RoiMcrCore.RoiTrace> rois = List.of(
        gaussianRoi(1, 20, 35, 27, 3, 1_000));

    assertTrue(RoiMcrWindows.build(80, rois, OPTIONS).isEmpty());
  }

  private static RoiMcrCore.RoiTrace gaussianRoi(int id, int start, int end, int apex,
      double width, double height) {
    final int length = end - start + 1;
    final int[] scans = new int[length];
    final double[] mzs = new double[length];
    final double[] intensities = new double[length];
    for (int index = 0; index < length; index++) {
      final int scan = start + index;
      scans[index] = scan;
      mzs[index] = 100 + id * 10;
      intensities[index] = height * Math.exp(-0.5 * Math.pow((scan - apex) / width, 2));
    }
    return new RoiMcrCore.RoiTrace(id, start, end, scans, mzs, intensities, 100 + id * 10,
        height, apex, length);
  }

  private static RoiMcrCore.RoiTrace broadRoi(int id, int start, int end, int apex,
      double height) {
    final int length = end - start + 1;
    final int[] scans = new int[length];
    final double[] mzs = new double[length];
    final double[] intensities = new double[length];
    for (int index = 0; index < length; index++) {
      final int scan = start + index;
      scans[index] = scan;
      mzs[index] = 500 + id;
      intensities[index] = scan == apex ? height : height * 0.5;
    }
    return new RoiMcrCore.RoiTrace(id, start, end, scans, mzs, intensities, 500 + id, height,
        apex, length);
  }
}
