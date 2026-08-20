/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import io.github.mzmine.datamodel.FeatureStatus;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.impl.SimpleIonTimeSeries;
import io.github.mzmine.datamodel.features.ModularFeature;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.datamodel.features.ModularFeatureListRow;
import io.github.mzmine.datamodel.features.SimpleFeatureListAppliedMethod;
import io.github.mzmine.datamodel.features.types.FeatureShapeType;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.DataTypeUtils;
import io.github.mzmine.util.FeatureListUtils;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Executes direct scan mass-list ROI tracking and constrained MCR-ALS feature creation. */
public final class RoiMcrTask extends AbstractTask {

  private static final Logger logger = Logger.getLogger(RoiMcrTask.class.getName());
  private static final long RANDOM_SEED = 20260804L;

  private final MZmineProject project;
  private final RawDataFile rawFile;
  private final ParameterSet parameters;
  private volatile double progress;

  public RoiMcrTask(@NotNull MZmineProject project, @NotNull RawDataFile rawFile,
      @NotNull ParameterSet parameters, @Nullable MemoryMapStorage storage,
      @NotNull Instant moduleCallDate) {
    super(storage, moduleCallDate);
    this.project = project;
    this.rawFile = rawFile;
    this.parameters = parameters;
  }

  @Override
  public String getTaskDescription() {
    return "ROI-MCR feature detection in " + rawFile.getName();
  }

  @Override
  public double getFinishedPercentage() {
    return progress;
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    try {
      final ScanSelection selection = parameters.getValue(RoiMcrParameters.scanSelection);
      final Scan[] scans = selection.getMatchingScans(rawFile);
      validateScans(scans);

      final MZTolerance tolerance = parameters.getValue(RoiMcrParameters.mzTolerance);
      final RoiMcrCore.RoiOptions roiOptions = roiOptions(tolerance);
      final RoiMcrCore.McrOptions mcrOptions = mcrOptions();
      final List<RoiMcrCore.RoiTrace> rois = buildRois(scans, roiOptions, 0, 0.20);
      validateRoiCount(rois);

      final double weightingExponent = parameters.getValue(RoiMcrParameters.weightingExponent);
      final Analysis reference = fitAnalysis(scans.length, rois, mcrOptions, weightingExponent);
      progress = 0.48;
      final RoiMcrRobustness.Assessment robustness = assessPerturbations(scans, tolerance,
          roiOptions, mcrOptions, weightingExponent, reference);
      progress = 0.75;

      final ModularFeatureList output = new ModularFeatureList(
          rawFile.getName() + " " + parameters.getValue(RoiMcrParameters.suffix),
          getMemoryMapStorage(), rawFile);
      DataTypeUtils.addDefaultChromatographicTypeColumns(output);
      final int created = createFeatures(output, scans, reference, robustness);
      if (created == 0) {
        throw new IllegalStateException("The MCR model was fitted, but no reconstructed ion feature "
            + "passed the feature-creation thresholds.");
      }

      FeatureListUtils.sortByDefaultRT(output, true);
      output.setSelectedScans(rawFile, Arrays.asList(scans));
      rawFile.getAppliedMethods().forEach(method -> output.getAppliedMethods().add(method));
      output.getAppliedMethods().add(new SimpleFeatureListAppliedMethod(RoiMcrModule.class,
          parameters, getModuleCallDate()));
      project.addFeatureList(output);
      progress = 1;
      setStatus(TaskStatus.FINISHED);
    } catch (CancellationException ignored) {
      cancel();
    } catch (Throwable error) {
      logger.log(Level.SEVERE, "ROI-MCR processing failed for " + rawFile.getName(), error);
      setErrorMessage(error.getMessage() == null ? error.getClass().getSimpleName()
          : error.getMessage());
      setStatus(TaskStatus.ERROR);
    }
  }

