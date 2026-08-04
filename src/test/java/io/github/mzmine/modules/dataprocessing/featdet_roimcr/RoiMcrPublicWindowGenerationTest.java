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

/** Characterizes automatic local windows before they are enabled in production feature creation. */
class RoiMcrPublicWindowGenerationTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Instant MODULE_DATE = Instant.parse("2026-08-04T00:00:00Z");
  private static final Path REPORT = Path.of("build", "roi_mcr_public_validation",
      "roi_mcr_public_window_generation.json").toAbsolutePath();
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private static final RoiMcrWindows.Options WINDOW_OPTIONS = new RoiMcrWindows.Options(2, 4, 12,
      120, 2, 0.05);

  @Test
  void characterizesFullRangeWindowsInSampleAndBlank() throws Exception {
    final Path samplePath = requiredFile(SAMPLE_ENV);
    final Path blankPath = requiredFile(BLANK_ENV);
    Files.createDirectories(REPORT.getParent());
    Files.deleteIfExists(REPORT);

    final MZmineProject project = new MZmineProjectImpl();
    importMzml(project, samplePath, blankPath);
    final RawDataFile sample = findRaw(project, samplePath.getFileName().toString());
    final RawDataFile blank = findRaw(project, blankPath.getFileName().toString());
    runMassDetection(project, sample, 1_000d);
    runMassDetection(project, blank, 1_000d);

    final PolarityType polarity = majorityPolarity(sample);
    final Range<Double> common = commonRtRange(sample, blank, polarity);
    final Scan[] sampleScans = matchingScans(sample, polarity, common);
    final Scan[] blankScans = matchingScans(blank, polarity, common);
    final double apexTic = maximumTic(sampleScans);
    final double roiNoise = Math.max(2_000d, apexTic * 1e-5);
    final double seed = roiNoise * 3d;
    final RoiMcrCore.RoiOptions roiOptions = new RoiMcrCore.RoiOptions(0.01, 15, roiNoise, seed,
        1, 4, 3, seed);

    final WindowSummary sampleSummary = characterize(sampleScans, roiOptions);
    final WindowSummary blankSummary = characterize(blankScans, roiOptions);
    assertValid("sample", sampleSummary);
    assertValid("blank", blankSummary);

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 1);
    report.put("method", "Full common negative-mode range -> persistent ROIs -> bounded local windows");
    report.put("sample", samplePath.toString());
    report.put("blank", blankPath.toString());
    report.put("polarity", polarity.toString());
    report.put("common_rt_min", common.lowerEndpoint());
    report.put("common_rt_max", common.upperEndpoint());
    report.put("roi_noise", roiNoise);
    report.put("seed_intensity", seed);
    report.put("window_options", Map.of(
        "maximum_gap_scans", WINDOW_OPTIONS.maximumGapScans(),
        "padding_scans", WINDOW_OPTIONS.paddingScans(),
        "minimum_window_scans", WINDOW_OPTIONS.minimumWindowScans(),
        "maximum_window_scans", WINDOW_OPTIONS.maximumWindowScans(),
        "minimum_rois", WINDOW_OPTIONS.minimumRois(),
        "active_fraction", WINDOW_OPTIONS.activeFraction()));
    report.put("sample_summary", sampleSummary.toMap(sampleScans));
    report.put("blank_summary", blankSummary.toMap(blankScans));
    report.put("success", true);
    MAPPER.writeValue(REPORT.toFile(), report);

    assertTrue(Files.size(REPORT) > 1_000);
    System.out.println("ROI_MCR_PUBLIC_WINDOW_REPORT=" + REPORT);
    System.out.println(MAPPER.writeValueAsString(report));
  }

  private static WindowSummary characterize(Scan[] scans, RoiMcrCore.RoiOptions roiOptions) {
    final RoiMcrCore.RoiBuilder builder = new RoiMcrCore.RoiBuilder(roiOptions);
    for (int index = 0; index < scans.length; index++) {
      final int size = scans[index].getMassList().getNumberOfDataPoints();
      builder.acceptScan(index, scans[index].getMassList().getMzValues(new double[size]),
          scans[index].getMassList().getIntensityValues(new double[size]));
    }
    final List<RoiMcrCore.RoiTrace> rois = builder.finish();
    final List<RoiMcrWindows.Window> windows = RoiMcrWindows.build(scans.length, rois,
        WINDOW_OPTIONS);
    final boolean[] covered = new boolean[scans.length];
    int overlapScans = 0;
    int maximumRois = 0;
    int maximumScans = 0;
    int minimumScans = Integer.MAX_VALUE;
    double totalRois = 0;
    for (RoiMcrWindows.Window window : windows) {
      maximumRois = Math.max(maximumRois, window.roiIndices().size());
      maximumScans = Math.max(maximumScans, window.scanCount());
      minimumScans = Math.min(minimumScans, window.scanCount());
      totalRois += window.roiIndices().size();
      for (int scan = window.startScan(); scan <= window.endScan(); scan++) {
        if (covered[scan]) {
          overlapScans++;
        }
        covered[scan] = true;
      }
    }
    int coveredScans = 0;
    for (boolean value : covered) {
      if (value) {
        coveredScans++;
      }
    }
    return new WindowSummary(rois.size(), windows, coveredScans, overlapScans,
        windows.isEmpty() ? 0 : minimumScans, maximumScans, maximumRois,
        windows.isEmpty() ? 0 : totalRois / windows.size());
  }

  private static void assertValid(String label, WindowSummary summary) {
    assertTrue(summary.roiCount() >= WINDOW_OPTIONS.minimumRois(), label + " has too few ROIs");
    assertFalse(summary.windows().isEmpty(), label + " generated no local windows");
    assertTrue(summary.windows().size() < 500, label + " generated an implausible window count");
    summary.windows().forEach(window -> {
      assertTrue(window.scanCount() >= WINDOW_OPTIONS.minimumWindowScans(),
          label + " has a window below the minimum scan count");
      assertTrue(window.scanCount() <= WINDOW_OPTIONS.maximumWindowScans(),
          label + " has an unbounded window");
      assertTrue(window.roiIndices().size() >= WINDOW_OPTIONS.minimumRois(),
          label + " has an under-supported window");
    });
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
    Assumptions.assumeTrue(Files.isRegularFile(path), "Public fixture is unavailable: " + path);
    return path;
  }

  private record WindowSummary(int roiCount, List<RoiMcrWindows.Window> windows,
                               int coveredScans, int overlappingScanAssignments,
                               int minimumWindowScans, int maximumWindowScans,
                               int maximumWindowRois, double meanWindowRois) {

    WindowSummary {
      windows = List.copyOf(windows);
    }

    Map<String, Object> toMap(Scan[] scans) {
      final List<Map<String, Object>> windowMaps = new ArrayList<>();
      for (RoiMcrWindows.Window window : windows) {
        windowMaps.add(Map.of(
            "id", window.id(),
            "start_scan", window.startScan(),
            "end_scan", window.endScan(),
            "scan_count", window.scanCount(),
            "apex_scan", window.apexScan(),
            "rt_min", scans[window.startScan()].getRetentionTime(),
            "rt_max", scans[window.endScan()].getRetentionTime(),
            "apex_rt", scans[window.apexScan()].getRetentionTime(),
            "roi_count", window.roiIndices().size(),
            "apex_signal", window.apexSignal()));
      }
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("scan_count", scans.length);
      map.put("roi_count", roiCount);
      map.put("window_count", windows.size());
      map.put("covered_scans", coveredScans);
      map.put("coverage_fraction", coveredScans / (double) scans.length);
      map.put("overlapping_scan_assignments", overlappingScanAssignments);
      map.put("minimum_window_scans", minimumWindowScans);
      map.put("maximum_window_scans", maximumWindowScans);
      map.put("maximum_window_rois", maximumWindowRois);
      map.put("mean_window_rois", meanWindowRois);
      map.put("windows", windowMaps);
      return map;
    }
  }
}
