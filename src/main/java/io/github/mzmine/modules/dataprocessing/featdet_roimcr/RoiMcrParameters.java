/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.impl.SimpleParameterSet;
import io.github.mzmine.parameters.parametertypes.DoubleParameter;
import io.github.mzmine.parameters.parametertypes.IntegerParameter;
import io.github.mzmine.parameters.parametertypes.StringParameter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesParameter;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelectionParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import io.github.mzmine.parameters.parametertypes.tolerances.ToleranceType;

/** Parameters for direct scan-to-feature ROI-MCR processing. */
public final class RoiMcrParameters extends SimpleParameterSet {

  public static final RawDataFilesParameter dataFiles = new RawDataFilesParameter();
  public static final ScanSelectionParameter scanSelection = new ScanSelectionParameter(
      new ScanSelection(1));
  public static final StringParameter suffix = new StringParameter("Suffix",
      "Suffix appended to the generated feature list.", "roi-mcr");
  public static final MZToleranceParameter mzTolerance = new MZToleranceParameter(
      ToleranceType.SCAN_TO_SCAN, 0.01, 10);
  public static final DoubleParameter noiseLevel = new DoubleParameter("ROI noise level",
      "Centroid signals below this intensity are ignored.",
      MZmineCore.getConfiguration().getIntensityFormat(), 100d, 0d, null);
  public static final DoubleParameter seedIntensity = new DoubleParameter("ROI seed intensity",
      "Minimum intensity required to start an ROI.",
      MZmineCore.getConfiguration().getIntensityFormat(), 500d, 0d, null);
  public static final DoubleParameter minimumFeatureHeight = new DoubleParameter(
      "Minimum resolved feature height", "Minimum reconstructed ion height.",
      MZmineCore.getConfiguration().getIntensityFormat(), 500d, 0d, null);
  public static final IntegerParameter maximumMissingScans = new IntegerParameter(
      "Maximum missing scans", "Allowed gaps while tracking an ROI.", 1, 0, 100);
  public static final IntegerParameter minimumDataPoints = new IntegerParameter(
      "Minimum ROI data points", "Minimum number of detected centroid points.", 4, 2, null);
  public static final IntegerParameter minimumConsecutiveScans = new IntegerParameter(
      "Minimum consecutive scans", "Minimum uninterrupted run for an ROI.", 3, 2, null);
  public static final IntegerParameter maximumComponents = new IntegerParameter(
      "Maximum MCR components", "Maximum local rank evaluated.", 4, 1, 7);
  public static final IntegerParameter restarts = new IntegerParameter("MCR restarts",
      "Independent initializations used to assess solution stability.", 6, 2, 30);
  public static final IntegerParameter maximumIterations = new IntegerParameter(
      "Maximum MCR iterations", "Maximum alternating least-squares iterations.", 400, 20, 5000);
  public static final IntegerParameter nnlsIterations = new IntegerParameter("NNLS iterations",
      "Coordinate-descent iterations in each non-negative least-squares update.", 60, 5, 1000);
  public static final DoubleParameter convergenceTolerance = new DoubleParameter(
      "Convergence tolerance", "Relative change in model error required for convergence.",
      MZmineCore.getConfiguration().getScoreFormat(), 1e-6, 1e-12, 0.1);
  public static final DoubleParameter minimumRankImprovement = new DoubleParameter(
      "Minimum rank improvement", "Minimum additional explained-signal fraction.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.015, 0d, 1d);
  public static final DoubleParameter minimumRestartStability = new DoubleParameter(
      "Minimum restart stability", "Minimum temporal/spectral reproducibility between restarts.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.75, 0d, 1d);
  public static final IntegerParameter smoothingRadius = new IntegerParameter(
      "Smoothing radius", "Moving-average radius for chromatographic profiles.", 1, 0, 20);
  public static final DoubleParameter unimodalityFlexibility = new DoubleParameter(
      "Unimodality flexibility", "Fraction of the unconstrained chromatographic profile retained.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.05, 0d, 1d);
  public static final DoubleParameter spectralSparsity = new DoubleParameter(
      "Spectral sparsity", "Loadings below this fraction of a component maximum are removed.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.002, 0d, 1d);
  public static final IntegerParameter minimumComponentIons = new IntegerParameter(
      "Minimum component ions", "Minimum reconstructed ions in one component.", 2, 1, null);
  public static final DoubleParameter minimumComponentEnergy = new DoubleParameter(
      "Minimum component energy", "Minimum fraction of summed component energy.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.001, 0d, 1d);
  public static final DoubleParameter maximumTemporalCosine = new DoubleParameter(
      "Maximum temporal cosine", "Rejects nearly duplicate component profiles.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.999, 0d, 1d);
  public static final DoubleParameter weightingExponent = new DoubleParameter(
      "ROI weighting exponent",
      "0 preserves intensity, 0.5 is Pareto weighting, and 1 is aggressive unit-maximum weighting.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.5, 0d, 1d);
  public static final DoubleParameter robustnessNoiseFactor = new DoubleParameter(
      "Robustness noise factor",
      "Builds lower- and higher-noise ROI models using 1/factor and factor perturbations.",
      MZmineCore.getConfiguration().getScoreFormat(), 1.5, 1.01, 4d);
  public static final DoubleParameter robustnessWeightingStep = new DoubleParameter(
      "Robustness weighting step",
      "Fits neighboring ROI-weighting models at exponent minus/plus this step.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.1, 0d, 0.5);
  public static final DoubleParameter minimumPerturbationSimilarity = new DoubleParameter(
      "Minimum perturbation similarity",
      "Minimum combined chromatographic, spectral, and apex similarity for component support.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.70, 0d, 1d);
  public static final DoubleParameter minimumPerturbationSupport = new DoubleParameter(
      "Minimum perturbation support",
      "Minimum fraction of neighboring models that must reproduce a stable component.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.75, 0d, 1d);
  public static final DoubleParameter minimumSpectralContribution = new DoubleParameter(
      "Minimum spectral contribution", "Minimum loading relative to the strongest component ion.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.01, 0d, 1d);
  public static final DoubleParameter minimumAssignmentFraction = new DoubleParameter(
      "Minimum component assignment", "Minimum fraction of an ROI loading assigned to a component.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.55, 0d, 1d);
  public static final DoubleParameter componentEdgeFraction = new DoubleParameter(
      "Component edge fraction", "Crop profiles below this fraction of the component apex.",
      MZmineCore.getConfiguration().getScoreFormat(), 0.01, 0d, 1d);

  public RoiMcrParameters() {
    super(new Parameter[]{dataFiles, scanSelection, suffix, mzTolerance, noiseLevel, seedIntensity,
            minimumFeatureHeight, maximumMissingScans, minimumDataPoints,
            minimumConsecutiveScans, maximumComponents, restarts, maximumIterations,
            nnlsIterations, convergenceTolerance, minimumRankImprovement,
            minimumRestartStability, smoothingRadius, unimodalityFlexibility, spectralSparsity,
            minimumComponentIons, minimumComponentEnergy, maximumTemporalCosine,
            weightingExponent, robustnessNoiseFactor, robustnessWeightingStep,
            minimumPerturbationSimilarity, minimumPerturbationSupport,
            minimumSpectralContribution, minimumAssignmentFraction, componentEdgeFraction},
        "https://github.com/VitorFrost/mzmine/blob/open-offline-main/docs/ROI_MCR_FEATURE_DETECTION.md");
  }
}
