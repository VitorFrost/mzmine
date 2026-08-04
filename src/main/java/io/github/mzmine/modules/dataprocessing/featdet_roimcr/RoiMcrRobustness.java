/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Component-level signatures, parameter-perturbation stability, and blank-evidence scoring.
 *
 * <p>The class deliberately keeps restart stability and parameter stability separate. Restart
 * stability asks whether repeated initializations converge to the same solution under one parameter
 * set. Perturbation stability asks whether the component survives scientifically reasonable changes
 * in ROI noise and weighting.</p>
 */
final class RoiMcrRobustness {

  private static final double EPS = 1e-12;
  private static final int NORMALIZED_PROFILE_POINTS = 101;

  private RoiMcrRobustness() {
  }

  enum Confidence {
    STABLE,
    MODERATE,
    PARAMETER_SENSITIVE
  }

  record Signature(int componentIndex, double apexFraction, double[] normalizedProfile,
                   double[] mzValues, double[] loadings, double height, double area) {

    Signature {
      normalizedProfile = normalizedProfile.clone();
      mzValues = mzValues.clone();
      loadings = loadings.clone();
      if (normalizedProfile.length != NORMALIZED_PROFILE_POINTS
          || mzValues.length != loadings.length || componentIndex < 0
          || apexFraction < 0 || apexFraction > 1 || height < 0 || area < 0) {
        throw new IllegalArgumentException("Invalid ROI-MCR component signature");
      }
    }
  }

  record Variant(String label, int rank, List<Signature> signatures) {

    Variant {
      signatures = List.copyOf(signatures);
      if (label == null || label.isBlank() || rank < 0 || signatures.size() != rank) {
        throw new IllegalArgumentException("Invalid ROI-MCR perturbation variant");
      }
    }

    static Variant failed(String label) {
      return new Variant(label, 0, List.of());
    }
  }

  record ComponentStability(double meanSimilarity, double supportFraction, int matchedVariants,
                            Confidence confidence) {
  }

  record Assessment(List<ComponentStability> components, double rankAgreement,
                    double overallStability, int variantCount) {

    Assessment {
      components = List.copyOf(components);
    }

    ComponentStability component(int componentIndex) {
      return components.get(componentIndex);
    }
  }

  record BlankEvidence(double bestSimilarity, double blankHeightRatio, double blankAreaRatio,
                       double blankBurden, double sampleSpecificity, String classification) {
  }

  static List<Signature> signatures(RoiMcrCore.McrResult result, double[][] restoredSpectra,
      List<RoiMcrCore.RoiTrace> rois) {
    if (result.rank() != restoredSpectra.length || rois.isEmpty()) {
      throw new IllegalArgumentException("MCR result, spectra, and ROIs are inconsistent");
    }
    final List<Signature> signatures = new ArrayList<>(result.rank());
    for (int component = 0; component < result.rank(); component++) {
      if (restoredSpectra[component].length != rois.size()) {
        throw new IllegalArgumentException("Spectral variables do not match ROI count");
      }
      final double[] profile = column(result.concentrations(), component);
      final int apex = argmax(profile);
      final double apexFraction = profile.length <= 1 ? 0
          : apex / (double) (profile.length - 1);
      final double[] normalizedProfile = resampleNormalized(profile,
          NORMALIZED_PROFILE_POINTS);
      final double[] mzValues = new double[rois.size()];
      final double[] loadings = restoredSpectra[component].clone();
      double spectrumMaximum = 0;
      double spectrumSum = 0;
      for (int ion = 0; ion < rois.size(); ion++) {
        mzValues[ion] = rois.get(ion).meanMz();
        spectrumMaximum = Math.max(spectrumMaximum, loadings[ion]);
        spectrumSum += loadings[ion];
      }
      final double profileMaximum = Arrays.stream(profile).max().orElse(0);
      double profileArea = 0;
      for (double value : profile) {
        profileArea += value;
      }
      signatures.add(new Signature(component, apexFraction, normalizedProfile, mzValues,
          loadings, profileMaximum * spectrumMaximum, profileArea * spectrumSum));
    }
    return List.copyOf(signatures);
  }

  static Assessment assess(List<Signature> reference, int referenceRank, List<Variant> variants,
      double absoluteMzTolerance, double ppmTolerance, double minimumSimilarity,
      double minimumSupport) {
    if (reference.isEmpty() || reference.size() != referenceRank || variants.isEmpty()
        || absoluteMzTolerance < 0 || ppmTolerance < 0 || minimumSimilarity < 0
        || minimumSimilarity > 1 || minimumSupport < 0 || minimumSupport > 1) {
      throw new IllegalArgumentException("Invalid robustness-assessment inputs");
    }

    final double[] similaritySums = new double[reference.size()];
    final int[] matchedCounts = new int[reference.size()];
    int matchingRanks = 0;
    for (Variant variant : variants) {
      if (variant.rank() == referenceRank) {
        matchingRanks++;
      }
      final double[] matched = match(reference, variant.signatures(), absoluteMzTolerance,
          ppmTolerance);
      for (int component = 0; component < reference.size(); component++) {
        similaritySums[component] += matched[component];
        if (matched[component] >= minimumSimilarity) {
          matchedCounts[component]++;
        }
      }
    }

    final List<ComponentStability> components = new ArrayList<>(reference.size());
    double overall = 0;
    for (int component = 0; component < reference.size(); component++) {
      final double mean = similaritySums[component] / variants.size();
      final double support = matchedCounts[component] / (double) variants.size();
      final Confidence confidence;
      if (mean >= minimumSimilarity && support >= minimumSupport) {
        confidence = Confidence.STABLE;
      } else if (mean >= minimumSimilarity * 0.8 && support >= 0.5) {
        confidence = Confidence.MODERATE;
      } else {
        confidence = Confidence.PARAMETER_SENSITIVE;
      }
      components.add(new ComponentStability(mean, support, matchedCounts[component], confidence));
      overall += 0.7 * mean + 0.3 * support;
    }
    final double rankAgreement = matchingRanks / (double) variants.size();
    overall = overall / reference.size() * (0.8 + 0.2 * rankAgreement);
    return new Assessment(components, rankAgreement, clamp01(overall), variants.size());
  }

