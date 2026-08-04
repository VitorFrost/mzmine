/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reconciles duplicate chemical components recovered from overlapping local MCR windows. */
final class RoiMcrReconciliation {

  private static final double EPS = 1e-12;

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

    // Complete-link grouping prevents transitive chains: A≈B and B≈C do not merge A and C unless
    // C is compatible with every existing member of the group.
    final List<List<Candidate>> grouped = new ArrayList<>();
    for (Candidate candidate : ordered) {
      List<Candidate> selected = null;
      double selectedMinimumSimilarity = -1;
      for (List<Candidate> group : grouped) {
        double minimumSimilarity = 1;
        boolean compatible = true;
        for (Candidate member : group) {
          if (!eligiblePair(candidate, member, maximumApexDistanceScans)) {
            compatible = false;
            break;
          }
          final double similarity = crossWindowSimilarity(candidate, member,
              maximumApexDistanceScans, absoluteMzTolerance, ppmTolerance);
          if (similarity < minimumComponentSimilarity) {
            compatible = false;
            break;
          }
          minimumSimilarity = Math.min(minimumSimilarity, similarity);
        }
        if (compatible && minimumSimilarity > selectedMinimumSimilarity) {
          selected = group;
          selectedMinimumSimilarity = minimumSimilarity;
        }
      }
      if (selected == null) {
        final List<Candidate> group = new ArrayList<>();
        group.add(candidate);
        grouped.add(group);
      } else {
        selected.add(candidate);
      }
    }

    final List<UnnumberedGroup> unnumbered = new ArrayList<>();
    for (List<Candidate> members : grouped) {
      final Candidate representative = members.stream().max(
          Comparator.comparingDouble(Candidate::quality)
              .thenComparingInt(candidate -> -candidate.windowId())
              .thenComparingInt(candidate -> -candidate.componentId())).orElseThrow();
      double minimumSimilarity = 1;
      for (Candidate member : members) {
        minimumSimilarity = Math.min(minimumSimilarity,
            crossWindowSimilarity(representative, member, maximumApexDistanceScans,
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

  private static boolean eligiblePair(Candidate first, Candidate second,
      int maximumApexDistanceScans) {
    return first.windowId() != second.windowId()
        && Math.abs(first.apexScan() - second.apexScan()) <= maximumApexDistanceScans
        && overlaps(first.startScan(), first.endScan(), second.startScan(), second.endScan());
  }

  private static double crossWindowSimilarity(Candidate first, Candidate second,
      int maximumApexDistanceScans, double absoluteMzTolerance, double ppmTolerance) {
    if (first == second) {
      return 1;
    }
    final double temporal = globalTemporalCosine(first, second);
    final double spectral = alignedSpectralCosine(first.signature(), second.signature(),
        absoluteMzTolerance, ppmTolerance);
    final int apexDistance = Math.abs(first.apexScan() - second.apexScan());
    final double apexAgreement = maximumApexDistanceScans == 0
        ? (apexDistance == 0 ? 1 : 0)
        : Math.max(0, 1 - apexDistance / (double) maximumApexDistanceScans);
    return Math.max(0, Math.min(1, 0.45 * temporal + 0.45 * spectral + 0.10 * apexAgreement));
  }

  private static double globalTemporalCosine(Candidate first, Candidate second) {
    final int start = Math.min(first.startScan(), second.startScan());
    final int end = Math.max(first.endScan(), second.endScan());
    double dot = 0;
    double firstNorm = 0;
    double secondNorm = 0;
    for (int scan = start; scan <= end; scan++) {
      final double firstValue = profileAt(first, scan);
      final double secondValue = profileAt(second, scan);
      dot += firstValue * secondValue;
      firstNorm += firstValue * firstValue;
      secondNorm += secondValue * secondValue;
    }
    return dot / Math.sqrt(Math.max(firstNorm * secondNorm, EPS));
  }

  private static double profileAt(Candidate candidate, int globalScan) {
    if (globalScan < candidate.startScan() || globalScan > candidate.endScan()) {
      return 0;
    }
    final double[] profile = candidate.signature().normalizedProfile();
    if (candidate.endScan() == candidate.startScan()) {
      return profile[0];
    }
    final double position = (globalScan - candidate.startScan())
        * (profile.length - 1d) / (candidate.endScan() - candidate.startScan());
    final int lower = (int) Math.floor(position);
    final int upper = Math.min(profile.length - 1, lower + 1);
    final double fraction = position - lower;
    return (1 - fraction) * profile[lower] + fraction * profile[upper];
  }

  private static double alignedSpectralCosine(RoiMcrRobustness.Signature first,
      RoiMcrRobustness.Signature second, double absoluteMzTolerance, double ppmTolerance) {
    final boolean[] used = new boolean[second.mzValues().length];
    double dot = 0;
    for (int firstIon = 0; firstIon < first.mzValues().length; firstIon++) {
      if (first.loadings()[firstIon] <= 0) {
        continue;
      }
      int best = -1;
      double bestDistance = Double.POSITIVE_INFINITY;
      for (int secondIon = 0; secondIon < second.mzValues().length; secondIon++) {
        if (used[secondIon] || second.loadings()[secondIon] <= 0) {
          continue;
        }
        final double referenceMz = 0.5 * (first.mzValues()[firstIon]
            + second.mzValues()[secondIon]);
        final double tolerance = Math.max(absoluteMzTolerance,
            referenceMz * ppmTolerance / 1_000_000d);
        final double distance = Math.abs(first.mzValues()[firstIon]
            - second.mzValues()[secondIon]);
        if (distance <= tolerance && distance < bestDistance) {
          best = secondIon;
          bestDistance = distance;
        }
      }
      if (best >= 0) {
        used[best] = true;
        dot += first.loadings()[firstIon] * second.loadings()[best];
      }
    }
    double firstNorm = 0;
    for (double loading : first.loadings()) {
      firstNorm += loading * loading;
    }
    double secondNorm = 0;
    for (double loading : second.loadings()) {
      secondNorm += loading * loading;
    }
    return dot / Math.sqrt(Math.max(firstNorm * secondNorm, EPS));
  }

  private static boolean overlaps(int firstStart, int firstEnd, int secondStart, int secondEnd) {
    return firstStart <= secondEnd && secondStart <= firstEnd;
  }

  private record UnnumberedGroup(Candidate representative, List<Candidate> members,
                                double minimumRepresentativeSimilarity) {
  }
}
