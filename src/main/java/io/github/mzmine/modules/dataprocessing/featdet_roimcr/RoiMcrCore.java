/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Dependency-free ROI tracking and constrained MCR-ALS scientific core. */
final class RoiMcrCore {

  static final double EPS = 1e-12;

  private RoiMcrCore() {
  }

  record RoiOptions(double absoluteMzTolerance, double ppmTolerance, double noiseLevel,
                    double seedIntensity, int maximumMissingScans, int minimumDataPoints,
                    int minimumConsecutiveScans, double minimumHeight) {

    RoiOptions {
      if (absoluteMzTolerance < 0 || ppmTolerance < 0 || noiseLevel < 0
          || seedIntensity < noiseLevel || maximumMissingScans < 0 || minimumDataPoints < 1
          || minimumConsecutiveScans < 1 || minimumHeight < 0) {
        throw new IllegalArgumentException("Invalid ROI options");
      }
    }

    double toleranceFor(double mz) {
      return Math.max(absoluteMzTolerance, mz * ppmTolerance / 1_000_000d);
    }
  }

  record RoiTrace(int id, int startScan, int endScan, int[] scanIndices, double[] mzValues,
                  double[] intensities, double meanMz, double height, int apexScan,
                  int maximumConsecutiveScans) {

    double intensityAt(int scanIndex) {
      final int position = Arrays.binarySearch(scanIndices, scanIndex);
      return position >= 0 ? intensities[position] : 0d;
    }

    double mzAtOrMean(int scanIndex) {
      final int position = Arrays.binarySearch(scanIndices, scanIndex);
      return position >= 0 ? mzValues[position] : meanMz;
    }
  }

  static final class RoiBuilder {

    private final RoiOptions options;
    private final List<MutableRoi> active = new ArrayList<>();
    private final List<RoiTrace> finished = new ArrayList<>();
    private int nextId = 1;
    private int lastScan = -1;

    RoiBuilder(RoiOptions options) {
      this.options = options;
    }

    void acceptScan(int scanIndex, double[] mzValues, double[] intensities) {
      if (scanIndex <= lastScan || mzValues.length != intensities.length) {
        throw new IllegalArgumentException("Scans must be ordered and arrays must match");
      }
      lastScan = scanIndex;
      final double[] referenceMz = new double[active.size()];
      for (int i = 0; i < active.size(); i++) {
        referenceMz[i] = active.get(i).meanMz();
        active.get(i).assigned = false;
      }
      final Integer[] order = new Integer[mzValues.length];
      for (int i = 0; i < order.length; i++) {
        order[i] = i;
      }
      Arrays.sort(order, Comparator.comparingDouble((Integer i) -> intensities[i]).reversed());
      for (int point : order) {
        final double mz = mzValues[point];
        final double intensity = intensities[point];
        if (!Double.isFinite(mz) || !Double.isFinite(intensity)
            || intensity < options.noiseLevel()) {
          continue;
        }
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < active.size(); i++) {
          final MutableRoi roi = active.get(i);
          if (roi.assigned
              || scanIndex - roi.lastScan > options.maximumMissingScans() + 1) {
            continue;
          }
          final double tolerance = options.toleranceFor(referenceMz[i]);
          final double distance = Math.abs(mz - referenceMz[i]);
          if (distance <= tolerance && distance < bestDistance) {
            best = i;
            bestDistance = distance;
          }
        }
        if (best >= 0) {
          active.get(best).add(scanIndex, mz, intensity);
        } else if (intensity >= options.seedIntensity()) {
          final MutableRoi roi = new MutableRoi(nextId++);
          roi.add(scanIndex, mz, intensity);
          active.add(roi);
        }
      }
      for (int i = active.size() - 1; i >= 0; i--) {
        if (scanIndex - active.get(i).lastScan > options.maximumMissingScans()) {
          finish(active.remove(i));
        }
      }
    }

    List<RoiTrace> finish() {
      active.forEach(this::finish);
      active.clear();
      finished.sort(Comparator.comparingDouble(RoiTrace::meanMz));
      return List.copyOf(finished);
    }

    private void finish(MutableRoi roi) {
      if (roi.scans.size() >= options.minimumDataPoints()
          && roi.height >= options.minimumHeight()
          && roi.maximumConsecutive() >= options.minimumConsecutiveScans()) {
        finished.add(roi.freeze());
      }
    }

    private static final class MutableRoi {