  private RoiMcrRobustness.Assessment assessPerturbations(Scan[] scans, MZTolerance tolerance,
      RoiMcrCore.RoiOptions referenceRoiOptions, RoiMcrCore.McrOptions mcrOptions,
      double weightingExponent, Analysis reference) {
    final List<RoiMcrRobustness.Variant> variants = new ArrayList<>();
    final double noiseFactor = parameters.getValue(RoiMcrParameters.robustnessNoiseFactor);
    variants.add(fitNoiseVariant("noise-low", scans, scale(referenceRoiOptions, 1 / noiseFactor),
        mcrOptions, weightingExponent));
    progress = 0.54;
    variants.add(fitNoiseVariant("noise-high", scans, scale(referenceRoiOptions, noiseFactor),
        mcrOptions, weightingExponent));
    progress = 0.60;

    final double weightingStep = parameters.getValue(RoiMcrParameters.robustnessWeightingStep);
    final double lowerWeight = Math.max(0, weightingExponent - weightingStep);
    final double upperWeight = Math.min(1, weightingExponent + weightingStep);
    if (Math.abs(lowerWeight - weightingExponent) > RoiMcrCore.EPS) {
      variants.add(fitWeightVariant("weight-low", scans.length, reference.rois(), mcrOptions,
          lowerWeight));
    }
    progress = 0.66;
    if (Math.abs(upperWeight - weightingExponent) > RoiMcrCore.EPS) {
      variants.add(fitWeightVariant("weight-high", scans.length, reference.rois(), mcrOptions,
          upperWeight));
    }
    progress = 0.72;

    final List<RoiMcrRobustness.Signature> signatures = RoiMcrRobustness.signatures(
        reference.result(), reference.restoredSpectra(), reference.rois());
    return RoiMcrRobustness.assess(signatures, reference.result().rank(), variants,
        tolerance.getMzTolerance(), tolerance.getPpmTolerance(),
        parameters.getValue(RoiMcrParameters.minimumPerturbationSimilarity),
        parameters.getValue(RoiMcrParameters.minimumPerturbationSupport));
  }

  private RoiMcrRobustness.Variant fitNoiseVariant(String label, Scan[] scans,
      RoiMcrCore.RoiOptions roiOptions, RoiMcrCore.McrOptions mcrOptions,
      double weightingExponent) {
    try {
      final List<RoiMcrCore.RoiTrace> rois = buildRois(scans, roiOptions, progress, progress);
      validateRoiCount(rois);
      final Analysis analysis = fitAnalysis(scans.length, rois, mcrOptions, weightingExponent);
      return new RoiMcrRobustness.Variant(label, analysis.result().rank(),
          RoiMcrRobustness.signatures(analysis.result(), analysis.restoredSpectra(), rois));
    } catch (CancellationException cancelled) {
      throw cancelled;
    } catch (RuntimeException failedVariant) {
      logger.log(Level.FINE, "ROI-MCR robustness variant failed: " + label, failedVariant);
      return RoiMcrRobustness.Variant.failed(label);
    }
  }

  private RoiMcrRobustness.Variant fitWeightVariant(String label, int numberOfScans,
      List<RoiMcrCore.RoiTrace> rois, RoiMcrCore.McrOptions mcrOptions,
      double weightingExponent) {
    try {
      final Analysis analysis = fitAnalysis(numberOfScans, rois, mcrOptions, weightingExponent);
      return new RoiMcrRobustness.Variant(label, analysis.result().rank(),
          RoiMcrRobustness.signatures(analysis.result(), analysis.restoredSpectra(), rois));
    } catch (CancellationException cancelled) {
      throw cancelled;
    } catch (RuntimeException failedVariant) {
      logger.log(Level.FINE, "ROI-MCR robustness variant failed: " + label, failedVariant);
      return RoiMcrRobustness.Variant.failed(label);
    }
  }

  private Analysis fitAnalysis(int numberOfScans, List<RoiMcrCore.RoiTrace> rois,
      RoiMcrCore.McrOptions mcrOptions, double weightingExponent) {
    checkCancelled();
    final double[][] original = buildMatrix(numberOfScans, rois);
    final double[] scales = RoiMcrCore.columnMaxima(original);
    final double[][] modelMatrix = RoiMcrCore.weightedCopy(original, weightingExponent, scales);
    final RoiMcrCore.McrResult weightedResult = RoiMcrCore.selectRank(modelMatrix, mcrOptions,
        this::isCanceled);
    if (weightedResult == null || weightedResult.rank() < 1) {
      throw new IllegalStateException("MCR-ALS did not produce an accepted component model.");
    }
    final double[][] restoredSpectra = RoiMcrCore.restoreSpectralScale(
        weightedResult.spectra(), scales);
    final double originalEnergy = squaredNorm(original);
    final double originalRss = RoiMcrCore.rss(original, weightedResult.concentrations(),
        restoredSpectra);
    final double originalExplained = Math.max(0, Math.min(1,
        1 - originalRss / Math.max(originalEnergy, RoiMcrCore.EPS)));
    return new Analysis(rois, weightedResult, restoredSpectra, originalExplained);
  }

