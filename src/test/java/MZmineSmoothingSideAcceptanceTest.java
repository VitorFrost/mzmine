/*
 * Copyright (c) 2026 Contributors to the open offline fork
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Range;
import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineProcessingStep;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderTask;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetectorParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingAlgorithm;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingTask;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.loess.LoessSmoothing;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.loess.LoessSmoothingParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.savitzkygolay.SavitzkyGolayParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.savitzkygolay.SavitzkyGolaySmoothing;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_spectral_library.SpectralLibraryImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OptionalParameter;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Produces one normalized side of the governed v4.0.8 feature-smoothing differential. */
class MZmineSmoothingSideAcceptanceTest {

  private static final String ORACLE_COMMIT = "8029f930d28c0447f0acf2bcabef0a79865ad434";
  private static final String PROBE_CLASS =
      "io.github.mzmine.modules.dataprocessing.featdet_smoothing.V408SmoothingTaskProbe";
  private static final String ADAP_MODULE_CLASS =
      "io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder."
          + "ModularADAPChromatogramBuilderModule";
  private static final String SMOOTHING_MODULE_CLASS =
      "io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingModule";
  private static final String POINT_SIDECAR_FORMAT =
      "tsv-v1:row_index,point_index,scan_number,rt_float32_bits,mz_float64_bits|null,"
          + "intensity_float64_bits|null";
  private static final String POINT_SIDECAR_HEADER =
      "row_index\tpoint_index\tscan_number\trt_float32_bits\tmz_float64_bits\tintensity_float64_bits\n";
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private static final HexFormat HEX = HexFormat.of();

  @AfterEach
  void cleanProject() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  @Timeout(value = 25, unit = MINUTES)
  void producesOneGovernedSmoothingParitySide() throws Exception {
    Map<String, String> env = System.getenv();
    String inputValue = env.get("MZMINE_SMOOTHING_PARITY_INPUT");
    String settingsValue = env.get("MZMINE_SMOOTHING_PARITY_SETTINGS");
    String outputValue = env.get("MZMINE_SMOOTHING_PARITY_SIDE_OUTPUT");
    String pointsOutputValue = env.get("MZMINE_SMOOTHING_PARITY_POINTS_OUTPUT");
    String side = env.get("MZMINE_SMOOTHING_PARITY_SIDE");
    Assumptions.assumeTrue(
        inputValue != null && settingsValue != null && outputValue != null
            && pointsOutputValue != null && side != null,
        "Governed smoothing side-report inputs were not supplied");
    assertTrue(side.equals("candidate") || side.equals("oracle"),
        "Side must be exactly candidate or oracle");

    Path input = Path.of(inputValue).toAbsolutePath().normalize();
    Path settings = Path.of(settingsValue).toAbsolutePath().normalize();
    Path output = Path.of(outputValue).toAbsolutePath().normalize();
    Path pointsOutput = Path.of(pointsOutputValue).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(input), "Governed mzML is missing");
    assertTrue(Files.isRegularFile(settings), "Published settings XML is missing");
    verifyHash(input, requireEnvironment("MZMINE_SMOOTHING_PARITY_INPUT_SHA256"));
    verifyHash(settings, requireEnvironment("MZMINE_SMOOTHING_PARITY_SETTINGS_SHA256"));

    Class<?> probeClass = null;
    if (side.equals("oracle")) {
      probeClass = Class.forName(PROBE_CLASS);
      assertTrue(Task.class.isAssignableFrom(probeClass),
          "Frozen v4.0.8 smoothing probe must implement Task");
    }

    MZmineTestUtil.cleanProject();
    importMzml(input);
    MZmineProject project = MZmineCore.getProjectManager().getCurrentProject();
    assertEquals(1, project.getCurrentRawDataFiles().size());
    RawDataFile raw = project.getCurrentRawDataFiles().get(0);
    runCentroidMassDetection(raw);
    ModularFeatureList adap = runGovernedAdap(project, raw, settings);

    List<Map<String, Object>> preRecords = compactSnapshot(adap, raw);
    String preRecordsSha = sha256(JSON.writeValueAsString(preRecords));

    SmoothingParameters smoothing = loadPublishedSmoothingParameters(settings, adap);
    Map<String, Object> governedSettings = governedSmoothingSettings(smoothing);
    ModularFeatureList smoothed = side.equals("candidate")
        ? runCandidateSmoothing(project, adap, smoothing.cloneParameterSet(true))
        : runOracleSmoothing(probeClass, project, adap, smoothing.cloneParameterSet(true));

