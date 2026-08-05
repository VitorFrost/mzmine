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

/** Characterizes complete-link reconciliation of public local-window ROI-MCR components. */
class RoiMcrPublicReconciliationBenchmarkTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Instant MODULE_DATE = Instant.parse("2026-08-04T00:00:00Z");
  private static final Path REPORT = Path.of("build", "roi_mcr_public_validation",
      "roi_mcr_public_reconciliation_benchmark.json").toAbsolutePath();
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private static final RoiMcrWindows.Options WINDOW_OPTIONS = new RoiMcrWindows.Options(2, 4, 12,
      120, 2, 0.05);
  private static final RoiMcrCore.McrOptions MCR_OPTIONS = new RoiMcrCore.McrOptions(4, 2, 120,
      40, 1e-6, 0.01, 0.55, 1, 0.05, 0.005, 2, 0.001, 0.9995, 20260805L);
  private static final double[] SIMILARITIES = {0.75, 0.85, 0.90};

  @Test
  void characterizesReconciliationAtNeighboringSimilarities() throws Exception {
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
      final double noise = Math.max(2_000d, maximumTic(sampleScans) * 1e-5);
      final RoiMcrCore.RoiOptions roiOptions = new RoiMcrCore.RoiOptions(0.01, 15, noise,
          noise * 3d, 1, 4, 3, noise * 3d);

      final FittedDataset sampleFitted = fit(sampleScans, roiOptions);
      final FittedDataset blankFitted = fit(blankScans, roiOptions);
      final List<Map<String, Object>> thresholds = new ArrayList<>();
      for (double similarity : SIMILARITIES) {
        final ReconciliationSummary sampleSummary = reconcile(sampleFitted, similarity);
        final ReconciliationSummary blankSummary = reconcile(blankFitted, similarity);
        final Map<String, Object> threshold = new LinkedHashMap<>();
        threshold.put("minimum_similarity", similarity);
        threshold.put("sample", sampleSummary.toMap());
        threshold.put("blank", blankSummary.toMap());
        threshold.put("sample_to_blank_group_ratio", ratio(sampleSummary.groups(),
            blankSummary.groups()));
        thresholds.add(threshold);
      }

      final Map<String, Object> report = new LinkedHashMap<>();
      report.put("schema_version", 1);
      report.put("method", "64-ROI local fits -> provisional complete-link reconciliation");
      report.put("warning", "Perturbation stability is not yet fitted per local window; restart "
          + "stability is used only for provisional representative quality ordering.");
      report.put("sample", samplePath.toString());
      report.put("blank", blankPath.toString());
      report.put("polarity", polarity.toString());
      report.put("common_rt_min", common.lowerEndpoint());
      report.put("common_rt_max", common.upperEndpoint());
      report.put("sample_fit", sampleFitted.toMap());
      report.put("blank_fit", blankFitted.toMap());
      report.put("maximum_apex_distance_scans", 12);
      report.put("absolute_mz_tolerance", 0.01);
      report.put("ppm_tolerance", 15d);
      report.put("thresholds", thresholds);
      report.put("success", true);
      MAPPER.writeValue(REPORT.toFile(), report);
      System.out.println("ROI_MCR_PUBLIC_RECONCILIATION_REPORT=" + REPORT);
      System.out.println(MAPPER.writeValueAsString(report));

      assertFalse(sampleFitted.candidates().isEmpty());
      assertFalse(blankFitted.candidates().isEmpty());
      assertTrue(Files.size(REPORT) > 2_000);
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
    }
  }

  private static FittedDataset fit(Scan[] scans, RoiMcrCore.RoiOptions options) {
    final RoiMcrMemorySafeBuilder builder = new RoiMcrMemorySafeBuilder(options, 8_000);
    for (int index = 0; index < scans.length; index++) {
      final int size = scans[index].getMassList().getNumberOfDataPoints();
      builder.acceptScan(index, scans[index].getMassList().getMzValues(new double[size]),
          scans[index].getMassList().getIntensityValues(new double[size]));
    }
    final RoiMcrMemorySafeBuilder.Result roiResult = builder.finish();
    assertEquals(0, roiResult.statistics().discardedByLimit());
    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(scans.length,
        roiResult.traces(), WINDOW_OPTIONS);
    final RoiMcrLocalFitter.Summary fits = RoiMcrLocalFitter.fit(windows, roiResult.traces(),
        MCR_OPTIONS, new RoiMcrLocalFitter.Options(64, 0.5), () -> false);
    assertEquals(0, fits.failedWindows());
    final List<RoiMcrReconciliation.Candidate> candidates = new ArrayList<>();
    for (RoiMcrLocalFitter.WindowFit fit : fits.fits()) {
      for (int component = 0; component < fit.signatures().size(); component++) {
        final RoiMcrRobustness.Signature signature = fit.signatures().get(component);
        final int apex = fit.startScan() + (int) Math.round(signature.apexFraction()
            * Math.max(0, fit.endScan() - fit.startScan()));
        candidates.add(new RoiMcrReconciliation.Candidate(fit.windowId(), component + 1,
            fit.startScan(), fit.endScan(), apex, signature, fit.result().restartStability(), 1d,
            fit.result().restartStability(), fit.originalExplained()));
      }
    }
    return new FittedDataset(scans.length, roiResult.traces().size(), windows.size(), fits,
        candidates);
  }

  private static ReconciliationSummary reconcile(FittedDataset fitted, double similarity) {
    final List<RoiMcrReconciliation.Group> groups = RoiMcrReconciliation.reconcile(
        fitted.candidates(), 12, similarity, 0.01, 15);
    int duplicateGroups = 0;
    int duplicateMembers = 0;
    int maximumGroupSize = 0;
    double minimumSimilarity = 1;
    for (RoiMcrReconciliation.Group group : groups) {
      maximumGroupSize = Math.max(maximumGroupSize, group.members().size());
      minimumSimilarity = Math.min(minimumSimilarity, group.minimumRepresentativeSimilarity());
      if (group.isReconciledDuplicate()) {
        duplicateGroups++;
        duplicateMembers += group.members().size();
      }
    }
    return new ReconciliationSummary(fitted.candidates().size(), groups.size(), duplicateGroups,
        duplicateMembers, fitted.candidates().size() - groups.size(), maximumGroupSize,
        groups.isEmpty() ? 0 : minimumSimilarity);
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

  private static double ratio(double numerator, double denominator) {
    return denominator == 0 ? (numerator == 0 ? 1 : Double.POSITIVE_INFINITY)
        : numerator / denominator;
  }

  private record FittedDataset(int scans, int rois, int windows,
                               RoiMcrLocalFitter.Summary fits,
                               List<RoiMcrReconciliation.Candidate> candidates) {

    FittedDataset {
      candidates = List.copyOf(candidates);
    }

    Map<String, Object> toMap() {
      return Map.of("scans", scans, "rois", rois, "windows", windows,
          "fitted_windows", fits.fits().size(), "failed_windows", fits.failedWindows(),
          "raw_components", candidates.size(), "elapsed_seconds",
          fits.elapsedNanos() / 1_000_000_000d);
    }
  }

  private record ReconciliationSummary(int rawComponents, int groups, int duplicateGroups,
                                       int duplicateMembers, int removedDuplicates,
                                       int maximumGroupSize, double minimumGroupSimilarity) {

    Map<String, Object> toMap() {
      return Map.of("raw_components", rawComponents, "groups", groups,
          "duplicate_groups", duplicateGroups, "duplicate_members", duplicateMembers,
          "removed_duplicates", removedDuplicates, "maximum_group_size", maximumGroupSize,
          "minimum_group_similarity", minimumGroupSimilarity);
    }
  }
}