  static BlankEvidence compareToBlank(Signature sample, List<Signature> blankComponents,
      double absoluteMzTolerance, double ppmTolerance, double minimumCandidateSimilarity) {
    Signature best = null;
    double bestSimilarity = 0;
    for (Signature blank : blankComponents) {
      final double similarity = componentSimilarity(sample, blank, absoluteMzTolerance,
          ppmTolerance);
      if (similarity > bestSimilarity) {
        bestSimilarity = similarity;
        best = blank;
      }
    }
    if (best == null || bestSimilarity < minimumCandidateSimilarity) {
      return new BlankEvidence(bestSimilarity, 0, 0, 0, 1, "unmatched-needs-review");
    }

    final double heightRatio = best.height() / Math.max(sample.height(), EPS);
    final double areaRatio = best.area() / Math.max(sample.area(), EPS);
    final double intensityBurden = Math.sqrt(clamp01(heightRatio) * clamp01(areaRatio));
    final double blankBurden = clamp01(bestSimilarity * intensityBurden);
    final double specificity = 1 - blankBurden;
    final String classification;
    if (bestSimilarity >= 0.8 && (heightRatio >= 0.5 || areaRatio >= 0.5)) {
      classification = "blank-associated";
    } else if (specificity >= 0.75 && heightRatio < 0.5 && areaRatio < 0.5) {
      classification = "sample-enriched";
    } else {
      classification = "ambiguous";
    }
    return new BlankEvidence(bestSimilarity, heightRatio, areaRatio, blankBurden, specificity,
        classification);
  }

  static double componentSimilarity(Signature first, Signature second,
      double absoluteMzTolerance, double ppmTolerance) {
    final double temporal = RoiMcrCore.cosine(first.normalizedProfile(),
        second.normalizedProfile());
    final double spectral = alignedSpectralCosine(first, second, absoluteMzTolerance,
        ppmTolerance);
    final double apex = Math.max(0, 1 - Math.abs(first.apexFraction() - second.apexFraction())
        / 0.15);
    return clamp01(0.45 * temporal + 0.45 * spectral + 0.10 * apex);
  }

  private static double[] match(List<Signature> reference, List<Signature> candidate,
      double absoluteMzTolerance, double ppmTolerance) {
    final double[] matched = new double[reference.size()];
    if (candidate.isEmpty()) {
      return matched;
    }
    final List<Pair> pairs = new ArrayList<>();
    for (int first = 0; first < reference.size(); first++) {
      for (int second = 0; second < candidate.size(); second++) {
        pairs.add(new Pair(first, second,
            componentSimilarity(reference.get(first), candidate.get(second),
                absoluteMzTolerance, ppmTolerance)));
      }
    }
    pairs.sort(Comparator.comparingDouble(Pair::score).reversed()
        .thenComparingInt(Pair::reference).thenComparingInt(Pair::candidate));
    final boolean[] usedReference = new boolean[reference.size()];
    final boolean[] usedCandidate = new boolean[candidate.size()];
    for (Pair pair : pairs) {
      if (!usedReference[pair.reference()] && !usedCandidate[pair.candidate()]) {
        matched[pair.reference()] = pair.score();
        usedReference[pair.reference()] = true;
        usedCandidate[pair.candidate()] = true;
      }
    }
    return matched;
  }

  private static double alignedSpectralCosine(Signature first, Signature second,
      double absoluteMzTolerance, double ppmTolerance) {
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

  private static double[] resampleNormalized(double[] values, int points) {
    final double[] result = new double[points];
    final double maximum = Arrays.stream(values).max().orElse(0);
    if (values.length == 1) {
      Arrays.fill(result, values[0] / Math.max(maximum, EPS));
      return result;
    }
    for (int point = 0; point < points; point++) {
      final double source = point * (values.length - 1d) / (points - 1d);
      final int lower = (int) Math.floor(source);
      final int upper = Math.min(values.length - 1, lower + 1);
      final double fraction = source - lower;
      result[point] = ((1 - fraction) * values[lower] + fraction * values[upper])
          / Math.max(maximum, EPS);
    }
    return result;
  }

  private static double[] column(double[][] matrix, int column) {
    final double[] result = new double[matrix.length];
    for (int row = 0; row < matrix.length; row++) {
      result[row] = matrix[row][column];
    }
    return result;
  }

  private static int argmax(double[] values) {
    int best = 0;
    for (int index = 1; index < values.length; index++) {
      if (values[index] > values[best]) {
        best = index;
      }
    }
    return best;
  }

  private static double clamp01(double value) {
    return Math.max(0, Math.min(1, value));
  }

  private record Pair(int reference, int candidate, double score) {
  }
}
