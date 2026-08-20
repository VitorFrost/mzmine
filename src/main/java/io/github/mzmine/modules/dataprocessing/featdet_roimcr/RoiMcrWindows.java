/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds bounded local chromatographic matrices from ROI activity intervals. */
final class RoiMcrWindows {

  private RoiMcrWindows() {
  }

  record Options(int maximumGapScans, int paddingScans, int minimumWindowScans,
                 int maximumWindowScans, int minimumRois, double activeFraction) {

    Options {
      if (maximumGapScans < 0 || paddingScans < 0 || minimumWindowScans < 2
          || maximumWindowScans < minimumWindowScans || minimumRois < 1
          || activeFraction <= 0 || activeFraction >= 1) {
        throw new IllegalArgumentException("Invalid ROI-MCR window options");
      }
    }
  }

  record Window(int id, int startScan, int endScan, int apexScan, List<Integer> roiIndices,
                double apexSignal) {

    Window {
      roiIndices = List.copyOf(roiIndices);
      if (id < 1 || startScan < 0 || endScan < startScan || apexScan < startScan
          || apexScan > endScan || roiIndices.isEmpty() || apexSignal < 0) {
        throw new IllegalArgumentException("Invalid ROI-MCR chromatographic window");
      }
    }

    int scanCount() {
      return endScan - startScan + 1;
    }
  }

  static List<Window> build(int numberOfScans, List<RoiMcrCore.RoiTrace> rois,
      Options options) {
    if (numberOfScans < 2 || rois.isEmpty()) {
      return List.of();
    }
    final List<Interval> intervals = new ArrayList<>(rois.size());
    final List<Interval> anchors = new ArrayList<>();
    for (int roiIndex = 0; roiIndex < rois.size(); roiIndex++) {
      final Interval interval = activeInterval(roiIndex, rois.get(roiIndex), options.activeFraction());
      intervals.add(interval);
      if (interval.width() <= options.maximumWindowScans()) {
        anchors.add(interval);
      }
    }
    anchors.sort(Comparator.comparingInt(Interval::start).thenComparingInt(Interval::end)
        .thenComparingInt(Interval::roiIndex));

    final List<Cluster> clusters = new ArrayList<>();
    Cluster current = null;
    for (Interval anchor : anchors) {
      if (current == null) {
        current = new Cluster(anchor);
        continue;
      }
      final int unionEnd = Math.max(current.end, anchor.end());
      final boolean close = anchor.start() <= current.end + options.maximumGapScans() + 1;
      final boolean bounded = unionEnd - current.start + 1 <= options.maximumWindowScans();
      if (close && bounded) {
        current.add(anchor);
      } else {
        clusters.add(current);
        current = new Cluster(anchor);
      }
    }
    if (current != null) {
      clusters.add(current);
    }

    // If all surviving ROIs are extremely broad, create bounded apex-centered candidates rather
    // than silently reverting to one whole-chromatogram matrix.
    if (clusters.isEmpty()) {
      for (Interval interval : intervals) {
        final int half = options.maximumWindowScans() / 2;
        final int start = Math.max(0, interval.apex() - half);
        final int end = Math.min(numberOfScans - 1,
            start + options.maximumWindowScans() - 1);
        clusters.add(new Cluster(start, end, interval.roiIndex()));
      }
    }

    final List<Window> candidates = new ArrayList<>();
    for (Cluster cluster : clusters) {
      final int preliminaryStart = Math.max(0, cluster.start - options.paddingScans());
      final int preliminaryEnd = Math.min(numberOfScans - 1,
          cluster.end + options.paddingScans());
      final Set<Integer> assigned = new LinkedHashSet<>();
      for (Interval interval : intervals) {
        if (overlaps(preliminaryStart, preliminaryEnd, interval.start(), interval.end())) {
          assigned.add(interval.roiIndex());
        }
      }
      if (assigned.size() < options.minimumRois()) {
        continue;
      }
      final Apex apex = apex(preliminaryStart, preliminaryEnd, assigned, rois);
      final Bounds bounds = bounded(preliminaryStart, preliminaryEnd, apex.scan(), numberOfScans,
          options.minimumWindowScans(), options.maximumWindowScans());
      candidates.add(new Window(1, bounds.start(), bounds.end(), apex.scan(),
          assigned.stream().sorted().toList(), apex.signal()));
    }

    candidates.sort(Comparator.comparingInt(Window::startScan).thenComparingInt(Window::apexScan));
    final List<Window> deduplicated = new ArrayList<>();
    for (Window candidate : candidates) {
      if (!deduplicated.isEmpty()) {
        final Window previous = deduplicated.get(deduplicated.size() - 1);
        if (nearlyDuplicate(previous, candidate)) {
          deduplicated.set(deduplicated.size() - 1,
              stronger(previous, candidate, numberOfScans, rois, options));
          continue;
        }
      }
      deduplicated.add(candidate);
    }

    final List<Window> result = new ArrayList<>(deduplicated.size());
    for (int index = 0; index < deduplicated.size(); index++) {
      final Window window = deduplicated.get(index);
      result.add(new Window(index + 1, window.startScan(), window.endScan(), window.apexScan(),
          window.roiIndices(), window.apexSignal()));
    }
    return List.copyOf(result);
  }

