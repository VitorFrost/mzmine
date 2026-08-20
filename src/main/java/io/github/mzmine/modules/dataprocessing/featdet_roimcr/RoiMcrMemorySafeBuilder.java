/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Memory-bounded ROI tracker for long chromatographic selections.
 *
 * <p>The original prototype builder stores every point as boxed {@link Integer}/{@link Double}
 * objects and retains every valid ROI. That is acceptable for local windows but can exhaust the
 * test-worker heap on a full LC-MS run. This implementation stores points in primitive arrays and
 * retains only the highest-quality completed ROIs when a configured limit is reached.</p>
 */
final class RoiMcrMemorySafeBuilder {

  private static final Comparator<ScoredTrace> LOWEST_QUALITY_FIRST =
      Comparator.comparingDouble(ScoredTrace::quality)
          .thenComparingInt(trace -> -trace.trace().id());

  record Statistics(long inputPoints, long pointsAboveNoise, int startedRois,
                    int completedCandidateRois, int retainedRois, int discardedByLimit,
                    int maximumActiveRois, int maximumPointsInOneRoi) {
  }

  record Result(List<RoiMcrCore.RoiTrace> traces, Statistics statistics) {

    Result {
      traces = List.copyOf(traces);
    }
  }

  private final RoiMcrCore.RoiOptions options;
  private final int maximumRetainedRois;
  private final List<MutableRoi> active = new ArrayList<>();
  private final PriorityQueue<ScoredTrace> retained;
  private int nextId = 1;
  private int lastScan = -1;
  private long inputPoints;
  private long pointsAboveNoise;
  private int startedRois;
  private int completedCandidateRois;
  private int discardedByLimit;
  private int maximumActiveRois;
  private int maximumPointsInOneRoi;

  RoiMcrMemorySafeBuilder(RoiMcrCore.RoiOptions options, int maximumRetainedRois) {
    if (options == null || maximumRetainedRois < 1) {
      throw new IllegalArgumentException("ROI options and a positive retention limit are required");
    }
    this.options = options;
    this.maximumRetainedRois = maximumRetainedRois;
    retained = new PriorityQueue<>(Math.min(maximumRetainedRois, 1024), LOWEST_QUALITY_FIRST);
  }

  void acceptScan(int scanIndex, double[] mzValues, double[] intensities) {
    if (scanIndex <= lastScan || mzValues.length != intensities.length) {
      throw new IllegalArgumentException("Scans must be ordered and arrays must match");
    }
    lastScan = scanIndex;
    inputPoints += mzValues.length;

    final double[] referenceMz = new double[active.size()];
    for (int index = 0; index < active.size(); index++) {
      referenceMz[index] = active.get(index).meanMz();
      active.get(index).assigned = false;
    }

    final int[] order = usablePoints(mzValues, intensities);
    sortByDescendingIntensity(order, intensities);
    for (int point : order) {
      final double mz = mzValues[point];
      final double intensity = intensities[point];
      int best = -1;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int index = 0; index < active.size(); index++) {
        final MutableRoi roi = active.get(index);
        if (roi.assigned
            || scanIndex - roi.lastScan > options.maximumMissingScans() + 1) {
          continue;
        }
        final double distance = Math.abs(mz - referenceMz[index]);
        if (distance <= options.toleranceFor(referenceMz[index]) && distance < bestDistance) {
          best = index;
          bestDistance = distance;
        }
      }
      if (best >= 0) {
        active.get(best).add(scanIndex, mz, intensity);
      } else if (intensity >= options.seedIntensity()) {
        final MutableRoi roi = new MutableRoi(nextId++);
        roi.add(scanIndex, mz, intensity);
        active.add(roi);
        startedRois++;
      }
    }

