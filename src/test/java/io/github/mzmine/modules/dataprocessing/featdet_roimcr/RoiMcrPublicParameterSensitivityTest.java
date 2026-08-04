/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.MZmineProcessingStep;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.auto.AutoMassDetectorParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.combowithinput.MsLevelFilter;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.ExitCode;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Public-data sensitivity sweep for ROI weighting and adaptive noise. */
class RoiMcrPublicParameterSensitivityTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Instant MODULE_DATE = Instant.parse("2026-08-04T00:00:00Z");
  private static final Pattern COMPONENT_PATTERN = Pattern.compile("component=(\\d+)");
  private static final Path REPORT = Path.of("build", "roi_mcr_public_validation",
      "roi_mcr_public_parameter_sensitivity.json").toAbsolutePath();
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void measuresWeightingAndNoiseSensitivityAtDominantPublicPeak() throws Exception {
    final Path samplePath = requiredFile(SAMPLE_ENV);
    final Path blankPath = requiredFile(BLANK_ENV);
    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);
    Files.createDirectories(REPORT.getParent());
    Files.deleteIfExists(REPORT);

    try {
      importMzml(project, samplePath, blankPath);
      final RawDataFile sample = findRaw(project, samplePath.getFileName().toString());
      final RawDataFile blank = findRaw(project, blankPath.getFileName().toString());
      runMassDetection(project, sample, 1_000d);
      runMassDetection(project, blank, 1_000d);

      final PolarityType polarity = majorityPolarity(sample);
      final Range<Double> common = commonRtRange(sample, blank, polarity);
      final double apexRt = dominantTicApex(sample, polarity, common);
      final Range<Double> window = Range.closed(Math.max(common.lowerEndpoint(), apexRt - 0.30),
          Math.min(common.upperEndpoint(), apexRt + 0.30));
      final ScanSelection selection = new ScanSelection(null, null, window, null, polarity,
          MassSpectrumType.ANY, MsLevelFilter.of(1), null);
      final double apexTic = maximumTic(sample, selection);
      final double baseNoise = Math.max(2_000d, apexTic * 1e-5);

      final double[] weightings = {0d, 0.5d, 1d};
      final double[] noiseMultipliers = {0.5d, 1d, 2d};
      final List<Map<String, Object>> settings = new ArrayList<>();
      int finishedPairs = 0;
      Execution baselineSample = null;
      Execution baselineBlank = null;

      for (double noiseMultiplier : noiseMultipliers) {
        final double noise = baseNoise * noiseMultiplier;
        final double seed = noise * 3d;
        for (double weighting : weightings) {
          final String label = "noise-" + noiseMultiplier + "-weight-" + weighting;
          final Execution sampleRun = runRoiMcr(project, sample, selection, noise, seed, weighting,
              "sensitivity-sample-" + label);
          final Execution blankRun = runRoiMcr(project, blank, selection, noise, seed, weighting,
              "sensitivity-blank-" + label);
          final Comparison comparison = compare(sampleRun.list(), blankRun.list(), 0.02, 0.12f);
          if (sampleRun.status() == TaskStatus.FINISHED
              && blankRun.status() == TaskStatus.FINISHED) {
            finishedPairs++;
          }
          if (noiseMultiplier == 1d && weighting == 0.5d) {
            baselineSample = sampleRun;
            baselineBlank = blankRun;
          }

          final Map<String, Object> result = new LinkedHashMap<>();
          result.put("noise_multiplier", noiseMultiplier);
          result.put("roi_noise", noise);
          result.put("seed_intensity", seed);
          result.put("weighting_exponent", weighting);
          result.put("sample", sampleRun.toMap());
          result.put("blank", blankRun.toMap());
          result.put("sample_vs_blank", comparison.toMap());
          result.put("maximum_height_ratio", ratio(sampleRun.maximumHeight(),
              blankRun.maximumHeight()));
          result.put("summed_area_ratio", ratio(sampleRun.summedArea(), blankRun.summedArea()));
          result.put("sample_only_fraction", sampleRun.rows() == 0 ? 0d
              : comparison.leftOnly() / (double) sampleRun.rows());
          settings.add(result);
        }
      }

      final Map<String, Object> report = new LinkedHashMap<>();
      report.put("schema_version", 1);
      report.put("method", "ROI-MCR sensitivity sweep on dominant public TIC region");
      report.put("sample", samplePath.toString());
      report.put("blank", blankPath.toString());
      report.put("polarity", polarity.toString());
      report.put("tic_apex_rt", apexRt);
      report.put("window_rt_min", window.lowerEndpoint());
      report.put("window_rt_max", window.upperEndpoint());
      report.put("sample_scans", selection.getMatchingScans(sample).length);
      report.put("blank_scans", selection.getMatchingScans(blank).length);
      report.put("sample_apex_tic", apexTic);
      report.put("base_roi_noise", baseNoise);
      report.put("finished_pairs", finishedPairs);
      report.put("settings", settings);
      report.put("success", finishedPairs >= 7 && baselineSample != null
          && baselineSample.status() == TaskStatus.FINISHED && baselineBlank != null
          && baselineBlank.status() == TaskStatus.FINISHED);
      MAPPER.writeValue(REPORT.toFile(), report);

      assertTrue(finishedPairs >= 7,
          "Too many parameter combinations failed on the public sample/blank pair");
      assertEquals(TaskStatus.FINISHED, baselineSample.status(), baselineSample.error());
      assertEquals(TaskStatus.FINISHED, baselineBlank.status(), baselineBlank.error());
      assertTrue(baselineSample.componentCount() > 0);
      assertTrue(baselineSample.rows() > 0);
      assertTrue(Files.size(REPORT) > 2_000);
      System.out.println("ROI_MCR_PUBLIC_PARAMETER_SENSITIVITY_REPORT=" + REPORT);
      System.out.println(MAPPER.writeValueAsString(report));
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
    }
  }

  private static Execution runRoiMcr(MZmineProject project, RawDataFile raw,
      ScanSelection selection, double noise, double seed, double weighting, String suffix) {
    final RoiMcrParameters parameters = new RoiMcrParameters();
    parameters.setParameter(RoiMcrParameters.scanSelection, selection);
    parameters.setParameter(RoiMcrParameters.suffix, suffix);
    parameters.setParameter(RoiMcrParameters.mzTolerance, new MZTolerance(0.01, 15));
    parameters.setParameter(RoiMcrParameters.noiseLevel, noise);
    parameters.setParameter(RoiMcrParameters.seedIntensity, seed);
    parameters.setParameter(RoiMcrParameters.minimumFeatureHeight, seed);
    parameters.setParameter(RoiMcrParameters.maximumMissingScans, 1);
    parameters.setParameter(RoiMcrParameters.minimumDataPoints, 4);
    parameters.setParameter(RoiMcrParameters.minimumConsecutiveScans, 3);
    parameters.setParameter(RoiMcrParameters.maximumComponents, 4);
    parameters.setParameter(RoiMcrParameters.restarts, 4);
    parameters.setParameter(RoiMcrParameters.maximumIterations, 220);
    parameters.setParameter(RoiMcrParameters.nnlsIterations, 50);
    parameters.setParameter(RoiMcrParameters.minimumRankImprovement, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumRestartStability, 0.65);
    parameters.setParameter(RoiMcrParameters.minimumComponentIons, 2);
    parameters.setParameter(RoiMcrParameters.minimumComponentEnergy, 0.001);
    parameters.setParameter(RoiMcrParameters.weightingExponent, weighting);
    parameters.setParameter(RoiMcrParameters.minimumSpectralContribution, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumAssignmentFraction, 0.50);

    final int before = project.getNumberOfFeatureLists();
    final RoiMcrTask task = new RoiMcrTask(project, raw, parameters, null, MODULE_DATE);
    final long started = System.nanoTime();
    task.run();
    final FeatureList list = project.getNumberOfFeatureLists() > before
        ? project.getCurrentFeatureLists().get(project.getNumberOfFeatureLists() - 1) : null;
    return summarize(task.getStatus(), task.getErrorMessage(), list, elapsedSeconds(started));
  }

  private static Execution summarize(TaskStatus status, String error, FeatureList list,
      double elapsed) {
    if (list == null) {
      return new Execution(status, error, null, 0, 0, 0d, 0d, Set.of(), elapsed);
    }
    double maximumHeight = 0;
    double summedArea = 0;
    final Set<Integer> components = new TreeSet<>();
    for (FeatureListRow row : list.getRows()) {
      if (row.getAverageHeight() != null) {
        maximumHeight = Math.max(maximumHeight, row.getAverageHeight());
      }
      if (row.getAverageArea() != null) {
        summedArea += row.getAverageArea();
      }
      final String comment = row.get(CommentType.class);
      if (comment != null) {
        final Matcher matcher = COMPONENT_PATTERN.matcher(comment);
        if (matcher.find()) {
          components.add(Integer.parseInt(matcher.group(1)));
        }
      }
    }
    return new Execution(status, error, list, list.getNumberOfRows(), components.size(),
        maximumHeight, summedArea, components, elapsed);
  }

  private static Comparison compare(FeatureList left, FeatureList right, double mzTolerance,
      float rtTolerance) {
    final int leftRows = left == null ? 0 : left.getNumberOfRows();
    final int rightRows = right == null ? 0 : right.getNumberOfRows();
    int matched = 0;
    if (left != null && right != null) {
      for (FeatureListRow leftRow : left.getRows()) {
        if (right.getRows().stream().anyMatch(rightRow ->
            Math.abs(leftRow.getAverageMZ() - rightRow.getAverageMZ()) <= mzTolerance
                && Math.abs(leftRow.getAverageRT() - rightRow.getAverageRT()) <= rtTolerance)) {
          matched++;
        }
      }
    }
    return new Comparison(leftRows, rightRows, matched, leftRows - matched, mzTolerance,
        rtTolerance);
  }

  private static void importMzml(MZmineProject project, Path sample, Path blank) {
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.setParameter(MSDKmzMLImportParameters.fileNames,
        new File[]{sample.toFile().getAbsoluteFile(), blank.toFile().getAbsoluteFile()});
    runModule(new MSDKmzMLImportModule(), project, parameters);
  }

  private static void runMassDetection(MZmineProject project, RawDataFile raw, double noise) {
    final AutoMassDetectorParameters detectorParameters = new AutoMassDetectorParameters();
    detectorParameters.setParameter(AutoMassDetectorParameters.noiseLevel, noise);
    final MZmineProcessingStep<MassDetector> detector = new MZmineProcessingStepImpl<>(
        MassDetectionParameters.auto, detectorParameters);
    final MassDetectionParameters parameters = new MassDetectionParameters();
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.massDetector, detector);
    runModule(new MassDetectionModule(), project, parameters);
  }

  private static void runModule(MZmineProcessingModule module, MZmineProject project,
      ParameterSet parameters) {
    final List<Task> tasks = new ArrayList<>();
    assertEquals(ExitCode.OK, module.runModule(project, parameters, tasks, MODULE_DATE));
    assertFalse(tasks.isEmpty());
    for (Task task : tasks) {
      task.run();
      assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    }
  }

  private static double dominantTicApex(RawDataFile raw, PolarityType polarity,
      Range<Double> range) {
    double maximum = -1;
    double apex = Double.NaN;
    for (Scan scan : raw.getScans()) {
      if (scan.getMSLevel() != 1 || scan.getPolarity() != polarity
          || !range.contains((double) scan.getRetentionTime())) {
        continue;
      }
      final double tic = tic(scan);
      if (tic > maximum) {
        maximum = tic;
        apex = scan.getRetentionTime();
      }
    }
    assertTrue(Double.isFinite(apex));
    return apex;
  }

  private static double maximumTic(RawDataFile raw, ScanSelection selection) {
    double maximum = 0;
    for (Scan scan : selection.getMatchingScans(raw)) {
      maximum = Math.max(maximum, tic(scan));
    }
    return maximum;
  }

  private static double tic(Scan scan) {
    if (scan.getMassList() == null) {
      return 0;
    }
    final int size = scan.getMassList().getNumberOfDataPoints();
    double sum = 0;
    for (double intensity : scan.getMassList().getIntensityValues(new double[size])) {
      sum += intensity;
    }
    return sum;
  }

  private static PolarityType majorityPolarity(RawDataFile raw) {
    return raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .collect(java.util.stream.Collectors.groupingBy(Scan::getPolarity,
            java.util.stream.Collectors.counting())).entrySet().stream()
        .max(Map.Entry.<PolarityType, Long>comparingByValue()).orElseThrow().getKey();
  }

  private static Range<Double> commonRtRange(RawDataFile sample, RawDataFile blank,
      PolarityType polarity) {
    final Range<Double> sampleRange = rtRange(sample, polarity);
    final Range<Double> blankRange = rtRange(blank, polarity);
    return Range.closed(Math.max(sampleRange.lowerEndpoint(), blankRange.lowerEndpoint()),
        Math.min(sampleRange.upperEndpoint(), blankRange.upperEndpoint()));
  }

  private static Range<Double> rtRange(RawDataFile raw, PolarityType polarity) {
    final List<Double> rts = raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity)
        .map(scan -> (double) scan.getRetentionTime()).sorted().toList();
    assertFalse(rts.isEmpty());
    return Range.closed(rts.get(0), rts.get(rts.size() - 1));
  }

  private static RawDataFile findRaw(MZmineProject project, String name) {
    return project.getCurrentRawDataFiles().stream().filter(raw -> raw.getName().equals(name))
        .findFirst().orElseThrow();
  }

  private static Path requiredFile(String environment) {
    final String value = System.getenv(environment);
    Assumptions.assumeTrue(value != null && !value.isBlank(),
        "Public fixture environment variable is not set: " + environment);
    final Path path = Path.of(value).toAbsolutePath();
    Assumptions.assumeTrue(Files.isRegularFile(path), "Public fixture is unavailable: " + path);
    return path;
  }

  private static double ratio(double numerator, double denominator) {
    return denominator <= 0 ? Double.POSITIVE_INFINITY : numerator / denominator;
  }

  private static double elapsedSeconds(long started) {
    return Math.round((System.nanoTime() - started) / 1_000_000d) / 1_000d;
  }

  private record Execution(TaskStatus status, String error, FeatureList list, int rows,
                           int componentCount, double maximumHeight, double summedArea,
                           Set<Integer> componentIds, double elapsedSeconds) {

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", status == null ? null : status.toString());
      map.put("error", error);
      map.put("feature_list", list == null ? null : list.getName());
      map.put("rows", rows);
      map.put("component_count", componentCount);
      map.put("component_ids", componentIds);
      map.put("maximum_height", maximumHeight);
      map.put("summed_area", summedArea);
      map.put("elapsed_seconds", elapsedSeconds);
      return map;
    }
  }

  private record Comparison(int leftRows, int rightRows, int matched, int leftOnly,
                            double mzTolerance, float rtTolerance) {

    Map<String, Object> toMap() {
      return Map.of("left_rows", leftRows, "right_rows", rightRows, "matched", matched,
          "left_only", leftOnly, "mz_tolerance", mzTolerance, "rt_tolerance", rtTolerance);
    }
  }
}