  private static Window stronger(Window first, Window second, int numberOfScans,
      List<RoiMcrCore.RoiTrace> rois, Options options) {
    final Set<Integer> combined = new LinkedHashSet<>(first.roiIndices());
    combined.addAll(second.roiIndices());
    final int preliminaryStart = Math.min(first.startScan(), second.startScan());
    final int preliminaryEnd = Math.max(first.endScan(), second.endScan());
    final Apex apex = apex(preliminaryStart, preliminaryEnd, combined, rois);
    final Bounds bounds = bounded(preliminaryStart, preliminaryEnd, apex.scan(), numberOfScans,
        options.minimumWindowScans(), options.maximumWindowScans());
    return new Window(1, bounds.start(), bounds.end(), apex.scan(),
        combined.stream().sorted().toList(), apex.signal());
  }

  private static boolean nearlyDuplicate(Window first, Window second) {
    final int overlap = Math.max(0,
        Math.min(first.endScan(), second.endScan()) - Math.max(first.startScan(), second.startScan())
            + 1);
    final int shorter = Math.min(first.scanCount(), second.scanCount());
    if (overlap / (double) shorter < 0.80) {
      return false;
    }
    int shared = 0;
    for (int roi : first.roiIndices()) {
      if (second.roiIndices().contains(roi)) {
        shared++;
      }
    }
    return shared / (double) Math.min(first.roiIndices().size(), second.roiIndices().size()) >= 0.80;
  }

  private static Interval activeInterval(int roiIndex, RoiMcrCore.RoiTrace roi,
      double activeFraction) {
    final double threshold = roi.height() * activeFraction;
    int start = roi.apexScan();
    int end = roi.apexScan();
    for (int index = 0; index < roi.scanIndices().length; index++) {
      if (roi.intensities()[index] >= threshold) {
        start = Math.min(start, roi.scanIndices()[index]);
        end = Math.max(end, roi.scanIndices()[index]);
      }
    }
    return new Interval(roiIndex, start, end, roi.apexScan());
  }

  private static Apex apex(int start, int end, Set<Integer> roiIndices,
      List<RoiMcrCore.RoiTrace> rois) {
    int bestScan = start;
    double bestSignal = -1;
    for (int scan = start; scan <= end; scan++) {
      double signal = 0;
      for (int roiIndex : roiIndices) {
        // Square-root compression prevents one very intense ROI from fully determining the window
        // apex while preserving the ordering of positive signals.
        signal += Math.sqrt(Math.max(0, rois.get(roiIndex).intensityAt(scan)));
      }
      if (signal > bestSignal) {
        bestSignal = signal;
        bestScan = scan;
      }
    }
    return new Apex(bestScan, Math.max(0, bestSignal));
  }

  private static Bounds bounded(int requestedStart, int requestedEnd, int apex,
      int numberOfScans, int minimumScans, int maximumScans) {
    int start = Math.max(0, requestedStart);
    int end = Math.min(numberOfScans - 1, requestedEnd);
    if (end - start + 1 > maximumScans) {
      start = Math.max(0, apex - maximumScans / 2);
      end = Math.min(numberOfScans - 1, start + maximumScans - 1);
      start = Math.max(0, end - maximumScans + 1);
    }
    if (end - start + 1 < minimumScans) {
      final int missing = minimumScans - (end - start + 1);
      start = Math.max(0, start - (missing + 1) / 2);
      end = Math.min(numberOfScans - 1, end + missing / 2);
      if (end - start + 1 < minimumScans) {
        start = Math.max(0, end - minimumScans + 1);
        end = Math.min(numberOfScans - 1, start + minimumScans - 1);
      }
    }
    return new Bounds(start, end);
  }

  private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
    return firstStart <= secondEnd && secondStart <= firstEnd;
  }

  private record Interval(int roiIndex, int start, int end, int apex) {

    int width() {
      return end - start + 1;
    }
  }

  private record Bounds(int start, int end) {
  }

  private record Apex(int scan, double signal) {
  }

  private static final class Cluster {

    private int start;
    private int end;
    private final Set<Integer> roiIndices = new LinkedHashSet<>();

    Cluster(Interval interval) {
      this(interval.start(), interval.end(), interval.roiIndex());
    }

    Cluster(int start, int end, int roiIndex) {
      this.start = start;
      this.end = end;
      roiIndices.add(roiIndex);
    }

    void add(Interval interval) {
      start = Math.min(start, interval.start());
      end = Math.max(end, interval.end());
      roiIndices.add(interval.roiIndex());
    }
  }
}