    for (int index = active.size() - 1; index >= 0; index--) {
      if (scanIndex - active.get(index).lastScan > options.maximumMissingScans()) {
        complete(active.remove(index));
      }
    }
    maximumActiveRois = Math.max(maximumActiveRois, active.size());
  }

  Result finish() {
    for (MutableRoi roi : active) {
      complete(roi);
    }
    active.clear();
    final List<RoiMcrCore.RoiTrace> traces = retained.stream().map(ScoredTrace::trace)
        .sorted(Comparator.comparingDouble(RoiMcrCore.RoiTrace::meanMz)
            .thenComparingInt(RoiMcrCore.RoiTrace::id)).toList();
    return new Result(traces,
        new Statistics(inputPoints, pointsAboveNoise, startedRois, completedCandidateRois,
            traces.size(), discardedByLimit, maximumActiveRois, maximumPointsInOneRoi));
  }

  private int[] usablePoints(double[] mzValues, double[] intensities) {
    int count = 0;
    for (int index = 0; index < mzValues.length; index++) {
      if (Double.isFinite(mzValues[index]) && Double.isFinite(intensities[index])
          && intensities[index] >= options.noiseLevel()) {
        count++;
      }
    }
    pointsAboveNoise += count;
    final int[] indices = new int[count];
    int target = 0;
    for (int index = 0; index < mzValues.length; index++) {
      if (Double.isFinite(mzValues[index]) && Double.isFinite(intensities[index])
          && intensities[index] >= options.noiseLevel()) {
        indices[target++] = index;
      }
    }
    return indices;
  }

  private void complete(MutableRoi roi) {
    maximumPointsInOneRoi = Math.max(maximumPointsInOneRoi, roi.size);
    if (roi.size < options.minimumDataPoints() || roi.height < options.minimumHeight()
        || roi.maximumConsecutive < options.minimumConsecutiveScans()) {
      return;
    }
    completedCandidateRois++;
    final RoiMcrCore.RoiTrace trace = roi.freeze();
    // Height preserves sensitivity to strong minor species; sqrt(persistence) rewards coherent
    // chromatographic evidence without allowing broad backgrounds to dominate linearly by length.
    final double quality = trace.height() * Math.sqrt(trace.maximumConsecutiveScans());
    final ScoredTrace candidate = new ScoredTrace(trace, quality);
    if (retained.size() < maximumRetainedRois) {
      retained.add(candidate);
      return;
    }
    final ScoredTrace weakest = retained.peek();
    if (LOWEST_QUALITY_FIRST.compare(candidate, weakest) > 0) {
      retained.poll();
      retained.add(candidate);
    }
    discardedByLimit++;
  }

  private static void sortByDescendingIntensity(int[] indices, double[] intensities) {
    if (indices.length > 1) {
      quickSort(indices, intensities, 0, indices.length - 1);
    }
  }

  private static void quickSort(int[] indices, double[] intensities, int low, int high) {
    int left = low;
    int right = high;
    final double pivot = intensities[indices[(low + high) >>> 1]];
    while (left <= right) {
      while (compare(indices[left], pivot, intensities) < 0) {
        left++;
      }
      while (compare(indices[right], pivot, intensities) > 0) {
        right--;
      }
      if (left <= right) {
        final int temporary = indices[left];
        indices[left] = indices[right];
        indices[right] = temporary;
        left++;
        right--;
      }
    }
    if (low < right) {
      quickSort(indices, intensities, low, right);
    }
    if (left < high) {
      quickSort(indices, intensities, left, high);
    }
  }

  private static int compare(int point, double pivotIntensity, double[] intensities) {
    return Double.compare(pivotIntensity, intensities[point]);
  }

  private record ScoredTrace(RoiMcrCore.RoiTrace trace, double quality) {
  }

  private static final class MutableRoi {

    private final int id;
    private int[] scans = new int[16];
    private double[] mzs = new double[16];
    private double[] intensities = new double[16];
    private int size;
    private double weightedMz;
    private double intensitySum;
    private double height;
    private int apexScan;
    private int lastScan = Integer.MIN_VALUE;
    private int currentConsecutive;
    private int maximumConsecutive;
    private boolean assigned;

    MutableRoi(int id) {
      this.id = id;
    }

    void add(int scan, double mz, double intensity) {
      ensureCapacity(size + 1);
      scans[size] = scan;
      mzs[size] = mz;
      intensities[size] = intensity;
      if (size == 0) {
        currentConsecutive = 1;
      } else {
        currentConsecutive = scan == scans[size - 1] + 1 ? currentConsecutive + 1 : 1;
      }
      maximumConsecutive = Math.max(maximumConsecutive, currentConsecutive);
      size++;
      weightedMz += mz * intensity;
      intensitySum += intensity;
      if (intensity > height) {
        height = intensity;
        apexScan = scan;
      }
      lastScan = scan;
      assigned = true;
    }

    double meanMz() {
      return intensitySum > 0 ? weightedMz / intensitySum : mzs[size - 1];
    }

    RoiMcrCore.RoiTrace freeze() {
      final int[] scanArray = Arrays.copyOf(scans, size);
      final double[] mzArray = Arrays.copyOf(mzs, size);
      final double[] intensityArray = Arrays.copyOf(intensities, size);
      return new RoiMcrCore.RoiTrace(id, scanArray[0], scanArray[size - 1], scanArray, mzArray,
          intensityArray, meanMz(), height, apexScan, maximumConsecutive);
    }

    private void ensureCapacity(int required) {
      if (required <= scans.length) {
        return;
      }
      final int capacity = Math.max(required, scans.length + (scans.length >>> 1));
      scans = Arrays.copyOf(scans, capacity);
      mzs = Arrays.copyOf(mzs, capacity);
      intensities = Arrays.copyOf(intensities, capacity);
    }
  }
}
