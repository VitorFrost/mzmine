/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineProcessingModule;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Executes ROI-MCR against the SHA-verified public Banane/blank mzML pair used by the open-offline
 * validation infrastructure. Dataset bytes are supplied by CI and are never committed.
 */
class RoiMcrPublicMzmlValidationTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Path OUTPUT_DIRECTORY = Path.of("build", "roi_mcr_public_validation")
      .toAbsolutePath();
  private static final Path REPORT_FILE = OUTPUT_DIRECTORY.resolve(
      "roi_mcr_public_validation.json");
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void processesApprovedPublicSampleAndBlank() throws Exception {
    final Path samplePath = requiredEnvironmentFile(SAMPLE_ENV);
    final Path blankPath = requiredEnvironmentFile(BLANK_ENV);
    Files.createDirectories(OUTPUT_DIRECTORY);
    Files.deleteIfExists(REPORT_FILE);

    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 2);
    report.put("method",
        "Direct public mzML import -> auto mass detection -> adaptive TIC window -> ROI-MCR -> perturbation robustness");
    report.put("sample_path", samplePath.toString());
    report.put("blank_path", blankPath.toString());
    report.put("dataset_policy",
        "Bytes downloaded and SHA-verified from frozen public manifests by CI");

    final long importStarted = System.nanoTime();
    importMzml(project, samplePath, blankPath);
    report.put("import_elapsed_seconds", elapsedSeconds(importStarted));
    assertEquals(2, project.getNumberOfDataFiles());

    final RawDataFile sample = findRawFile(project, samplePath.getFileName().toString());
    final RawDataFile blank = findRawFile(project, blankPath.getFileName().toString());
    assertNotNull(sample);
    assertNotNull(blank);

    final double massNoise = 1_000d;
    final long massStarted = System.nanoTime();
    runMassDetection(project, sample, massNoise);
    runMassDetection(project, blank, massNoise);
    report.put("mass_detection_elapsed_seconds", elapsedSeconds(massStarted));
    report.put("mass_detection_noise", massNoise);

    final PolarityType polarity = majorityPolarity(sample);
    final Range<Double> commonRange = commonRtRange(sample, blank, polarity);
    final double apexRt = ticApexRt(sample, polarity, commonRange);
    final Range<Double> selectedRange = adaptiveWindow(sample, blank, polarity, commonRange, apexRt);
    final ScanSelection selection = new ScanSelection(null, null, selectedRange, null, polarity,
        MassSpectrumType.ANY, MsLevelFilter.of(1), null);

    final double apexTic = maximumTic(sample, selection);
    final double roiNoise = Math.max(2_000d, apexTic * 1e-5);
    final double seedIntensity = roiNoise * 3d;
    final int sampleSelectedScans = selection.getMatchingScans(sample).length;
    final int blankSelectedScans = selection.getMatchingScans(blank).length;

    report.put("polarity", polarity.toString());
    report.put("common_rt_min", commonRange.lowerEndpoint());
    report.put("common_rt_max", commonRange.upperEndpoint());
    report.put("sample_tic_apex_rt", apexRt);
    report.put("window_rt_min", selectedRange.lowerEndpoint());
    report.put("window_rt_max", selectedRange.upperEndpoint());
    report.put("sample_selected_scans", sampleSelectedScans);
    report.put("blank_selected_scans", blankSelectedScans);
    report.put("sample_apex_tic", apexTic);
    report.put("roi_noise", roiNoise);
    report.put("roi_seed_intensity", seedIntensity);

    assertTrue(sampleSelectedScans >= 12, "Public sample window has too few MS1 scans");
    assertTrue(blankSelectedScans >= 12, "Public blank window has too few MS1 scans");

    final long sampleStarted = System.nanoTime();
    final RoiExecution sampleExecution = runRoiMcr(project, sample, selection, roiNoise,
        seedIntensity, "public-sample-roi-mcr");
    report.put("sample_roi_mcr_elapsed_seconds", elapsedSeconds(sampleStarted));
    report.put("sample", sampleExecution.toMap());

    assertEquals(TaskStatus.FINISHED, sampleExecution.status(), sampleExecution.errorMessage());
    assertNotNull(sampleExecution.featureList());
    assertTrue(sampleExecution.rows() > 0, "Public sample did not produce ROI-MCR features");
    assertValidProvenance("sample", sampleExecution.provenance());

    final long blankStarted = System.nanoTime();
    final RoiExecution blankExecution = runRoiMcr(project, blank, selection, roiNoise,
        seedIntensity, "public-blank-roi-mcr");
    report.put("blank_roi_mcr_elapsed_seconds", elapsedSeconds(blankStarted));
    report.put("blank", blankExecution.toMap());

    assertEquals(TaskStatus.FINISHED, blankExecution.status(), blankExecution.errorMessage());
    assertNotNull(blankExecution.featureList());
    assertTrue(blankExecution.rows() > 0, "Public blank did not produce ROI-MCR features");
    assertValidProvenance("blank", blankExecution.provenance());

    final Comparison comparison = compare(sampleExecution.featureList(),
        blankExecution.featureList(), 0.02, 0.12f);
    report.put("comparison", comparison.toMap());
    report.put("success", true);
    MAPPER.writeValue(REPORT_FILE.toFile(), report);

    assertTrue(Files.isRegularFile(REPORT_FILE));
    assertTrue(Files.size(REPORT_FILE) > 1_000);
    assertFalse(comparison.sampleRows() == 0);
    System.out.println("ROI_MCR_PUBLIC_VALIDATION_REPORT=" + REPORT_FILE);
    System.out.println(MAPPER.writeValueAsString(report));
  }

  private static void assertValidProvenance(String label, RoiMcrProvenance.Summary provenance) {
    assertTrue(provenance.componentCount() > 0, label + " has no parsed ROI-MCR components");
    final int classified = provenance.confidenceCounts().values().stream()
        .mapToInt(Integer::intValue).sum();
    assertEquals(provenance.componentCount(), classified,
        label + " confidence counts do not match unique component count");
    assertTrue(provenance.minimumPerturbationVariants() >= 2,
        label + " did not execute enough perturbation variants");
    assertTrue(provenance.maximumPerturbationVariants()
            >= provenance.minimumPerturbationVariants(),
        label + " has inconsistent perturbation variant counts");
    assertTrue(provenance.meanRestartStability() >= 0
            && provenance.meanRestartStability() <= 1,
        label + " restart stability is outside [0,1]");
    assertTrue(provenance.meanPerturbationStability() >= 0
            && provenance.meanPerturbationStability() <= 1,
        label + " perturbation stability is outside [0,1]");
    assertTrue(provenance.meanPerturbationSupport() >= 0
            && provenance.meanPerturbationSupport() <= 1,
        label + " perturbation support is outside [0,1]");
  }

  private static void importMzml(MZmineProject project, Path sample, Path blank) {
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.setParameter(MSDKmzMLImportParameters.fileNames,
        new File[]{sample.toFile().getAbsoluteFile(), blank.toFile().getAbsoluteFile()});
    runModule(new MSDKmzMLImportModule(), project, parameters);
  }

  private static void runMassDetection(MZmineProject project, RawDataFile raw,
      double noiseLevel) {
    final AutoMassDetectorParameters autoParameters = new AutoMassDetectorParameters();
    autoParameters.setParameter(AutoMassDetectorParameters.noiseLevel, noiseLevel);

    final MassDetectionParameters parameters = new MassDetectionParameters();
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.massDetector,
        new MZmineProcessingStepImpl<MassDetector>(MassDetectionParameters.auto, autoParameters));
    runModule(new MassDetectionModule(), project, parameters);

    final long missingMassLists = raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getMassList() == null).count();
    assertEquals(0, missingMassLists, raw.getName() + " retains MS1 scans without a mass list");
  }

  private static void runModule(MZmineProcessingModule module, MZmineProject project,
      ParameterSet parameters) {
    final List<Task> tasks = new ArrayList<>();
    final ExitCode exitCode = module.runModule(project, parameters, tasks, Instant.now());
    assertEquals(ExitCode.OK, exitCode, module.getName());
    assertFalse(tasks.isEmpty(), module.getName() + " did not create tasks");
    for (Task task : tasks) {
      task.run();
      assertEquals(TaskStatus.FINISHED, task.getStatus(),
          () -> module.getName() + ": " + task.getErrorMessage());
    }
  }

  private static RoiExecution runRoiMcr(MZmineProject project, RawDataFile raw,
      ScanSelection selection, double noise, double seed, String suffix) {
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
    parameters.setParameter(RoiMcrParameters.maximumComponents, 3);
    parameters.setParameter(RoiMcrParameters.restarts, 3);
    parameters.setParameter(RoiMcrParameters.maximumIterations, 160);
    parameters.setParameter(RoiMcrParameters.nnlsIterations, 40);
    parameters.setParameter(RoiMcrParameters.minimumRankImprovement, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumRestartStability, 0.65);
    parameters.setParameter(RoiMcrParameters.minimumComponentIons, 2);
    parameters.setParameter(RoiMcrParameters.minimumComponentEnergy, 0.001);
    parameters.setParameter(RoiMcrParameters.weightingExponent, 0.5);
    parameters.setParameter(RoiMcrParameters.minimumSpectralContribution, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumAssignmentFraction, 0.50);

    final int listsBefore = project.getNumberOfFeatureLists();
    final RoiMcrTask task = new RoiMcrTask(project, raw, parameters, null, Instant.now());
    task.run();
    final FeatureList result = project.getNumberOfFeatureLists() > listsBefore
        ? project.getCurrentFeatureLists().get(project.getNumberOfFeatureLists() - 1) : null;
    return summarize(task.getStatus(), task.getErrorMessage(), result);
  }

  private static RoiExecution summarize(TaskStatus status, String error, FeatureList list) {
    final RoiMcrProvenance.Summary provenance = RoiMcrProvenance.summarize(list);
    if (list == null) {
      return new RoiExecution(status, error, null, 0, provenance, null, null, null, null, 0d,
          0d);
    }
    Double minMz = null;
    Double maxMz = null;
    Float minRt = null;
    Float maxRt = null;
    double maximumHeight = 0;
    double summedArea = 0;
    for (FeatureListRow row : list.getRows()) {
      final double mz = row.getAverageMZ();
      final float rt = row.getAverageRT();
      minMz = minMz == null ? mz : Math.min(minMz, mz);
      maxMz = maxMz == null ? mz : Math.max(maxMz, mz);
      minRt = minRt == null ? rt : Math.min(minRt, rt);
      maxRt = maxRt == null ? rt : Math.max(maxRt, rt);
      maximumHeight = Math.max(maximumHeight, row.getAverageHeight());
      summedArea += row.getAverageArea();
    }
    return new RoiExecution(status, error, list, list.getNumberOfRows(), provenance, minMz, maxMz,
        minRt, maxRt, maximumHeight, summedArea);
  }

  private static Comparison compare(FeatureList sample, FeatureList blank, double mzTolerance,
      float rtTolerance) {
    final int sampleRows = sample == null ? 0 : sample.getNumberOfRows();
    final int blankRows = blank == null ? 0 : blank.getNumberOfRows();
    int matched = 0;
    if (sample != null && blank != null) {
      for (FeatureListRow sampleRow : sample.getRows()) {
        final boolean hasMatch = blank.getRows().stream().anyMatch(blankRow ->
            Math.abs(sampleRow.getAverageMZ() - blankRow.getAverageMZ()) <= mzTolerance
                && Math.abs(sampleRow.getAverageRT() - blankRow.getAverageRT()) <= rtTolerance);
        if (hasMatch) {
          matched++;
        }
      }
    }
    return new Comparison(sampleRows, blankRows, matched, sampleRows - matched, mzTolerance,
        rtTolerance);
  }

  private static RawDataFile findRawFile(MZmineProject project, String name) {
    return project.getCurrentRawDataFiles().stream().filter(raw -> raw.getName().equals(name))
        .findFirst().orElseThrow(() -> new IllegalStateException("Imported file not found: " + name));
  }

  private static PolarityType majorityPolarity(RawDataFile raw) {
    return raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .collect(java.util.stream.Collectors.groupingBy(Scan::getPolarity,
            java.util.stream.Collectors.counting())).entrySet().stream()
        .max(Map.Entry.<PolarityType, Long>comparingByValue()
            .thenComparing(entry -> entry.getKey().ordinal()))
        .orElseThrow(() -> new IllegalStateException("No MS1 polarity in " + raw.getName()))
        .getKey();
  }

  private static Range<Double> commonRtRange(RawDataFile sample, RawDataFile blank,
      PolarityType polarity) {
    final Range<Double> sampleRange = rtRange(sample, polarity);
    final Range<Double> blankRange = rtRange(blank, polarity);
    final double lower = Math.max(sampleRange.lowerEndpoint(), blankRange.lowerEndpoint());
    final double upper = Math.min(sampleRange.upperEndpoint(), blankRange.upperEndpoint());
    if (upper <= lower) {
      throw new IllegalStateException("Sample and blank do not share an MS1 retention-time range");
    }
    return Range.closed(lower, upper);
  }

  private static Range<Double> rtRange(RawDataFile raw, PolarityType polarity) {
    final List<Scan> scans = raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity).sorted(
            Comparator.comparingDouble(Scan::getRetentionTime)).toList();
    if (scans.isEmpty()) {
      throw new IllegalStateException("No matching MS1 scans in " + raw.getName());
    }
    return Range.closed((double) scans.get(0).getRetentionTime(),
        (double) scans.get(scans.size() - 1).getRetentionTime());
  }

  private static double ticApexRt(RawDataFile raw, PolarityType polarity,
      Range<Double> allowedRange) {
    return raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity)
        .filter(scan -> allowedRange.contains((double) scan.getRetentionTime()))
        .max(Comparator.comparingDouble(RoiMcrPublicMzmlValidationTest::tic))
        .orElseThrow(() -> new IllegalStateException("No MS1 scan in common RT range"))
        .getRetentionTime();
  }

  private static Range<Double> adaptiveWindow(RawDataFile sample, RawDataFile blank,
      PolarityType polarity, Range<Double> commonRange, double apexRt) {
    for (double halfWidth : new double[]{0.35, 0.50, 0.75, 1.0, 1.5}) {
      final Range<Double> candidate = Range.closed(
          Math.max(commonRange.lowerEndpoint(), apexRt - halfWidth),
          Math.min(commonRange.upperEndpoint(), apexRt + halfWidth));
      if (countScans(sample, polarity, candidate) >= 12
          && countScans(blank, polarity, candidate) >= 12) {
        return candidate;
      }
    }
    return commonRange;
  }

  private static long countScans(RawDataFile raw, PolarityType polarity,
      Range<Double> rtRange) {
    return raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity)
        .filter(scan -> rtRange.contains((double) scan.getRetentionTime())).count();
  }

  private static double maximumTic(RawDataFile raw, ScanSelection selection) {
    return java.util.Arrays.stream(selection.getMatchingScans(raw))
        .mapToDouble(RoiMcrPublicMzmlValidationTest::tic).max().orElse(0d);
  }

  private static double tic(Scan scan) {
    if (scan.getMassList() == null) {
      return 0d;
    }
    final int size = scan.getMassList().getNumberOfDataPoints();
    double sum = 0;
    for (double value : scan.getMassList().getIntensityValues(new double[size])) {
      sum += value;
    }
    return sum;
  }

  private static Path requiredEnvironmentFile(String name) {
    final String value = System.getenv(name);
    Assumptions.assumeTrue(value != null && !value.isBlank(), () -> "Set " + name + "=<path>");
    final Path path = Path.of(value).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(path), () -> "Missing required file for " + name + ": " + path);
    return path;
  }

  private static double elapsedSeconds(long startedNanos) {
    return Double.parseDouble(String.format(Locale.ROOT, "%.3f",
        (System.nanoTime() - startedNanos) / 1_000_000_000d));
  }

  private record RoiExecution(TaskStatus status, String errorMessage, FeatureList featureList,
                              int rows, RoiMcrProvenance.Summary provenance, Double minimumMz,
                              Double maximumMz, Float minimumRt, Float maximumRt,
                              double maximumHeight, double summedArea) {

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", status.toString());
      map.put("error_message", errorMessage);
      map.put("feature_list", featureList == null ? null : featureList.getName());
      map.put("rows", rows);
      map.put("provenance", provenance.toMap());
      map.put("minimum_mz", minimumMz);
      map.put("maximum_mz", maximumMz);
      map.put("minimum_rt", minimumRt);
      map.put("maximum_rt", maximumRt);
      map.put("maximum_height", maximumHeight);
      map.put("summed_area", summedArea);
      return map;
    }
  }

  private record Comparison(int sampleRows, int blankRows, int matchedSampleRows,
                            int sampleOnlyRows, double mzTolerance, float rtTolerance) {

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("sample_rows", sampleRows);
      map.put("blank_rows", blankRows);
      map.put("matched_sample_rows", matchedSampleRows);
      map.put("sample_only_rows", sampleOnlyRows);
      map.put("mz_tolerance", mzTolerance);
      map.put("rt_tolerance", rtTolerance);
      return map;
    }
  }
}
