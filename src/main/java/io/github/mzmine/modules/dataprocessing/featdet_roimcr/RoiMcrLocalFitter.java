/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Fits bounded MCR models independently in local chromatographic windows. */
final class RoiMcrLocalFitter {

  private RoiMcrLocalFitter() {
  }

  record Options(int maximumRoisPerWindow, double weightingExponent) {

    Options {
      if (maximumRoisPerWindow < 2 || weightingExponent < 0 || weightingExponent > 1) {
        throw new IllegalArgumentException("Invalid local ROI-MCR fitting options");
      }
    }
  }

  record WindowFit(int windowId, int startScan, int endScan, int inputRois, int selectedRois,
                   List<Integer> globalRoiIndices, RoiMcrCore.McrResult result,
                   double[][] restoredSpectra, double originalExplained,
                   List<RoiMcrRobustness.Signature> signatures, long elapsedNanos) {

    WindowFit {
      globalRoiIndices = List.copyOf(globalRoiIndices);
      signatures = List.copyOf(signatures);
      if (windowId < 1 || startScan < 0 || endScan < startScan || inputRois < selectedRois
          || selectedRois < 2 || globalRoiIndices.size() != selectedRois || result == null
          || result.rank() != signatures.size() || restoredSpectra.length != result.rank()
          || originalExplained < 0 || originalExplained > 1 || elapsedNanos < 0) {
        throw new IllegalArgumentException("Invalid local ROI-MCR fit result");
      }
    }

    int scanCount() {
      return endScan - startScan + 1;
    }
  }

  record Summary(List<WindowFit> fits, int failedWindows, int totalInputRois,
                 int totalSelectedRois, int maximumInputRois, int maximumSelectedRois,
                 long elapsedNanos) {

    Summary {
      fits = List.copyOf(fits);
      if (failedWindows < 0 || totalInputRois < totalSelectedRois || maximumInputRois < 0
          || maximumSelectedRois < 0 || elapsedNanos < 0) {
        throw new IllegalArgumentException("Invalid local ROI-MCR fitting summary");
      }
    }
  }

  static Summary fit(List<RoiMcrWindows.Window> windows, List<RoiMcrCore.RoiTrace> rois,
      RoiMcrCore.McrOptions mcrOptions, Options options, BooleanSupplier cancelled) {
    final long started = System.nanoTime();
    final List<WindowFit> fits = new ArrayList<>();
    int failed = 0;
    int totalInput = 0;
    int totalSelected = 0;
    int maximumInput = 0;
    int maximumSelected = 0;

    for (RoiMcrWindows.Window window : windows) {
      if (cancelled.getAsBoolean()) {
        throw new java.util.concurrent.CancellationException("Local ROI-MCR fitting cancelled");
      }
      totalInput += window.roiIndices().size();
      maximumInput = Math.max(maximumInput, window.roiIndices().size());
      try {
        final WindowFit fit = fitWindow(window, rois, mcrOptions, options, cancelled);
        fits.add(fit);
        totalSelected += fit.selectedRois();
        maximumSelected = Math.max(maximumSelected, fit.selectedRois());
      } catch (java.util.concurrent.CancellationException cancelledFit) {
        throw cancelledFit;
      } catch (RuntimeException failedFit) {
        failed++;
      }
    }
    return new Summary(fits, failed, totalInput, totalSelected, maximumInput, maximumSelected,
        System.nanoTime() - started);
  }

