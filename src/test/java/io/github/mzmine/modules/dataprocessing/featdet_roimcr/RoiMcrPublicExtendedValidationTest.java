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
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.MZmineProcessingStep;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderTask;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverTask;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.auto.AutoMassDetectorParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Extended public-data validation across independent chromatographic regions. */
class RoiMcrPublicExtendedValidationTest {

  private static final String SAMPLE_ENV = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENV = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";
  private static final Instant MODULE_DATE = Instant.parse("2026-08-04T00:00:00Z");
  private static final Pattern COMPONENT_PATTERN = Pattern.compile("component=(\\d+)");
  private static final Path REPORT = Path.of("build", "roi_mcr_public_validation",
      "roi_mcr_public_extended_validation.json").toAbsolutePath();
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void comparesThreePublicWindowsWithConventionalPipeline() throws Exception {
    final Path samplePath = requiredFile(SAMPLE_ENV);
    final Path blankPath = requiredFile(BLANK_ENV);
    Files.createDirectories(REPORT.getParent());
    Files.deleteIfExists(REPORT);

    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);
    importMzml(project, samplePath, blankPath);
    final RawDataFile sample = findRaw(project, samplePath.getFileName().toString());
    final RawDataFile blank = findRaw(project, blankPath.getFileName().toString());
    runMassDetection(project, sample, 1_000d);
    runMassDetection(project, blank, 1_000d);

    final PolarityType polarity = majorityPolarity(sample);
    final Range<Double> common = commonRtRange(sample, blank, polarity);
    final List<Double> apexes = selectSeparatedTicApexes(sample, polarity, common, 3, 1.25);
    assertEquals(3, apexes.size(), "Could not select three independent TIC regions");

