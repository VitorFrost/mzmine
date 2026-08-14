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
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderTask;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetectorParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingModule;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingParameters;
import io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingTask;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_spectral_library.SpectralLibraryImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.MemoryMapStorage;
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

/** Produces one governed smoothing side report in a dedicated fresh test-worker JVM. */
class MZmineSmoothingSideAcceptanceTest {

  private static final String SMOOTHING_MODULE_CLASS =
      "io.github.mzmine.modules.dataprocessing.featdet_smoothing.SmoothingModule";
  private static final String PROBE_CLASS =
      "io.github.mzmine.modules.dataprocessing.featdet_smoothing.V408SmoothingTaskProbe";
  private static final ObjectMapper JSON = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @AfterEach
  void cleanProject() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  @Timeout(value = 20, unit = MINUTES)
  void producesOneGovernedSmoothingParitySide() throws Exception {
    Map<String, String> env = System.getenv();
    String inputValue = env.get("MZMINE_SMOOTHING_PARITY_INPUT");
    String settingsValue = env.get("MZMINE_SMOOTHING_PARITY_SETTINGS");
    String outputValue = env.get("MZMINE_SMOOTHING_PARITY_SIDE_OUTPUT");
    String side = env.get("MZMINE_SMOOTHING_PARITY_SIDE");
    Assumptions.assumeTrue(inputValue != null && settingsValue != null && outputValue != null
        && side != null, "Governed smoothing inputs were not supplied");
    assertTrue(side.equals("candidate") || side.equals("oracle"));

    Path input = Path.of(inputValue).toAbsolutePath().normalize();
    Path settings = Path.of(settingsValue).toAbsolutePath().normalize();
    Path output = Path.of(outputValue).toAbsolutePath().normalize();
    verifyHash(input, requireEnvironment("MZMINE_SMOOTHING_PARITY_INPUT_SHA256"));
    verifyHash(settings, requireEnvironment("MZMINE_SMOOTHING_PARITY_SETTINGS_SHA256"));

    Class<?> probeClass = side.equals("oracle") ? Class.forName(PROBE_CLASS) : null;
    if (probeClass != null) {
      assertTrue(Task.class.isAssignableFrom(probeClass));
    }

    MZmineTestUtil.cleanProject();
    importMzml(input);
    MZmineProject project = MZmineCore.getProjectManager().getCurrentProject();
    assertEquals(1, project.getCurrentRawDataFiles().size());
    RawDataFile raw = project.getCurrentRawDataFiles().get(0);
    runCentroidMassDetection(raw);

    ADAPChromatogramBuilderParameters adap = loadPublishedAdapParameters(settings, raw);
    ModularFeatureList adapList = (ModularFeatureList) runAdap(project, raw, adap.cloneParameterSet(true));
    List<Map<String, Object>> inputRecords = snapshot(adapList, raw);
    String inputRecordsSha = sha256(JSON.writeValueAsString(inputRecords));

    SmoothingParameters smoothing = loadPublishedSmoothingParameters(settings);
    String algorithmClass = smoothing.getValue(SmoothingParameters.smoothingAlgorithm)
        .getModule().getClass().getName();
    String algorithmParametersClass = smoothing.getValue(SmoothingParameters.smoothingAlgorithm)
        .getParameterSet().getClass().getName();

    FeatureList smoothed = side.equals("candidate")
        ? runCandidateSmoothing(project, adapList, smoothing.cloneParameterSet(true))
        : runOracleSmoothing(probeClass, project, adapList, smoothing.cloneParameterSet(true));
    List<Map<String, Object>> outputRecords = snapshot(smoothed, raw);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 1);
    report.put("gate_id", "mzmine-v4.0.8-feature-smoothing-side-report-v1");
    report.put("implementation", side);
    report.put("candidate_commit", requireEnvironment("MZMINE_SMOOTHING_PARITY_CANDIDATE_COMMIT"));
    report.put("oracle_commit", "8029f930d28c0447f0acf2bcabef0a79865ad434");
    report.put("input_sha256", sha256(input));
    report.put("published_settings_sha256", sha256(settings));
    report.put("ms1_scan_count", raw.getScans().stream().filter(scan -> scan.getMSLevel() == 1).count());
    report.put("adap_input_feature_count", inputRecords.size());
    report.put("adap_input_records_sha256", inputRecordsSha);
    report.put("smoothing_algorithm_class", algorithmClass);
    report.put("smoothing_algorithm_parameters_class", algorithmParametersClass);
    report.put("smoothing_suffix", smoothing.getValue(SmoothingParameters.suffix));
    report.put("handle_original", smoothing.getValue(SmoothingParameters.handleOriginal).toString());
    report.put("output_feature_count", outputRecords.size());
    report.put("output_records_sha256", sha256(JSON.writeValueAsString(outputRecords)));
    report.put("records", outputRecords);

