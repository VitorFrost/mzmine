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
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
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
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.ExitCode;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Public whole-run benchmark for bounded local MCR fitting at 64, 128, and 256 ROIs/window. */
class RoiMcrPublicLocalFittingBenchmarkTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Instant MODULE_DATE = Instant.parse("2026-08-04T00:00:00Z");
  private static final Path REPORT = Path.of("build", "roi_mcr_public_validation",
      "roi_mcr_public_local_fitting_benchmark.json").toAbsolutePath();
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private static final int MAXIMUM_RETAINED_ROIS = 8_000;
  private static final int[] LIMITS = {64, 128, 256};
  private static final RoiMcrWindows.Options WINDOW_OPTIONS = new RoiMcrWindows.Options(2, 4, 12,
      120, 2, 0.05);
  private static final RoiMcrCore.McrOptions MCR_OPTIONS = new RoiMcrCore.McrOptions(4, 2, 120,
      40, 1e-6, 0.01, 0.55, 1, 0.05, 0.005, 2, 0.001, 0.9995, 20260805L);

  @Test
  void benchmarksLocalFitsAcrossThreeMatrixWidths() throws Exception {
    final Path samplePath = requiredFile(SAMPLE_ENV);
    final Path blankPath = requiredFile(BLANK_ENV);
    Files.createDirectories(REPORT.getParent());
    Files.deleteIfExists(REPORT);

    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);
    try {
      importMzml(project, samplePath, blankPath);
      final RawDataFile sample = findRaw(project, samplePath.getFileName().toString());
      final RawDataFile blank = findRaw(project, blankPath.getFileName().toString());
      runMassDetection(project, sample, 1_000d);
      runMassDetection(project, blank, 1_000d);

      final PolarityType polarity = majorityPolarity(sample);
      final Range<Double> common = commonRtRange(sample, blank, polarity);
      final Scan[] sampleScans = matchingScans(sample, polarity, common);
      final Scan[] blankScans = matchingScans(blank, polarity, common);
      final double roiNoise = Math.max(2_000d, maximumTic(sampleScans) * 1e-5);
      final RoiMcrCore.RoiOptions roiOptions = new RoiMcrCore.RoiOptions(0.01, 15, roiNoise,
          roiNoise * 3d, 1, 4, 3, roiNoise * 3d);

      final Dataset sampleDataset = prepare(sampleScans, roiOptions);
      final Dataset blankDataset = prepare(blankScans, roiOptions);
      final List<Map<String, Object>> limits = new ArrayList<>();
      int successfulDatasetFits = 0;
      for (int limit : LIMITS) {
        final Benchmark sampleBenchmark = benchmark(sampleDataset, limit);
        final Benchmark blankBenchmark = benchmark(blankDataset, limit);
        if (sampleBenchmark.success() && blankBenchmark.success()) {
          successfulDatasetFits++;
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("maximum_rois_per_window", limit);
        result.put("sample", sampleBenchmark.toMap());
        result.put("blank", blankBenchmark.toMap());
        result.put("sample_to_blank_component_ratio", ratio(sampleBenchmark.components(),
            blankBenchmark.components()));
        result.put("sample_to_blank_elapsed_ratio", ratio(sampleBenchmark.elapsedSeconds(),
            blankBenchmark.elapsedSeconds()));
        limits.add(result);
      }

      final Map<String, Object> report = new LinkedHashMap<>();
      report.put("schema_version", 1);
      report.put("method", "Full common range -> primitive ROIs -> bounded windows -> local MCR");
      report.put("sample", samplePath.toString());
      report.put("blank", blankPath.toString());
      report.put("polarity", polarity.toString());
      report.put("common_rt_min", common.lowerEndpoint());
      report.put("common_rt_max", common.upperEndpoint());
      report.put("roi_noise", roiNoise);
      report.put("mcr_restarts", MCR_OPTIONS.restarts());
      report.put("mcr_maximum_iterations", MCR_OPTIONS.maximumIterations());
      report.put("sample_preparation", sampleDataset.toMap());
      report.put("blank_preparation", blankDataset.toMap());
      report.put("limits", limits);
      report.put("successful_dataset_pairs", successfulDatasetFits);
      report.put("success", successfulDatasetFits == LIMITS.length);
      MAPPER.writeValue(REPORT.toFile(), report);
      System.out.println("ROI_MCR_PUBLIC_LOCAL_FITTING_REPORT=" + REPORT);
      System.out.println(MAPPER.writeValueAsString(report));

      assertEquals(LIMITS.length, successfulDatasetFits,
          "At least one public sample/blank local-fitting benchmark failed");
      assertTrue(Files.size(REPORT) > 3_000);
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
    }
  }

  private static Dataset prepare(Scan[] scans, RoiMcrCore.RoiOptions options) {
    final long started = System.nanoTime();
    final RoiMcrMemorySafeBuilder builder = new RoiMcrMemorySafeBuilder(options,
        MAXIMUM_RETAINED_ROIS);
    for (int index = 0; index < scans.length; index++) {
      final int size = scans[index].getMassList().getNumberOfDataPoints();
      builder.acceptScan(index, scans[index].getMassList().getMzValues(new double[size]),
          scans[index].getMassList().getIntensityValues(new double[size]));
    }
    final RoiMcrMemorySafeBuilder.Result result = builder.finish();
    assertEquals(0, result.statistics().discardedByLimit(),
        "ROI retention limit truncated valid public candidates");
    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(scans.length, result.traces(),
        WINDOW_OPTIONS);
    assertFalse(windows.isEmpty());
    return new Dataset(scans.length, result.traces(), result.statistics(), windows,
        elapsedSeconds(started));
  }

  private static Benchmark benchmark(Dataset dataset, int limit) {
    final RoiMcrLocalFitter.Summary summary = RoiMcrLocalFitter.fit(dataset.windows(),
        dataset.rois(), MCR_OPTIONS, new RoiMcrLocalFitter.Options(limit, 0.5), () -> false);
    int components = 0;
    double explainedSum = 0;
    double restartStabilitySum = 0;
    final Map<Integer, Integer> ranks = new java.util.TreeMap<>();
    for (RoiMcrLocalFitter.WindowFit fit : summary.fits()) {
      components += fit.result().rank();
      explainedSum += fit.originalExplained();
      restartStabilitySum += fit.result().restartStability();
      ranks.merge(fit.result().rank(), 1, Integer::sum);
    }
    final int fitted = summary.fits().size();
    final double failureFraction = dataset.windows().isEmpty() ? 1
        : summary.failedWindows() / (double) dataset.windows().size();
    final boolean success = fitted > 0 && failureFraction <= 0.10;
    return new Benchmark(success, dataset.windows().size(), fitted, summary.failedWindows(),
        failureFraction, components, ranks, fitted == 0 ? 0 : explainedSum / fitted,
        fitted == 0 ? 0 : restartStabilitySum / fitted, summary.maximumInputRois(),
        summary.maximumSelectedRois(), summary.totalInputRois(), summary.totalSelectedRois(),
        summary.elapsedNanos() / 1_000_000_000d);
  }

  private static Scan[] matchingScans(RawDataFile raw, PolarityType polarity,
      Range<Double> range) {
    return raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity)
        .filter(scan -> range.contains((double) scan.getRetentionTime()))
        .sorted(Comparator.comparingDouble(Scan::getRetentionTime)).toArray(Scan[]::new);
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

  private static double maximumTic(Scan[] scans) {
    double maximum = 0;
    for (Scan scan : scans) {
      final int size = scan.getMassList().getNumberOfDataPoints();
      double sum = 0;
      for (double intensity : scan.getMassList().getIntensityValues(new double[size])) {
        sum += intensity;
      }
      maximum = Math.max(maximum, sum);
    }
    return maximum;
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
    assertTrue(Files.isRegularFile(path), "Public fixture is unavailable: " + path);
    return path;
  }

  private static double elapsedSeconds(long started) {
    return Math.round((System.nanoTime() - started) / 1_000_000d) / 1_000d;
  }

  private static double ratio(double numerator, double denominator) {
    return denominator == 0 ? (numerator == 0 ? 1 : Double.POSITIVE_INFINITY)
        : numerator / denominator;
  }

  private record Dataset(int scans, List<RoiMcrCore.RoiTrace> rois,
                         RoiMcrMemorySafeBuilder.Statistics statistics,
                         List<RoiMcrWindows.Window> windows, double elapsedSeconds) {

    Dataset {
      rois = List.copyOf(rois);
      windows = List.copyOf(windows);
    }

    Map<String, Object> toMap() {
      return Map.of("scans", scans, "rois", rois.size(), "windows", windows.size(),
          "discarded_by_limit", statistics.discardedByLimit(),
          "maximum_active_rois", statistics.maximumActiveRois(),
          "maximum_points_in_one_roi", statistics.maximumPointsInOneRoi(),
          "elapsed_seconds", elapsedSeconds);
    }
  }

  private record Benchmark(boolean success, int windows, int fittedWindows, int failedWindows,
                           double failureFraction, int components, Map<Integer, Integer> rankCounts,
                           double meanOriginalExplained, double meanRestartStability,
                           int maximumInputRois, int maximumSelectedRois, int totalInputRois,
                           int totalSelectedRois, double elapsedSeconds) {

    Benchmark {
      rankCounts = Map.copyOf(rankCounts);
    }

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("success", success);
      map.put("windows", windows);
      map.put("fitted_windows", fittedWindows);
      map.put("failed_windows", failedWindows);
      map.put("failure_fraction", failureFraction);
      map.put("components", components);
      map.put("rank_counts", rankCounts);
      map.put("mean_original_explained", meanOriginalExplained);
      map.put("mean_restart_stability", meanRestartStability);
      map.put("maximum_input_rois", maximumInputRois);
      map.put("maximum_selected_rois", maximumSelectedRois);
      map.put("total_input_rois", totalInputRois);
      map.put("total_selected_rois", totalSelectedRois);
      map.put("elapsed_seconds", elapsedSeconds);
      return map;
    }
  }
}