  static WindowFit fitWindow(RoiMcrWindows.Window window, List<RoiMcrCore.RoiTrace> rois,
      RoiMcrCore.McrOptions mcrOptions, Options options, BooleanSupplier cancelled) {
    final long started = System.nanoTime();
    final List<Integer> selected = selectRois(window, rois, options.maximumRoisPerWindow());
    if (selected.size() < 2) {
      throw new IllegalArgumentException("Local window contains fewer than two selected ROIs");
    }
    final int scans = window.scanCount();
    final double[][] original = new double[scans][selected.size()];
    final List<RoiMcrCore.RoiTrace> localRois = new ArrayList<>(selected.size());
    for (int local = 0; local < selected.size(); local++) {
      final RoiMcrCore.RoiTrace roi = rois.get(selected.get(local));
      localRois.add(shiftToWindow(roi, window.startScan(), window.endScan()));
      for (int scan = window.startScan(); scan <= window.endScan(); scan++) {
        original[scan - window.startScan()][local] = roi.intensityAt(scan);
      }
    }

    final double[] scales = RoiMcrCore.columnMaxima(original);
    final double[][] weighted = RoiMcrCore.weightedCopy(original, options.weightingExponent(),
        scales);
    final RoiMcrCore.McrResult result = RoiMcrCore.selectRank(weighted, mcrOptions, cancelled);
    if (result == null || result.rank() < 1) {
      throw new IllegalStateException("No accepted local MCR component model");
    }
    final double[][] restored = RoiMcrCore.restoreSpectralScale(result.spectra(), scales);
    final double energy = squaredNorm(original);
    final double rss = RoiMcrCore.rss(original, result.concentrations(), restored);
    final double explained = Math.max(0,
        Math.min(1, 1 - rss / Math.max(energy, RoiMcrCore.EPS)));
    final List<RoiMcrRobustness.Signature> signatures = RoiMcrRobustness.signatures(result,
        restored, localRois);
    return new WindowFit(window.id(), window.startScan(), window.endScan(),
        window.roiIndices().size(), selected.size(), selected, result, restored, explained,
        signatures, System.nanoTime() - started);
  }

  private static List<Integer> selectRois(RoiMcrWindows.Window window,
      List<RoiMcrCore.RoiTrace> rois, int limit) {
    final List<Integer> ordered = new ArrayList<>(window.roiIndices());
    ordered.sort(Comparator
        .comparingDouble((Integer index) -> quality(rois.get(index))).reversed()
        .thenComparingDouble(index -> rois.get(index).meanMz())
        .thenComparingInt(index -> rois.get(index).id()));
    if (ordered.size() > limit) {
      ordered.subList(limit, ordered.size()).clear();
    }
    // The matrix order must be deterministic and independent of priority-queue ordering.
    ordered.sort(Comparator.comparingDouble((Integer index) -> rois.get(index).meanMz())
        .thenComparingInt(index -> rois.get(index).id()));
    return List.copyOf(ordered);
  }

  private static double quality(RoiMcrCore.RoiTrace roi) {
    return roi.height() * Math.sqrt(Math.max(1, roi.maximumConsecutiveScans()));
  }

  private static RoiMcrCore.RoiTrace shiftToWindow(RoiMcrCore.RoiTrace roi, int start, int end) {
    final List<Integer> indices = new ArrayList<>();
    final List<Double> mzs = new ArrayList<>();
    final List<Double> intensities = new ArrayList<>();
    double height = 0;
    int apex = 0;
    for (int i = 0; i < roi.scanIndices().length; i++) {
      final int global = roi.scanIndices()[i];
      if (global < start || global > end) {
        continue;
      }
      indices.add(global - start);
      mzs.add(roi.mzValues()[i]);
      intensities.add(roi.intensities()[i]);
      if (roi.intensities()[i] > height) {
        height = roi.intensities()[i];
        apex = global - start;
      }
    }
    if (indices.isEmpty()) {
      // A broad ROI can overlap by its active interval while containing no stored centroid exactly
      // inside a padded boundary. Preserve one zero-free representative at the nearest boundary.
      final int local = Math.max(0, Math.min(end - start, roi.apexScan() - start));
      indices.add(local);
      mzs.add(roi.meanMz());
      intensities.add(roi.height());
      height = roi.height();
      apex = local;
    }
    final int[] scanArray = new int[indices.size()];
    final double[] mzArray = new double[indices.size()];
    final double[] intensityArray = new double[indices.size()];
    for (int i = 0; i < indices.size(); i++) {
      scanArray[i] = indices.get(i);
      mzArray[i] = mzs.get(i);
      intensityArray[i] = intensities.get(i);
    }
    return new RoiMcrCore.RoiTrace(roi.id(), scanArray[0], scanArray[scanArray.length - 1],
        scanArray, mzArray, intensityArray, roi.meanMz(), height, apex,
        maximumConsecutive(scanArray));
  }

  private static int maximumConsecutive(int[] scans) {
    int best = scans.length == 0 ? 0 : 1;
    int current = best;
    for (int i = 1; i < scans.length; i++) {
      current = scans[i] == scans[i - 1] + 1 ? current + 1 : 1;
      best = Math.max(best, current);
    }
    return best;
  }

  private static double squaredNorm(double[][] matrix) {
    double sum = 0;
    for (double[] row : matrix) {
      for (double value : row) {
        sum += value * value;
      }
    }
    return sum;
  }
}