    final List<Map<String, Object>> windows = new ArrayList<>();
    int roiFinished = 0;
    int roiRows = 0;
    int conventionalRows = 0;
    int crossMethodMatches = 0;
    for (int index = 0; index < apexes.size(); index++) {
      final double apex = apexes.get(index);
      final Range<Double> range = Range.closed(Math.max(common.lowerEndpoint(), apex - 0.30),
          Math.min(common.upperEndpoint(), apex + 0.30));
      final ScanSelection selection = new ScanSelection(null, null, range, null, polarity,
          MassSpectrumType.ANY, MsLevelFilter.of(1), null);
      final double apexTic = maximumTic(sample, selection);
      final double roiNoise = Math.max(2_000d, apexTic * 1e-5);
      final double seed = roiNoise * 3d;

      final Execution sampleRoi = runRoiMcr(project, sample, selection, roiNoise, seed,
          "extended-roi-sample-" + index);
      final Execution blankRoi = runRoiMcr(project, blank, selection, roiNoise, seed,
          "extended-roi-blank-" + index);
      final Execution sampleConventional = runConventional(project, sample, selection, roiNoise,
          seed, "extended-adap-sample-" + index);
      final Execution blankConventional = runConventional(project, blank, selection, roiNoise,
          seed, "extended-adap-blank-" + index);

      if (sampleRoi.status() == TaskStatus.FINISHED) {
        roiFinished++;
        roiRows += sampleRoi.rows();
      }
      conventionalRows += sampleConventional.rows();
      final Comparison roiBlank = compare(sampleRoi.list(), blankRoi.list(), 0.02, 0.12f);
      final Comparison conventionalBlank = compare(sampleConventional.list(),
          blankConventional.list(), 0.02, 0.12f);
      final Comparison methods = compare(sampleRoi.list(), sampleConventional.list(), 0.02,
          0.12f);
      crossMethodMatches += methods.matched();

      final Map<String, Object> window = new LinkedHashMap<>();
      window.put("index", index + 1);
      window.put("tic_apex_rt", apex);
      window.put("rt_min", range.lowerEndpoint());
      window.put("rt_max", range.upperEndpoint());
      window.put("sample_scans", selection.getMatchingScans(sample).length);
      window.put("blank_scans", selection.getMatchingScans(blank).length);
      window.put("sample_apex_tic", apexTic);
      window.put("roi_noise", roiNoise);
      window.put("seed_intensity", seed);
      window.put("roi_sample", sampleRoi.toMap());
      window.put("roi_blank", blankRoi.toMap());
      window.put("conventional_sample", sampleConventional.toMap());
      window.put("conventional_blank", blankConventional.toMap());
      window.put("roi_sample_vs_blank", roiBlank.toMap());
      window.put("conventional_sample_vs_blank", conventionalBlank.toMap());
      window.put("roi_vs_conventional_sample", methods.toMap());
      windows.add(window);
    }

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 1);
    report.put("method", "Three separated public TIC windows: ROI-MCR vs ADAP + minimum-search resolver");
    report.put("sample", samplePath.toString());
    report.put("blank", blankPath.toString());
    report.put("polarity", polarity.toString());
    report.put("common_rt_min", common.lowerEndpoint());
    report.put("common_rt_max", common.upperEndpoint());
    report.put("windows", windows);
    report.put("roi_finished_windows", roiFinished);
    report.put("roi_sample_rows_total", roiRows);
    report.put("conventional_sample_rows_total", conventionalRows);
    report.put("cross_method_matches_total", crossMethodMatches);
    report.put("success", roiFinished >= 2 && conventionalRows > 0 && crossMethodMatches > 0);
    MAPPER.writeValue(REPORT.toFile(), report);

    assertTrue(roiFinished >= 2, "ROI-MCR failed in more than one selected public region");
    assertTrue(roiRows > 0, "ROI-MCR produced no sample rows across public regions");
    assertTrue(conventionalRows > 0, "Conventional pipeline produced no sample rows");
    assertTrue(crossMethodMatches > 0, "ROI-MCR and conventional pipeline had no shared feature");
    assertTrue(Files.size(REPORT) > 1_000);
    System.out.println("ROI_MCR_PUBLIC_EXTENDED_REPORT=" + REPORT);
    System.out.println(MAPPER.writeValueAsString(report));
  }

  private static Execution runRoiMcr(MZmineProject project, RawDataFile raw,
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
    parameters.setParameter(RoiMcrParameters.maximumComponents, 4);
    parameters.setParameter(RoiMcrParameters.restarts, 4);
    parameters.setParameter(RoiMcrParameters.maximumIterations, 220);
    parameters.setParameter(RoiMcrParameters.nnlsIterations, 50);
    parameters.setParameter(RoiMcrParameters.minimumRankImprovement, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumRestartStability, 0.65);
    parameters.setParameter(RoiMcrParameters.minimumComponentIons, 2);
    parameters.setParameter(RoiMcrParameters.minimumComponentEnergy, 0.001);
    parameters.setParameter(RoiMcrParameters.weightingExponent, 0.5);
    parameters.setParameter(RoiMcrParameters.minimumSpectralContribution, 0.01);
    parameters.setParameter(RoiMcrParameters.minimumAssignmentFraction, 0.50);

    final int before = project.getNumberOfFeatureLists();
    final RoiMcrTask task = new RoiMcrTask(project, raw, parameters, null, MODULE_DATE);
    final long started = System.nanoTime();
    task.run();
    final FeatureList list = project.getNumberOfFeatureLists() > before
        ? project.getCurrentFeatureLists().get(project.getNumberOfFeatureLists() - 1) : null;
    return summarize("ROI-MCR", task.getStatus(), task.getErrorMessage(), list,
        elapsedSeconds(started));
  }

  private static Execution runConventional(MZmineProject project, RawDataFile raw,
      ScanSelection selection, double noise, double seed, String suffix) {
    final int builderBefore = project.getNumberOfFeatureLists();
    final ADAPChromatogramBuilderParameters builderParameters =
        new ADAPChromatogramBuilderParameters();
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.scanSelection, selection);
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.minimumConsecutiveScans, 4);
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.minGroupIntensity, noise);
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.minHighestPoint, seed);
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.mzTolerance,
        new MZTolerance(0.01, 15));
    builderParameters.setParameter(ADAPChromatogramBuilderParameters.suffix, suffix + "-eics");
    final long started = System.nanoTime();
    final ModularADAPChromatogramBuilderTask builder =
        ModularADAPChromatogramBuilderTask.forChromatography(project, raw, builderParameters, null,
            MODULE_DATE, ModularADAPChromatogramBuilderModule.class);
    builder.run();
    if (builder.getStatus() != TaskStatus.FINISHED
        || project.getNumberOfFeatureLists() <= builderBefore) {
      return summarize("ADAP + resolver", builder.getStatus(), builder.getErrorMessage(), null,
          elapsedSeconds(started));
    }

    final FeatureList chromatograms = project.getCurrentFeatureLists().get(builderBefore);
    final int resolverBefore = project.getNumberOfFeatureLists();
    final MinimumSearchFeatureResolverParameters resolverParameters =
        new MinimumSearchFeatureResolverParameters();
    resolverParameters.setParameter(GeneralResolverParameters.SUFFIX, suffix + "-resolved");
    resolverParameters.setParameter(GeneralResolverParameters.handleOriginal,
        OriginalFeatureListOption.KEEP);
    resolverParameters.getParameter(GeneralResolverParameters.groupMS2Parameters).setValue(false);
    resolverParameters.setParameter(
        MinimumSearchFeatureResolverParameters.CHROMATOGRAPHIC_THRESHOLD_LEVEL, 0.0);
    resolverParameters.setParameter(MinimumSearchFeatureResolverParameters.SEARCH_RT_RANGE, 0.08);
    resolverParameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_RELATIVE_HEIGHT,
        0.0);
    resolverParameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_ABSOLUTE_HEIGHT,
        seed);
    resolverParameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_RATIO, 1.3);
    resolverParameters.setParameter(MinimumSearchFeatureResolverParameters.PEAK_DURATION,
        Range.closed(0.02, 0.60));
    resolverParameters.setParameter(GeneralResolverParameters.MIN_NUMBER_OF_DATAPOINTS, 4);
    final FeatureResolverTask resolver = new FeatureResolverTask(project, null, chromatograms,
        resolverParameters, FeatureDataUtils.DEFAULT_CENTER_FUNCTION, MODULE_DATE);
    resolver.run();
    final FeatureList resolved = project.getNumberOfFeatureLists() > resolverBefore
        ? project.getCurrentFeatureLists().get(project.getNumberOfFeatureLists() - 1) : null;
    return summarize("ADAP + resolver", resolver.getStatus(), resolver.getErrorMessage(), resolved,
        elapsedSeconds(started));
  }

  private static Execution summarize(String method, TaskStatus status, String error,
      FeatureList list, double elapsed) {
    if (list == null) {
      return new Execution(method, status, error, null, 0, 0, 0d, 0d, Set.of(), elapsed);
    }
    double maximumHeight = 0;
    double summedArea = 0;
    final Set<Integer> components = new TreeSet<>();
    for (FeatureListRow row : list.getRows()) {
      maximumHeight = Math.max(maximumHeight, row.getAverageHeight());
      summedArea += row.getAverageArea();
      final String comment = row.get(CommentType.class);
      if (comment != null) {
        final Matcher matcher = COMPONENT_PATTERN.matcher(comment);
        if (matcher.find()) {
          components.add(Integer.parseInt(matcher.group(1)));
        }
      }
    }
    return new Execution(method, status, error, list, list.getNumberOfRows(), components.size(),
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
    assertEquals(0, raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getMassList() == null).count());
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

  private static List<Double> selectSeparatedTicApexes(RawDataFile raw, PolarityType polarity,
      Range<Double> range, int count, double minimumSpacing) {
    final List<ScanTic> candidates = raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1)
        .filter(scan -> scan.getPolarity() == polarity)
        .filter(scan -> range.contains((double) scan.getRetentionTime()))
        .map(scan -> new ScanTic(scan.getRetentionTime(), tic(scan)))
        .sorted(Comparator.comparingDouble(ScanTic::tic).reversed()).toList();
    final List<Double> selected = new ArrayList<>();
    for (ScanTic candidate : candidates) {
      if (selected.stream().allMatch(rt -> Math.abs(rt - candidate.rt()) >= minimumSpacing)) {
        selected.add(candidate.rt());
        if (selected.size() == count) {
          break;
        }
      }
    }
    selected.sort(Double::compareTo);
    return selected;
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

  private static double elapsedSeconds(long started) {
    return Math.round((System.nanoTime() - started) / 1_000_000d) / 1_000d;
  }

  private record ScanTic(double rt, double tic) {
  }

  private record Execution(String method, TaskStatus status, String error, FeatureList list,
                           int rows, int componentCount, double maximumHeight, double summedArea,
                           Set<Integer> componentIds, double elapsedSeconds) {

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("method", method);
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