  private int createFeatures(ModularFeatureList output, Scan[] scans, Analysis analysis,
      RoiMcrRobustness.Assessment robustness) {
    final double minimumContribution = parameters.getValue(
        RoiMcrParameters.minimumSpectralContribution);
    final double minimumAssignment = parameters.getValue(
        RoiMcrParameters.minimumAssignmentFraction);
    final double edgeFraction = parameters.getValue(RoiMcrParameters.componentEdgeFraction);
    final double minimumHeight = parameters.getValue(RoiMcrParameters.minimumFeatureHeight);
    final RoiMcrCore.McrResult result = analysis.result();
    final double[][] spectra = analysis.restoredSpectra();
    final List<RoiMcrCore.RoiTrace> rois = analysis.rois();
    int rowId = 1;

    for (int component = 0; component < result.rank(); component++) {
      final RoiMcrRobustness.ComponentStability componentStability = robustness.component(
          component);
      final double spectrumMaximum = Arrays.stream(spectra[component]).max().orElse(0);
      final double[] profile = componentProfile(result.concentrations(), component);
      final int apex = argmax(profile);
      int start = apex;
      int end = apex;
      while (start > 0 && profile[start - 1] >= edgeFraction * profile[apex]) {
        start--;
      }
      while (end + 1 < profile.length && profile[end + 1] >= edgeFraction * profile[apex]) {
        end++;
      }

      for (int roiIndex = 0; roiIndex < rois.size(); roiIndex++) {
        final double loading = spectra[component][roiIndex];
        if (loading <= 0 || loading < minimumContribution * spectrumMaximum) {
          continue;
        }
        double totalLoading = 0;
        for (double[] componentSpectrum : spectra) {
          totalLoading += componentSpectrum[roiIndex];
        }
        final double assignment = loading / Math.max(totalLoading, RoiMcrCore.EPS);
        if (assignment < minimumAssignment) {
          continue;
        }

        final int length = end - start + 1;
        final double[] intensities = new double[length];
        final double[] mzValues = new double[length];
        final List<Scan> featureScans = new ArrayList<>(length);
        double height = 0;
        for (int offset = 0; offset < length; offset++) {
          final int scanIndex = start + offset;
          intensities[offset] = profile[scanIndex] * loading;
          height = Math.max(height, intensities[offset]);
          mzValues[offset] = rois.get(roiIndex).mzAtOrMean(scanIndex);
          featureScans.add(scans[scanIndex]);
        }
        if (height < minimumHeight) {
          continue;
        }

        final SimpleIonTimeSeries series = new SimpleIonTimeSeries(output.getMemoryMapStorage(),
            mzValues, intensities, featureScans);
        final ModularFeature feature = new ModularFeature(output, rawFile, series,
            FeatureStatus.DETECTED);
        final ModularFeatureListRow row = new ModularFeatureListRow(output, rowId++, feature);
        row.set(FeatureShapeType.class, true);
        row.set(CommentType.class,
            "ROI-MCR component=%d; rank=%d; ROI=%d; assignment=%.4f; "
                .formatted(component + 1, result.rank(), rois.get(roiIndex).id(), assignment)
                + "restart_stability=" + format(result.restartStability())
                + "; perturbation_stability=" + format(componentStability.meanSimilarity())
                + "; perturbation_support=" + format(componentStability.supportFraction())
                + "; perturbation_variants=" + robustness.variantCount()
                + "; rank_agreement=" + format(robustness.rankAgreement())
                + "; confidence=" + confidenceLabel(componentStability.confidence())
                + "; original_explained=" + format(analysis.originalExplained()));
        output.addRow(row);
      }
      progress = 0.75 + 0.20 * (component + 1d) / result.rank();
    }
    return rowId - 1;
  }

  private List<RoiMcrCore.RoiTrace> buildRois(Scan[] scans, RoiMcrCore.RoiOptions options,
      double progressStart, double progressEnd) {
    final RoiMcrCore.RoiBuilder builder = new RoiMcrCore.RoiBuilder(options);
    for (int scanIndex = 0; scanIndex < scans.length; scanIndex++) {
      checkCancelled();
      final MassSpectrum massList = scans[scanIndex].getMassList();
      final int size = massList.getNumberOfDataPoints();
      builder.acceptScan(scanIndex, massList.getMzValues(new double[size]),
          massList.getIntensityValues(new double[size]));
      if (progressEnd > progressStart) {
        progress = progressStart + (progressEnd - progressStart) * (scanIndex + 1d) / scans.length;
      }
    }
    return builder.finish();
  }