    SnapshotResult snapshot = writeDetailedSnapshot(smoothed, raw, preRecords, pointsOutput);
    List<Map<String, Object>> postRecords = snapshot.records();
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 2);
    report.put("gate_id", "mzmine-v4.0.8-smoothing-side-report-v2");
    report.put("implementation", side);
    report.put("candidate_commit", requireEnvironment("MZMINE_SMOOTHING_PARITY_CANDIDATE_COMMIT"));
    report.put("oracle_commit", ORACLE_COMMIT);
    report.put("input_sha256", sha256(input));
    report.put("published_settings_sha256", sha256(settings));
    report.put("ms1_scan_count",
        raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1).count());
    report.put("adap_feature_count", adap.getNumberOfRows());
    report.put("pre_smoothing_records_sha256", preRecordsSha);
    report.put("settings", governedSettings);
    report.put("smoothed_feature_count", postRecords.size());
    report.put("complete_point_count", snapshot.completePointCount());
    report.put("points_sidecar_format", POINT_SIDECAR_FORMAT);
    report.put("points_sidecar_sha256", snapshot.pointsSha256());
    report.put("points_sidecar_bytes", snapshot.pointsBytes());
    report.put("records_sha256", sha256(JSON.writeValueAsString(postRecords)));
    report.put("records", postRecords);

    if (output.getParent() != null) {
      Files.createDirectories(output.getParent());
    }
    JSON.writeValue(output.toFile(), report);
    assertTrue(Files.isRegularFile(output), "Smoothing side report was not written");
    assertTrue(Files.isRegularFile(pointsOutput), "Smoothing complete-point sidecar was not written");
  }

  private static void importMzml(Path input) throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(AllSpectralDataImportModule.class).cloneParameterSet();
    parameters.setParameter(AllSpectralDataImportParameters.fileNames, new File[]{input.toFile()});
    parameters.setParameter(AllSpectralDataImportParameters.advancedImport, false);
    parameters.setParameter(SpectralLibraryImportParameters.dataBaseFiles, new File[0]);
    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(10, MINUTES, AllSpectralDataImportModule.class,
            parameters), "mzML import did not finish successfully");
  }

  private static void runCentroidMassDetection(RawDataFile raw) throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(MassDetectionModule.class).cloneParameterSet();
    ParameterSet centroidParameters = new CentroidMassDetectorParameters();
    centroidParameters.setParameter(CentroidMassDetectorParameters.noiseLevel, 0d);
    centroidParameters.setParameter(CentroidMassDetectorParameters.detectIsotopes, false);
    parameters.setParameter(MassDetectionParameters.massDetector,
        new MZmineProcessingStepImpl<>(new CentroidMassDetector(), centroidParameters));
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    parameters.setParameter(MassDetectionParameters.outFilenameOption, false);
    assertInstanceOf(CentroidMassDetector.class,
        parameters.getValue(MassDetectionParameters.massDetector).getModule());
    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(10, MINUTES, MassDetectionModule.class, parameters),
        "MS1 centroid mass detection did not finish successfully");
  }

  private static ModularFeatureList runGovernedAdap(MZmineProject project, RawDataFile raw,
      Path settings) throws Exception {
    ADAPChromatogramBuilderParameters parameters = loadPublishedAdapParameters(settings, raw);
    assertEquals(0, project.getNumberOfFeatureLists());
    Task task = ModularADAPChromatogramBuilderTask.forChromatography(project, raw,
        parameters.cloneParameterSet(true), null, Instant.EPOCH,
        ModularADAPChromatogramBuilderModule.class);
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(),
        () -> "Governed ADAP input task failed: " + task.getErrorMessage());
    assertEquals(1, project.getNumberOfFeatureLists());
    return assertInstanceOf(ModularFeatureList.class, project.getCurrentFeatureLists().get(0));
  }

  private static ADAPChromatogramBuilderParameters loadPublishedAdapParameters(Path settings,
      RawDataFile raw) throws Exception {
    Element step = findBatchStep(settings, ADAP_MODULE_CLASS);
    ADAPChromatogramBuilderParameters parameters = new ADAPChromatogramBuilderParameters();
    parameters.loadValuesFromXML(step);
    parameters.setParameter(ADAPChromatogramBuilderParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(ADAPChromatogramBuilderParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(ADAPChromatogramBuilderParameters.suffix, "parity-chromatograms");
    return parameters;
  }

  private static SmoothingParameters loadPublishedSmoothingParameters(Path settings,
      ModularFeatureList input) throws Exception {
    Element step = findBatchStep(settings, SMOOTHING_MODULE_CLASS);
    SmoothingParameters parameters = new SmoothingParameters();
    parameters.loadValuesFromXML(step);
    parameters.getParameter(SmoothingParameters.featureLists)
        .setValue(FeatureListsSelectionType.SPECIFIC_FEATURELISTS, new FeatureList[]{input});
    parameters.setParameter(SmoothingParameters.suffix, "parity-smoothed");
    assertNotNull(parameters.getValue(SmoothingParameters.smoothingAlgorithm),
        "Published smoothing algorithm must resolve");
    return parameters;
  }

  private static Element findBatchStep(Path settings, String method) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    setOptionalJaxpAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    setOptionalJaxpAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    Document document = factory.newDocumentBuilder().parse(settings.toFile());
    Element found = null;
    NodeList steps = document.getDocumentElement().getElementsByTagName("batchstep");
    for (int index = 0; index < steps.getLength(); index++) {
      Element candidate = (Element) steps.item(index);
      if (method.equals(candidate.getAttribute("method"))) {
        if (found != null) {
          throw new IllegalStateException("Published workflow contains duplicate step " + method);
        }
        found = candidate;
      }
    }
    assertNotNull(found, "Published workflow does not contain " + method);
    return found;
  }

  private static void setOptionalJaxpAttribute(DocumentBuilderFactory factory, String name,
      Object value) {
    try {
      factory.setAttribute(name, value);
    } catch (IllegalArgumentException ignored) {
      // Mandatory anti-XXE features above remain enforced and throw if unsupported.
    }
  }

  private static ModularFeatureList runCandidateSmoothing(MZmineProject project,
      ModularFeatureList input, ParameterSet parameters) {
    Task task = new SmoothingTask(project, input, null, parameters, Instant.EPOCH);
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(),
        () -> "Candidate smoothing task failed: " + task.getErrorMessage());
    return findOutputList(project, input);
  }

  private static ModularFeatureList runOracleSmoothing(Class<?> probeClass, MZmineProject project,
      ModularFeatureList input, ParameterSet parameters) throws Exception {
    Constructor<?> constructor = probeClass.getConstructor(MZmineProject.class,
        ModularFeatureList.class, MemoryMapStorage.class, ParameterSet.class, Instant.class);
    Task task;
    try {
      task = (Task) constructor.newInstance(project, input, null, parameters, Instant.EPOCH);
    } catch (InvocationTargetException error) {
      if (error.getCause() instanceof Exception exception) {
        throw exception;
      }
      throw error;
    }
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(),
        () -> "Frozen v4.0.8 smoothing probe failed: " + task.getErrorMessage());
    return findOutputList(project, input);
  }

  private static ModularFeatureList findOutputList(MZmineProject project, FeatureList input) {
    for (FeatureList list : project.getCurrentFeatureLists()) {
      if (list != input) {
        return assertInstanceOf(ModularFeatureList.class, list);
      }
    }
    throw new AssertionError("Smoothing task produced no distinct output feature list");
  }

  private static Map<String, Object> governedSmoothingSettings(SmoothingParameters parameters) {
    Map<String, Object> value = new LinkedHashMap<>();
    MZmineProcessingStep<SmoothingAlgorithm> selected =
        parameters.getValue(SmoothingParameters.smoothingAlgorithm);
    assertNotNull(selected);
    value.put("algorithm_class", selected.getModule().getClass().getName());
    value.put("algorithm_name", selected.getModule().getName());
    value.put("handle_original",
        String.valueOf(parameters.getValue(SmoothingParameters.handleOriginal)));
    value.put("suffix", parameters.getValue(SmoothingParameters.suffix));

    ParameterSet embedded = selected.getParameterSet();
    if (selected.getModule() instanceof SavitzkyGolaySmoothing) {
      OptionalParameter<?> rt = embedded.getParameter(SavitzkyGolayParameters.rtSmoothing);
      OptionalParameter<?> mobility =
          embedded.getParameter(SavitzkyGolayParameters.mobilitySmoothing);
      value.put("rt_smoothing_enabled", rt.getValue());
      value.put("rt_smoothing_points", rt.getEmbeddedParameter().getValue());
      value.put("mobility_smoothing_enabled", mobility.getValue());
      value.put("mobility_smoothing_points", mobility.getEmbeddedParameter().getValue());
    } else if (selected.getModule() instanceof LoessSmoothing) {
      OptionalParameter<?> rt = embedded.getParameter(LoessSmoothingParameters.rtSmoothing);
      OptionalParameter<?> mobility =
          embedded.getParameter(LoessSmoothingParameters.mobilitySmoothing);
      value.put("rt_smoothing_enabled", rt.getValue());
      value.put("rt_smoothing_width_scans", rt.getEmbeddedParameter().getValue());
      value.put("mobility_smoothing_enabled", mobility.getValue());
      value.put("mobility_smoothing_width_scans", mobility.getEmbeddedParameter().getValue());
    } else {
      throw new AssertionError("Unexpected published smoothing algorithm: "
          + selected.getModule().getClass().getName());
    }
    return value;
  }

  private static List<Map<String, Object>> compactSnapshot(FeatureList list, RawDataFile raw)
      throws Exception {
    List<Map<String, Object>> records = new ArrayList<>(list.getNumberOfRows());
    for (int index = 0; index < list.getNumberOfRows(); index++) {
      FeatureListRow row = list.getRow(index);
      Feature feature = row.getFeature(raw);
      assertNotNull(feature);
      Map<String, Object> record = new LinkedHashMap<>();
      record.put("row_index", index);
      record.put("row_id", row.getID());
      record.put("scan_count", feature.getScanNumbers().size());
      record.put("series_sha256", featureSeriesSha256(feature));
      records.add(record);
    }
    return records;
  }

  private static SnapshotResult writeDetailedSnapshot(FeatureList list, RawDataFile raw,
      List<Map<String, Object>> preRecords, Path pointsOutput) throws Exception {
    assertEquals(preRecords.size(), list.getNumberOfRows(),
        "Smoothing must not silently add/remove rows in the governed path");
    if (pointsOutput.getParent() != null) {
      Files.createDirectories(pointsOutput.getParent());
    }

    List<Map<String, Object>> records = new ArrayList<>(list.getNumberOfRows());
    long completePointCount = 0L;
    try (BufferedWriter writer = Files.newBufferedWriter(pointsOutput, StandardCharsets.UTF_8)) {
      writer.write(POINT_SIDECAR_HEADER);
      for (int rowIndex = 0; rowIndex < list.getNumberOfRows(); rowIndex++) {
        FeatureListRow row = list.getRow(rowIndex);
        Feature feature = row.getFeature(raw);
        assertNotNull(feature);
        Map<String, Object> pre = preRecords.get(rowIndex);
        MessageDigest featureDigest = MessageDigest.getInstance("SHA-256");

        for (int pointIndex = 0; pointIndex < feature.getScanNumbers().size(); pointIndex++) {
          Scan scan = feature.getScanAtIndex(pointIndex);
          DataPoint point = feature.getDataPointAtIndex(pointIndex);
          String line = canonicalPointLine(rowIndex, pointIndex, scan, point);
          writer.write(line);
          featureDigest.update(line.getBytes(StandardCharsets.UTF_8));
          completePointCount++;
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("row_index", rowIndex);
        record.put("row_id", row.getID());
        record.put("input_row_id", pre.get("row_id"));
        record.put("input_scan_count", pre.get("scan_count"));
        record.put("input_series_sha256", pre.get("series_sha256"));
        record.put("mz", feature.getMZ());
        record.put("rt", feature.getRT());
        record.put("height", feature.getHeight());
        record.put("area", feature.getArea());
        record.put("representative_scan_number",
            feature.getRepresentativeScan() == null ? null
                : feature.getRepresentativeScan().getScanNumber());
        record.put("rt_range", range(feature.getRawDataPointsRTRange()));
        record.put("mz_range", range(feature.getRawDataPointsMZRange()));
        record.put("intensity_range", range(feature.getRawDataPointsIntensityRange()));
        record.put("scan_count", feature.getScanNumbers().size());
        record.put("series_sha256", HexFormat.of().formatHex(featureDigest.digest()));
        records.add(record);
      }
    }

    return new SnapshotResult(records, completePointCount, sha256(pointsOutput),
        Files.size(pointsOutput));
  }

  private static String canonicalPointLine(int rowIndex, int pointIndex, Scan scan,
      DataPoint point) {
    String mzBits = point == null ? "null"
        : HEX.toHexDigits(Double.doubleToLongBits(point.getMZ()));
    String intensityBits = point == null ? "null"
        : HEX.toHexDigits(Double.doubleToLongBits(point.getIntensity()));
    return rowIndex + "\t"
        + pointIndex + "\t"
        + scan.getScanNumber() + "\t"
        + HEX.toHexDigits(Float.floatToIntBits(scan.getRetentionTime())) + "\t"
        + mzBits + "\t"
        + intensityBits + "\n";
  }

  private static List<Number> range(Range<? extends Number> range) {
    return List.of(range.lowerEndpoint(), range.upperEndpoint());
  }

  private static String featureSeriesSha256(Feature feature) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (int index = 0; index < feature.getScanNumbers().size(); index++) {
      Scan scan = feature.getScanAtIndex(index);
      DataPoint point = feature.getDataPointAtIndex(index);
      String token = point == null
          ? scan.getScanNumber() + "|null\n"
          : scan.getScanNumber() + "|"
              + Long.toHexString(Double.doubleToLongBits(point.getMZ())) + "|"
              + Long.toHexString(Double.doubleToLongBits(point.getIntensity())) + "\n";
      digest.update(token.getBytes(StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void verifyHash(Path path, String expected) throws Exception {
    assertEquals(expected, sha256(path), "Governed file SHA-256 changed: " + path.getFileName());
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read > 0) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String sha256(String value) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing governed environment variable " + name);
    }
    return value;
  }

  private record SnapshotResult(List<Map<String, Object>> records, long completePointCount,
                                String pointsSha256, long pointsBytes) {
  }
}