      private final int id;
      private final List<Integer> scans = new ArrayList<>();
      private final List<Double> mzs = new ArrayList<>();
      private final List<Double> intensities = new ArrayList<>();
      private double weightedMz;
      private double intensitySum;
      private double height;
      private int apexScan;
      private int lastScan = Integer.MIN_VALUE;
      private boolean assigned;

      MutableRoi(int id) {
        this.id = id;
      }

      void add(int scan, double mz, double intensity) {
        scans.add(scan);
        mzs.add(mz);
        intensities.add(intensity);
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
        return intensitySum > 0 ? weightedMz / intensitySum : mzs.get(mzs.size() - 1);
      }

      int maximumConsecutive() {
        int best = scans.isEmpty() ? 0 : 1;
        int current = best;
        for (int i = 1; i < scans.size(); i++) {
          current = scans.get(i) == scans.get(i - 1) + 1 ? current + 1 : 1;
          best = Math.max(best, current);
        }
        return best;
      }

      RoiTrace freeze() {
        final int[] scanArray = new int[scans.size()];
        final double[] mzArray = new double[scans.size()];
        final double[] intensityArray = new double[scans.size()];
        for (int i = 0; i < scans.size(); i++) {
          scanArray[i] = scans.get(i);
          mzArray[i] = mzs.get(i);
          intensityArray[i] = intensities.get(i);
        }
        return new RoiTrace(id, scanArray[0], scanArray[scanArray.length - 1], scanArray,
            mzArray, intensityArray, meanMz(), height, apexScan, maximumConsecutive());
      }
    }
  }

  record McrOptions(int maximumComponents, int restarts, int maximumIterations,
                    int nnlsIterations, double convergenceTolerance,
                    double minimumRankImprovement, double minimumRestartStability,
                    int smoothingRadius, double unimodalityFlexibility,
                    double spectralSparsity, int minimumComponentIons,
                    double minimumComponentEnergyFraction, double maximumTemporalCosine,
                    long randomSeed) {
  }

  record McrResult(int rank, double[][] concentrations, double[][] spectra,
                   double relativeError, double explainedVariance, double restartStability,
                   double rankImprovement, boolean converged) {
  }

  static McrResult selectRank(double[][] matrix, McrOptions options,
      BooleanSupplier cancelled) {
    validate(matrix);
    final double energy = norm2(matrix);
    final int maxRank = Math.min(options.maximumComponents(),
        Math.min(Math.max(1, matrix.length - 1), Math.max(1, matrix[0].length - 1)));
    McrResult accepted = null;
    double previousExplained = 0;
    for (int rank = 1; rank <= maxRank; rank++) {
      checkCancelled(cancelled);
      final List<McrResult> fits = new ArrayList<>();
      for (int restart = 0; restart < options.restarts(); restart++) {
        fits.add(fit(matrix, rank, options, energy,
            options.randomSeed() + rank * 1_000_003L + restart * 7_919L, restart, cancelled));
      }
      fits.sort(Comparator.comparingDouble(McrResult::relativeError));
      final McrResult best = fits.get(0);
      final double stability = stability(best, fits);
      final double improvement = best.explainedVariance() - previousExplained;
      final McrResult current = new McrResult(rank, best.concentrations(), best.spectra(),
          best.relativeError(), best.explainedVariance(), stability, improvement,
          best.converged());
      if (rank > 1 && (improvement < options.minimumRankImprovement()
          || stability < options.minimumRestartStability()
          || !plausible(current, options))) {
        break;
      }
      accepted = current;
      previousExplained = current.explainedVariance();
    }
    return accepted;
  }

  private static McrResult fit(double[][] matrix, int rank, McrOptions options, double energy,
      long seed, int restart, BooleanSupplier cancelled) {
    final double[][] spectra = initializeSpectra(matrix, rank, seed, restart);
    final double[][] concentrations = new double[matrix.length][rank];
    updateConcentrations(matrix, concentrations, spectra, options.nnlsIterations());
    constrainConcentrations(concentrations, options);
    normalize(concentrations, spectra);
    double previousError = Double.POSITIVE_INFINITY;
    boolean converged = false;
    for (int iteration = 1; iteration <= options.maximumIterations(); iteration++) {
      if ((iteration & 7) == 1) {
        checkCancelled(cancelled);
      }
      updateSpectra(matrix, concentrations, spectra, options.nnlsIterations());
      sparsify(spectra, options.spectralSparsity());
      updateConcentrations(matrix, concentrations, spectra, options.nnlsIterations());
      constrainConcentrations(concentrations, options);
      normalize(concentrations, spectra);
      if (iteration % 5 == 0 || iteration == options.maximumIterations()) {
        final double error = Math.sqrt(rss(matrix, concentrations, spectra) / energy);
        if (Double.isFinite(previousError)
            && Math.abs(previousError - error) / Math.max(previousError, EPS)
            < options.convergenceTolerance()) {
          converged = true;
          break;
        }
        previousError = error;
      }
    }
    normalize(concentrations, spectra);
    reorderByApex(concentrations, spectra);
    final double relativeError = Math.sqrt(rss(matrix, concentrations, spectra) / energy);
    return new McrResult(rank, concentrations, spectra, relativeError,
        Math.max(0, Math.min(1, 1 - relativeError * relativeError)), 1, 0, converged);
  }

  static double[] columnMaxima(double[][] matrix) {
    final double[] maxima = new double[matrix[0].length];
    for (double[] row : matrix) {
      for (int j = 0; j < row.length; j++) {
        maxima[j] = Math.max(maxima[j], row[j]);
      }
    }
    return maxima;
  }

  static double[][] weightedCopy(double[][] matrix, double exponent, double[] scales) {
    final double[][] result = copy(matrix);
    for (int j = 0; j < scales.length; j++) {
      scales[j] = Math.pow(Math.max(scales[j], EPS), exponent);
      for (double[] row : result) {
        row[j] /= scales[j];
      }
    }
    return result;
  }

  static double[][] restoreSpectralScale(double[][] spectra, double[] scales) {
    final double[][] result = copy(spectra);
    for (double[] spectrum : result) {
      for (int j = 0; j < spectrum.length; j++) {
        spectrum[j] *= scales[j];
      }
    }
    return result;
  }

  static double rss(double[][] matrix, double[][] concentrations, double[][] spectra) {
    double sum = 0;
    for (int i = 0; i < matrix.length; i++) {
      for (int j = 0; j < matrix[0].length; j++) {
        double fitted = 0;
        for (int k = 0; k < spectra.length; k++) {
          fitted += concentrations[i][k] * spectra[k][j];
        }
        final double residual = matrix[i][j] - fitted;
        sum += residual * residual;
      }
    }
    return sum;
  }

  private static double[][] initializeSpectra(double[][] matrix, int rank, long seed,
      int restart) {
    final Random random = new Random(seed);
    final double[] norms = new double[matrix.length];
    for (int i = 0; i < matrix.length; i++) {
      norms[i] = Math.sqrt(dot(matrix[i], matrix[i]));
    }
    final int[] selected = new int[rank];
    selected[0] = restart == 0 ? argmax(norms) : random.nextInt(matrix.length);
    for (int component = 1; component < rank; component++) {
      double bestScore = -1;
      for (int scan = 0; scan < matrix.length; scan++) {
        double distance = 1;
        for (int prior = 0; prior < component; prior++) {
          distance = Math.min(distance, 1 - cosine(matrix[scan], matrix[selected[prior]]));
        }
        final double score = distance * (0.25 + 0.75 * norms[scan]
            / Math.max(norms[argmax(norms)], EPS)) + random.nextDouble() * 0.01;
        if (score > bestScore) {
          bestScore = score;
          selected[component] = scan;
        }
      }
    }
    final double[][] spectra = new double[rank][matrix[0].length];
    for (int component = 0; component < rank; component++) {
      spectra[component] = matrix[selected[component]].clone();
    }
    return spectra;
  }

  private static void updateSpectra(double[][] matrix, double[][] concentrations,
      double[][] spectra, int iterations) {
    final double[][] gram = gramColumns(concentrations);
    final double[] atb = new double[spectra.length];
    final double[] solution = new double[spectra.length];
    for (int variable = 0; variable < matrix[0].length; variable++) {
      Arrays.fill(atb, 0);
      for (int component = 0; component < spectra.length; component++) {
        solution[component] = spectra[component][variable];
        for (int scan = 0; scan < matrix.length; scan++) {
          atb[component] += concentrations[scan][component] * matrix[scan][variable];
        }
      }
      nnls(gram, atb, solution, iterations);
      for (int component = 0; component < spectra.length; component++) {
        spectra[component][variable] = solution[component];
      }
    }
  }

  private static void updateConcentrations(double[][] matrix, double[][] concentrations,
      double[][] spectra, int iterations) {
    final double[][] gram = gramRows(spectra);
    final double[] atb = new double[spectra.length];
    final double[] solution = new double[spectra.length];
    for (int scan = 0; scan < matrix.length; scan++) {
      Arrays.fill(atb, 0);
      for (int component = 0; component < spectra.length; component++) {
        solution[component] = concentrations[scan][component];
        for (int variable = 0; variable < matrix[0].length; variable++) {
          atb[component] += spectra[component][variable] * matrix[scan][variable];
        }
      }
      nnls(gram, atb, solution, iterations);
      System.arraycopy(solution, 0, concentrations[scan], 0, solution.length);
    }
  }

  private static void nnls(double[][] gram, double[] atb, double[] solution, int iterations) {
    for (int iteration = 0; iteration < iterations; iteration++) {
      double change = 0;
      for (int component = 0; component < solution.length; component++) {
        double residual = atb[component];
        for (int other = 0; other < solution.length; other++) {
          if (other != component) {
            residual -= gram[component][other] * solution[other];
          }
        }
        final double updated = Math.max(0,
            residual / Math.max(gram[component][component], EPS));
        change = Math.max(change, Math.abs(updated - solution[component]));
        solution[component] = updated;
      }
      if (change < 1e-10) {
        break;
      }
    }
  }

  private static void constrainConcentrations(double[][] concentrations, McrOptions options) {
    final double[] profile = new double[concentrations.length];
    for (int component = 0; component < concentrations[0].length; component++) {
      for (int scan = 0; scan < concentrations.length; scan++) {
        profile[scan] = concentrations[scan][component];
      }
      smooth(profile, options.smoothingRadius());
      unimodal(profile, options.unimodalityFlexibility());
      smooth(profile, options.smoothingRadius());
      for (int scan = 0; scan < concentrations.length; scan++) {
        concentrations[scan][component] = Math.max(0, profile[scan]);
      }
    }
  }

  private static void smooth(double[] values, int radius) {
    if (radius == 0) {
      return;
    }
    final double[] copy = values.clone();
    for (int i = 0; i < values.length; i++) {
      double sum = 0;
      int count = 0;
      for (int j = Math.max(0, i - radius); j <= Math.min(values.length - 1, i + radius); j++) {
        sum += copy[j];
        count++;
      }
      values[i] = sum / count;
    }
  }

  private static void unimodal(double[] values, double flexibility) {
    final double[] original = values.clone();
    final int apex = argmax(values);
    for (int i = 1; i <= apex; i++) {
      values[i] = Math.max(values[i], values[i - 1]);
    }
    for (int i = values.length - 2; i >= apex; i--) {
      values[i] = Math.max(values[i], values[i + 1]);
    }
    for (int i = 0; i < values.length; i++) {
      values[i] = (1 - flexibility) * values[i] + flexibility * original[i];
    }
  }

  private static void sparsify(double[][] spectra, double thresholdFraction) {
    for (double[] spectrum : spectra) {
      final double threshold = Arrays.stream(spectrum).max().orElse(0) * thresholdFraction;
      for (int j = 0; j < spectrum.length; j++) {
        if (spectrum[j] < threshold) {
          spectrum[j] = 0;
        }
      }
    }
  }

  private static void normalize(double[][] concentrations, double[][] spectra) {
    for (int component = 0; component < spectra.length; component++) {
      double maximum = 0;
      for (double[] concentration : concentrations) {
        maximum = Math.max(maximum, concentration[component]);
      }
      if (maximum <= EPS) {
        continue;
      }
      for (double[] concentration : concentrations) {
        concentration[component] /= maximum;
      }
      for (int j = 0; j < spectra[component].length; j++) {
        spectra[component][j] *= maximum;
      }
    }
  }

  private static boolean plausible(McrResult result, McrOptions options) {
    double totalEnergy = 0;
    final double[] energies = new double[result.rank()];
    for (int component = 0; component < result.rank(); component++) {
      int ions = 0;
      for (double loading : result.spectra()[component]) {
        if (loading > EPS) {
          ions++;
        }
      }
      if (ions < options.minimumComponentIons()) {
        return false;
      }
      energies[component] = dot(result.spectra()[component], result.spectra()[component])
          * dot(column(result.concentrations(), component),
          column(result.concentrations(), component));
      totalEnergy += energies[component];
    }
    for (double energy : energies) {
      if (energy / Math.max(totalEnergy, EPS) < options.minimumComponentEnergyFraction()) {
        return false;
      }
    }
    for (int first = 0; first < result.rank(); first++) {
      for (int second = first + 1; second < result.rank(); second++) {
        if (cosine(column(result.concentrations(), first),
            column(result.concentrations(), second)) > options.maximumTemporalCosine()) {
          return false;
        }
      }
    }
    return true;
  }

  private static double stability(McrResult reference, List<McrResult> results) {
    double sum = 0;
    int count = 0;
    for (McrResult other : results) {
      if (other == reference) {
        continue;
      }
      final boolean[] used = new boolean[reference.rank()];
      double matched = 0;
      for (int component = 0; component < reference.rank(); component++) {
        double best = 0;
        int bestIndex = -1;
        for (int candidate = 0; candidate < other.rank(); candidate++) {
          if (used[candidate]) {
            continue;
          }
          final double score = 0.5 * cosine(column(reference.concentrations(), component),
              column(other.concentrations(), candidate))
              + 0.5 * cosine(reference.spectra()[component], other.spectra()[candidate]);
          if (score > best) {
            best = score;
            bestIndex = candidate;
          }
        }
        if (bestIndex >= 0) {
          used[bestIndex] = true;
          matched += best;
        }
      }
      sum += matched / reference.rank();
      count++;
    }
    return count == 0 ? 1 : sum / count;
  }

  private static void reorderByApex(double[][] concentrations, double[][] spectra) {
    final Integer[] order = new Integer[spectra.length];
    for (int i = 0; i < order.length; i++) {
      order[i] = i;
    }
    Arrays.sort(order, Comparator.comparingInt(i -> argmax(column(concentrations, i))));
    final double[][] oldConcentrations = copy(concentrations);
    final double[][] oldSpectra = copy(spectra);
    for (int target = 0; target < order.length; target++) {
      for (int scan = 0; scan < concentrations.length; scan++) {
        concentrations[scan][target] = oldConcentrations[scan][order[target]];
      }
      spectra[target] = oldSpectra[order[target]].clone();
    }
  }

  private static double[][] gramColumns(double[][] matrix) {
    final double[][] gram = new double[matrix[0].length][matrix[0].length];
    for (double[] row : matrix) {
      for (int first = 0; first < gram.length; first++) {
        for (int second = 0; second < gram.length; second++) {
          gram[first][second] += row[first] * row[second];
        }
      }
    }
    for (int i = 0; i < gram.length; i++) {
      gram[i][i] += EPS;
    }
    return gram;
  }

  private static double[][] gramRows(double[][] matrix) {
    final double[][] gram = new double[matrix.length][matrix.length];
    for (int first = 0; first < matrix.length; first++) {
      for (int second = 0; second < matrix.length; second++) {
        gram[first][second] = dot(matrix[first], matrix[second]);
      }
      gram[first][first] += EPS;
    }
    return gram;
  }

  private static double norm2(double[][] matrix) {
    double sum = 0;
    for (double[] row : matrix) {
      sum += dot(row, row);
    }
    return sum;
  }

  private static double[][] copy(double[][] matrix) {
    final double[][] result = new double[matrix.length][];
    for (int i = 0; i < matrix.length; i++) {
      result[i] = matrix[i].clone();
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
    for (int i = 1; i < values.length; i++) {
      if (values[i] > values[best]) {
        best = i;
      }
    }
    return best;
  }

  private static double dot(double[] first, double[] second) {
    double sum = 0;
    for (int i = 0; i < first.length; i++) {
      sum += first[i] * second[i];
    }
    return sum;
  }

  static double cosine(double[] first, double[] second) {
    return dot(first, second) / Math.sqrt(Math.max(dot(first, first) * dot(second, second), EPS));
  }

  private static void validate(double[][] matrix) {
    if (matrix.length == 0 || matrix[0].length == 0) {
      throw new IllegalArgumentException("Matrix must be non-empty");
    }
    for (double[] row : matrix) {
      if (row.length != matrix[0].length) {
        throw new IllegalArgumentException("Matrix must be rectangular");
      }
      for (double value : row) {
        if (!Double.isFinite(value) || value < 0) {
          throw new IllegalArgumentException("Matrix must be finite and non-negative");
        }
      }
    }
  }

  private static void checkCancelled(BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean()) {
      throw new CancellationException("ROI-MCR calculation cancelled");
    }
  }
}