    Path parent = output.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    JSON.writeValue(output.toFile(), report);
    assertTrue(Files.isRegularFile(output));
  }

  private static void importMzml(Path input) throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(AllSpectralDataImportModule.class).cloneParameterSet();
    parameters.setParameter(AllSpectralDataImportParameters.fileNames, new File[]{input.toFile()});
    parameters.setParameter(AllSpectralDataImportParameters.advancedImport, false);
    parameters.setParameter(SpectralLibraryImportParameters.dataBaseFiles, new File[0]);
    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(10, MINUTES, AllSpectralDataImportModule.class, parameters));
  }

  private static void runCentroidMassDetection(RawDataFile raw) throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(MassDetectionModule.class).cloneParameterSet();
    ParameterSet centroid = new CentroidMassDetectorParameters();
    centroid.setParameter(CentroidMassDetectorParameters.noiseLevel, 0d);
    centroid.setParameter(CentroidMassDetectorParameters.detectIsotopes, false);
    parameters.setParameter(MassDetectionParameters.massDetector,
        new MZmineProcessingStepImpl<>(new CentroidMassDetector(), centroid));
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    parameters.setParameter(MassDetectionParameters.outFilenameOption, false);
    assertInstanceOf(CentroidMassDetector.class,
        parameters.getValue(MassDetectionParameters.massDetector).getModule());
    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(10, MINUTES, MassDetectionModule.class, parameters));
  }

  private static ADAPChromatogramBuilderParameters loadPublishedAdapParameters(Path settings,
      RawDataFile raw) throws Exception {
    Element step = findStep(settings,
        "io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule");
    ADAPChromatogramBuilderParameters parameters = new ADAPChromatogramBuilderParameters();
    parameters.loadValuesFromXML(step);
    parameters.setParameter(ADAPChromatogramBuilderParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{raw}));
    parameters.setParameter(ADAPChromatogramBuilderParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(ADAPChromatogramBuilderParameters.suffix, "smoothing-parity-input");
    return parameters;
  }

  private static SmoothingParameters loadPublishedSmoothingParameters(Path settings) throws Exception {
    Element step = findStep(settings, SMOOTHING_MODULE_CLASS);
    SmoothingParameters parameters = new SmoothingParameters();
    parameters.loadValuesFromXML(step);
    return parameters;
  }

  private static Element findStep(Path settings, String moduleClass) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    try { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); } catch (IllegalArgumentException ignored) {}
    try { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); } catch (IllegalArgumentException ignored) {}
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    Document document = factory.newDocumentBuilder().parse(settings.toFile());
    NodeList steps = document.getDocumentElement().getElementsByTagName("batchstep");
    Element found = null;
    for (int i = 0; i < steps.getLength(); i++) {
      Element candidate = (Element) steps.item(i);
      if (moduleClass.equals(candidate.getAttribute("method"))) {
        if (found != null) throw new IllegalStateException("Duplicate batch step " + moduleClass);
        found = candidate;
      }
    }
    assertNotNull(found, "Published workflow missing " + moduleClass);
    return found;
  }

  private static FeatureList runAdap(MZmineProject project, RawDataFile raw, ParameterSet params) {
    int before = project.getNumberOfFeatureLists();
    Task task = ModularADAPChromatogramBuilderTask.forChromatography(project, raw, params, null,
        Instant.EPOCH, ModularADAPChromatogramBuilderModule.class);
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(before + 1, project.getNumberOfFeatureLists());
    return project.getCurrentFeatureLists().get(project.getNumberOfFeatureLists() - 1);
  }

  private static FeatureList runCandidateSmoothing(MZmineProject project, ModularFeatureList input,
      ParameterSet params) {
    List<FeatureList> before = new ArrayList<>(project.getCurrentFeatureLists());
    Task task = new SmoothingTask(project, input, null, params, Instant.EPOCH);
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    return newFeatureList(project, before, input);
  }

  private static FeatureList runOracleSmoothing(Class<?> probeClass, MZmineProject project,
      ModularFeatureList input, ParameterSet params) throws Exception {
    List<FeatureList> before = new ArrayList<>(project.getCurrentFeatureLists());
    Constructor<?> constructor = probeClass.getConstructor(MZmineProject.class, ModularFeatureList.class,
        MemoryMapStorage.class, ParameterSet.class, Instant.class);
    Task task;
    try {
      task = (Task) constructor.newInstance(project, input, null, params, Instant.EPOCH);
    } catch (InvocationTargetException error) {
      if (error.getCause() instanceof Exception exception) throw exception;
      throw error;
    }
    task.run();
    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    return newFeatureList(project, before, input);
  }

  private static FeatureList newFeatureList(MZmineProject project, List<FeatureList> before,
      FeatureList input) {
    for (FeatureList current : project.getCurrentFeatureLists()) {
      if (current != input && !before.contains(current)) return current;
    }
    if (project.getCurrentFeatureLists().size() == 1
        && project.getCurrentFeatureLists().get(0) != input) {
      return project.getCurrentFeatureLists().get(0);
    }
    throw new AssertionError("Smoothing output feature list was not uniquely identified");
  }

  private static List<Map<String, Object>> snapshot(FeatureList list, RawDataFile raw) throws Exception {
    List<Map<String, Object>> records = new ArrayList<>(list.getNumberOfRows());
    for (int rowIndex = 0; rowIndex < list.getNumberOfRows(); rowIndex++) {
      FeatureListRow row = list.getRow(rowIndex);
      Feature feature = row.getFeature(raw);
      assertNotNull(feature);
      Map<String, Object> record = new LinkedHashMap<>();
      record.put("row_index", rowIndex);
      record.put("row_id", row.getID());
      record.put("mz", feature.getMZ());
      record.put("rt", feature.getRT());
      record.put("height", feature.getHeight());
      record.put("area", feature.getArea());
      record.put("representative_scan_number",
          feature.getRepresentativeScan() == null ? null : feature.getRepresentativeScan().getScanNumber());
      record.put("rt_range", range(feature.getRawDataPointsRTRange()));
      record.put("mz_range", range(feature.getRawDataPointsMZRange()));
      record.put("intensity_range", range(feature.getRawDataPointsIntensityRange()));
      record.put("scan_count", feature.getScanNumbers().size());
      record.put("series_sha256", featureSeriesSha256(feature));
      record.put("sampled_points", sampledPoints(feature));
      records.add(record);
    }
    return records;
  }

  private static List<Number> range(Range<? extends Number> range) {
    return List.of(range.lowerEndpoint(), range.upperEndpoint());
  }

  private static String featureSeriesSha256(Feature feature) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (int index = 0; index < feature.getScanNumbers().size(); index++) {
      Scan scan = feature.getScanAtIndex(index);
      DataPoint point = feature.getDataPointAtIndex(index);
      String token = point == null ? scan.getScanNumber() + "|null\n"
          : scan.getScanNumber() + "|" + Long.toHexString(Double.doubleToLongBits(point.getMZ()))
              + "|" + Long.toHexString(Double.doubleToLongBits(point.getIntensity())) + "\n";
      digest.update(token.getBytes(StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static List<Map<String, Object>> sampledPoints(Feature feature) {
    int size = feature.getScanNumbers().size();
    if (size == 0) return List.of();
    int[] indexes = size == 1 ? new int[]{0} : size == 2 ? new int[]{0, 1}
        : new int[]{0, size / 2, size - 1};
    List<Map<String, Object>> points = new ArrayList<>();
    for (int index : indexes) {
      Map<String, Object> record = new LinkedHashMap<>();
      Scan scan = feature.getScanAtIndex(index);
      DataPoint point = feature.getDataPointAtIndex(index);
      record.put("index", index);
      record.put("scan_number", scan.getScanNumber());
      record.put("mz", point == null ? null : point.getMZ());
      record.put("intensity", point == null ? null : point.getIntensity());
      points.add(record);
    }
    return points;
  }

  private static void verifyHash(Path path, String expected) throws Exception {
    assertEquals(expected, sha256(path));
  }

  private static String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(path)) {
      byte[] buffer = new byte[1024 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String requireEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
    return value;
  }
}
