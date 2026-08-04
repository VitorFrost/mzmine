/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reconciles duplicate chemical components recovered from overlapping local MCR windows. */
final class RoiMcrReconciliation {

  private RoiMcrReconciliation() {
  }

  record Candidate(int windowId, int componentId, int startScan, int endScan, int apexScan,
                   RoiMcrRobustness.Signature signature, double perturbationStability,
                   double perturbationSupport, double restartStability,
                   double originalExplained) {

    Candidate {
      if (windowId < 1 || componentId < 1 || startScan < 0 || endScan < startScan
          || apexScan < startScan || apexScan > endScan || signature == null
          || perturbationStability < 0 || perturbationStability > 1
          || perturbationSupport < 0 || perturbationSupport > 1
          || restartStability < 0 || restartStability > 1
          || originalExplained < 0 || originalExplained > 1) {
        throw new IllegalArgumentException("Invalid ROI-MCR reconciliation candidate");
      }
    }

    double quality() {
      return 0.35 * perturbationStability + 0.25 * perturbationSupport
          + 0.20 * restartStability + 0.20 * originalExplained;
    }
  }

  record Group(int id, Candidate representative, List<Candidate> members,
               double minimumRepresentativeSimilarity) {

    Group {
      members = List.copyOf(members);
      if (id < 1 || representative == null || members.isEmpty()
          || minimumRepresentativeSimilarity < 0 || minimumRepresentativeSimilarity > 1) {
        throw new IllegalArgumentException("Invalid ROI-MCR reconciliation group");
      }
    }

    boolean isReconciledDuplicate() {
      return members.size() > 1;
    }
  }

  static List<Group> reconcile(List<Candidate> candidates, int maximumApexDistanceScans,
      double minimumComponentSimilarity, double absoluteMzTolerance, double ppmTolerance) {
    if (maximumApexDistanceScans < 0 || minimumComponentSimilarity < 0
        || minimumComponentSimilarity > 1 || absoluteMzTolerance < 0 || ppmTolerance < 0) {
      throw new IllegalArgumentException("Invalid ROI-MCR reconciliation options");
    }
    if (candidates.isEmpty()) {
      return List.of();
    }

    final List<Candidate> ordered = candidates.stream()
        .sorted(Comparator.comparingInt(Candidate::apexScan)
            .thenComparingInt(Candidate::windowId).thenComparingInt(Candidate::componentId))
        .toList();
    final UnionFind union = new UnionFind(ordered.size());
    for (int first = 0; first < ordered.size(); first++) {
      for (int second = first + 1; second < ordered.size(); second++) {
        final Candidate left = ordered.get(first);
        final Candidate right = ordered.get(second);
        if (right.apexScan() - left.apexScan() > maximumApexDistanceScans) {
          break;
        }
        if (left.windowId() == right.windowId()
            || !overlaps(left.startScan(), left.endScan(), right.startScan(), right.endScan())) {
          continue;
        }
        final double similarity = RoiMcrRobustness.componentSimilarity(left.signature(),
            right.signature(), absoluteMzTolerance, ppmTolerance);
        if (similarity >= minimumComponentSimilarity) {
          union.join(first, second);
        }
      }
    }

    final Map<Integer, List<Candidate>> grouped = new LinkedHashMap<>();
    for (int index = 0; index < ordered.size(); index++) {
      grouped.computeIfAbsent(union.root(index), ignored -> new ArrayList<>()).add(ordered.get(index));
    }

    final List<UnnumberedGroup> unnumbered = new ArrayList<>();
    for (List<Candidate> members : grouped.values()) {
      final Candidate representative = members.stream().max(
          Comparator.comparingDouble(Candidate::quality)
              .thenComparingInt(candidate -> -candidate.windowId())
              .thenComparingInt(candidate -> -candidate.componentId())).orElseThrow();
      double minimumSimilarity = 1;
      for (Candidate member : members) {
        minimumSimilarity = Math.min(minimumSimilarity,
            RoiMcrRobustness.componentSimilarity(representative.signature(), member.signature(),
                absoluteMzTolerance, ppmTolerance));
      }
      members.sort(Comparator.comparingInt(Candidate::windowId)
          .thenComparingInt(Candidate::componentId));
      unnumbered.add(new UnnumberedGroup(representative, List.copyOf(members), minimumSimilarity));
    }
    unnumbered.sort(Comparator.comparingInt(group -> group.representative().apexScan()));

    final List<Group> result = new ArrayList<>(unnumbered.size());
    for (int index = 0; index < unnumbered.size(); index++) {
      final UnnumberedGroup group = unnumbered.get(index);
      result.add(new Group(index + 1, group.representative(), group.members(),
          group.minimumRepresentativeSimilarity()));
    }
    return List.copyOf(result);
  }

  private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
    return firstStart <= secondEnd && secondStart <= firstEnd;
  }

  private record UnnumberedGroup(Candidate representative, List<Candidate> members,
                                double minimumRepresentativeSimilarity) {
  }

  private static final class UnionFind {

    private final int[] parent;
    private final byte[] rank;

    UnionFind(int size) {
      parent = new int[size];
      rank = new byte[size];
      for (int index = 0; index < size; index++) {
        parent[index] = index;
      }
    }

    int root(int index) {
      int current = index;
      while (parent[current] != current) {
        current = parent[current];
      }
      while (parent[index] != index) {
        final int next = parent[index];
        parent[index] = current;
        index = next;
      }
      return current;
    }

    void join(int first, int second) {
      int left = root(first);
      int right = root(second);
      if (left == right) {
        return;
      }
      if (rank[left] < rank[right]) {
        final int temporary = left;
        left = right;
        right = temporary;
      }
      parent[right] = left;
      if (rank[left] == rank[right]) {
        rank[left]++;
      }
    }
  }
}