  private RoiMcrCore.RoiOptions roiOptions(MZTolerance tolerance) {
    return new RoiMcrCore.RoiOptions(tolerance.getMzTolerance(), tolerance.getPpmTolerance(),
        parameters.getValue(RoiMcrParameters.noiseLevel),
        parameters.getValue(RoiMcrParameters.seedIntensity),
        parameters.getValue(RoiMcrParameters.maximumMissingScans),
        parameters.getValue(RoiMcrParameters.minimumDataPoints),
        parameters.getValue(RoiMcrParameters.minimumConsecutiveScans),
        parameters.getValue(RoiMcrParameters.minimumFeatureHeight));
  }

  private RoiMcrCore.McrOptions mcrOptions() {
    return new RoiMcrCore.McrOptions(parameters.getValue(RoiMcrParameters.maximumComponents),
        parameters.getValue(RoiMcrParameters.restarts),
        parameters.getValue(RoiMcrParameters.maximumIterations),
        parameters.getValue(RoiMcrParameters.nnlsIterations),
        parameters.getValue(RoiMcrParameters.convergenceTolerance),
        parameters.getValue(RoiMcrParameters.minimumRankImprovement),
        parameters.getValue(RoiMcrParameters.minimumRestartStability),
        parameters.getValue(RoiMcrParameters.smoothingRadius),
        parameters.getValue(RoiMcrParameters.unimodalityFlexibility),
        parameters.getValue(RoiMcrParameters.spectralSparsity),
        parameters.getValue(RoiMcrParameters.minimumComponentIons),
        parameters.getValue(RoiMcrParameters.minimumComponentEnergy),
        parameters.getValue(RoiMcrParameters.maximumTemporalCosine), RANDOM_SEED);
  }

  private static RoiMcrCore.RoiOptions scale(RoiMcrCore.RoiOptions options, double factor) {
    return new RoiMcrCore.RoiOptions(options.absoluteMzTolerance(), options.ppmTolerance(),
        options.noiseLevel() * factor, options.seedIntensity() * factor,
        options.maximumMissingScans(), options.minimumDataPoints(),
        options.minimumConsecutiveScans(), options.minimumHeight() * factor);
  }

  private void validateRoiCount(List<RoiMcrCore.RoiTrace> rois) {
    final int minimum = parameters.getValue(RoiMcrParameters.minimumComponentIons);
    if (rois.size() < minimum) {
      throw new IllegalArgumentException("Only " + rois.size()
          + " valid m/z ROIs remained; at least " + minimum + " are required.");
    }
  }

  private static double[][] buildMatrix(int numberOfScans, List<RoiMcrCore.RoiTrace> rois) {
    final double[][] matrix = new double[numberOfScans][rois.size()];
    for (int roi = 0; roi < rois.size(); roi++) {
      for (int scan = rois.get(roi).startScan(); scan <= rois.get(roi).endScan(); scan++) {
        matrix[scan][roi] = rois.get(roi).intensityAt(scan);
      }
    }
    return matrix;
  }

  private static void validateScans(Scan[] scans) {
    if (scans.length == 0) {
      throw new IllegalArgumentException("No scans match the selected scan filter.");
    }
    final int level = scans[0].getMSLevel();
    final PolarityType polarity = scans[0].getPolarity();
    float previousRt = Float.NEGATIVE_INFINITY;
    for (Scan scan : scans) {
      if (scan.getMSLevel() != level) {
        throw new IllegalArgumentException("The scan filter must select exactly one MS level.");
      }
      if (scan.getPolarity() != polarity) {
        throw new IllegalArgumentException("The scan filter must select exactly one polarity.");
      }
      if (scan.getRetentionTime() < previousRt) {
        throw new IllegalArgumentException("Selected scans are not ordered by retention time.");
      }
      if (scan.getMassList() == null) {
        throw new IllegalArgumentException("Scan #" + scan.getScanNumber()
            + " has no mass list. Run mass detection before ROI-MCR.");
      }
      previousRt = scan.getRetentionTime();
    }
  }

  private void checkCancelled() {
    if (isCanceled()) {
      throw new CancellationException();
    }
  }

  private static double[] componentProfile(double[][] concentrations, int component) {
    final double[] profile = new double[concentrations.length];
    for (int scan = 0; scan < concentrations.length; scan++) {
      profile[scan] = concentrations[scan][component];
    }
    return profile;
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

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.6f", value);
  }

  private static String confidenceLabel(RoiMcrRobustness.Confidence confidence) {
    return confidence.name().toLowerCase(Locale.ROOT).replace('_', '-');
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

  private record Analysis(List<RoiMcrCore.RoiTrace> rois, RoiMcrCore.McrResult result,
                          double[][] restoredSpectra, double originalExplained) {

    Analysis {
      rois = List.copyOf(rois);
    }
  }
}
