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
package io.github.mzmine.modules.batchmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyCSVExportModule;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyCSVExportParameters;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowCommonElement;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowDataFileElement;
import io.github.mzmine.modules.io.export_features_gnps.fbmn.FeatureListRowsFilter;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Generates a real headless batch from the frozen published settings and approved public mzML files.
 *
 * <p>The test only prepares artifacts. Processing happens in a separate MZmine CLI process so a
 * module failure, process exit, timeout or unexpected authentication/network output can be recorded
 * independently by the runner.</p>
 */
class PublicWorkflowHeadlessFixtureTest {

  private static final String SETTINGS_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZMINE_SETTINGS";
  private static final String REPLICATE_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZML_REPLICATE_2";
  private static final String BLANK_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZML_BLANK_1";

  static final Path OUTPUT_DIRECTORY = Path.of("build", "public_workflow_execution").toAbsolutePath();
  static final Path BATCH_FILE = OUTPUT_DIRECTORY.resolve("public_workflow.mzbatch");
  static final Path CSV_FILE = OUTPUT_DIRECTORY.resolve("public_workflow_features.csv");
  static final Path FIXTURE_REPORT = OUTPUT_DIRECTORY.resolve("public_workflow_fixture.json");

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void serializesPublishedWorkflowWithApprovedPublicMzmlInputs() throws Exception {
    final Path settings = requiredEnvironmentFile(SETTINGS_ENVIRONMENT);
    final Path replicate = requiredEnvironmentFile(REPLICATE_ENVIRONMENT);
    final Path blank = requiredEnvironmentFile(BLANK_ENVIRONMENT);

    Files.createDirectories(OUTPUT_DIRECTORY);
    Files.deleteIfExists(CSV_FILE);
    Files.deleteIfExists(BATCH_FILE);
    Files.deleteIfExists(FIXTURE_REPORT);

    final DocumentBuilderFactory sourceFactory = secureFactory();
    assertRejectsDoctype(sourceFactory);
    final Document sourceDocument = sourceFactory.newDocumentBuilder().parse(settings.toFile());
    final Element sourceRoot = sourceDocument.getDocumentElement();
    assertEquals("batch", sourceRoot.getTagName());
    assertEquals("3.4.27", sourceRoot.getAttribute("mzmine_version"));

    final List<Element> publishedSteps = directChildren(sourceRoot, "batchstep");
    assertEquals(10, publishedSteps.size());

    final BatchQueue queue = new BatchQueue();
    final List<Map<String, Object>> stepInventory = new ArrayList<>();

    final MSDKmzMLImportModule importModule = new MSDKmzMLImportModule();
    final MSDKmzMLImportParameters importParameters = new MSDKmzMLImportParameters();
    importParameters.setParameter(MSDKmzMLImportParameters.fileNames,
        new File[]{replicate.toFile().getAbsoluteFile(), blank.toFile().getAbsoluteFile()});
    addStep(queue, importModule, importParameters);
    stepInventory.add(stepRecord(1, "fixture-import", importModule.getClass().getName(),
        importModule.getName(), null));

    for (int index = 0; index < publishedSteps.size(); index++) {
      final Element publishedStep = publishedSteps.get(index);
      final String moduleClassName = publishedStep.getAttribute("method");
      final Class<?> rawModuleClass = Class.forName(moduleClassName, true,
          Thread.currentThread().getContextClassLoader());
      assertTrue(MZmineProcessingModule.class.isAssignableFrom(rawModuleClass),
          () -> moduleClassName + " is not an MZmineProcessingModule");

      final MZmineProcessingModule module =
          (MZmineProcessingModule) rawModuleClass.getDeclaredConstructor().newInstance();
      final Class<? extends ParameterSet> parameterSetClass =
          ((MZmineModule) module).getParameterSetClass();
      assertTrue(parameterSetClass != null,
          () -> moduleClassName + " unexpectedly has no ParameterSet class");
      final ParameterSet parameterSet = parameterSetClass.getDeclaredConstructor().newInstance();
      parameterSet.loadValuesFromXML(publishedStep);
      final List<String> parameterErrors = new ArrayList<>();
      assertTrue(parameterSet.checkParameterValues(parameterErrors),
          () -> "Invalid published values before execution for " + moduleClassName + ": "
              + parameterErrors);

      addStep(queue, module, parameterSet);
      stepInventory.add(stepRecord(index + 2, "published", moduleClassName, module.getName(),
          index + 1));
    }

    final LegacyCSVExportModule exportModule = new LegacyCSVExportModule();
    final LegacyCSVExportParameters exportParameters = exportParameters();
    addStep(queue, exportModule, exportParameters);
    stepInventory.add(stepRecord(queue.size(), "fixture-export", exportModule.getClass().getName(),
        exportModule.getName(), null));

    final Document outputDocument = secureFactory().newDocumentBuilder().newDocument();
    final Element outputRoot = outputDocument.createElement("batch");
    outputRoot.setAttribute("mzmine_version", "open-offline-public-workflow");
    outputDocument.appendChild(outputRoot);
    queue.saveToXml(outputRoot);

    final var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(outputDocument), new StreamResult(BATCH_FILE.toFile()));

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 1);
    report.put("source_settings", settings.toString());
    report.put("source_mzmine_version", sourceRoot.getAttribute("mzmine_version"));
    report.put("replicate_mzml", replicate.toString());
    report.put("blank_mzml", blank.toString());
    report.put("batch_file", BATCH_FILE.toString());
    report.put("expected_csv", CSV_FILE.toString());
    report.put("published_step_count", publishedSteps.size());
    report.put("total_batch_step_count", queue.size());
    report.put("workflow_executed", false);
    report.put("steps", stepInventory);
    MAPPER.writeValue(FIXTURE_REPORT.toFile(), report);

    assertEquals(12, queue.size());
    assertTrue(Files.isRegularFile(BATCH_FILE));
    assertTrue(Files.size(BATCH_FILE) > Files.size(settings));
    assertTrue(Files.isRegularFile(FIXTURE_REPORT));
    assertFalse(Files.exists(CSV_FILE), "CSV must be created only by the child CLI process");
  }

  private static Path requiredEnvironmentFile(final String name) {
    final String value = System.getenv(name);
    Assumptions.assumeTrue(value != null && !value.isBlank(), () -> "Set " + name + "=<path>");
    final Path path = Path.of(value).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(path), () -> "Missing required file for " + name + ": " + path);
    return path;
  }

  private static LegacyCSVExportParameters exportParameters() {
    final LegacyCSVExportParameters parameters = new LegacyCSVExportParameters();
    parameters.setParameter(LegacyCSVExportParameters.featureLists,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    parameters.setParameter(LegacyCSVExportParameters.filename, CSV_FILE.toFile().getAbsoluteFile());
    parameters.setParameter(LegacyCSVExportParameters.fieldSeparator, ",");
    parameters.setParameter(LegacyCSVExportParameters.exportCommonItems,
        new LegacyExportRowCommonElement[]{LegacyExportRowCommonElement.ROW_ID,
            LegacyExportRowCommonElement.ROW_MZ, LegacyExportRowCommonElement.ROW_RT});
    parameters.setParameter(LegacyCSVExportParameters.exportDataFileItems,
        new LegacyExportRowDataFileElement[]{LegacyExportRowDataFileElement.FEATURE_STATUS,
            LegacyExportRowDataFileElement.FEATURE_MZ,
            LegacyExportRowDataFileElement.FEATURE_RT,
            LegacyExportRowDataFileElement.FEATURE_HEIGHT});
    parameters.setParameter(LegacyCSVExportParameters.exportAllFeatureInfo, false);
    parameters.setParameter(LegacyCSVExportParameters.idSeparator, ";");
    parameters.setParameter(LegacyCSVExportParameters.filter, FeatureListRowsFilter.ALL);
    return parameters;
  }

  private static Map<String, Object> stepRecord(final int batchStep, final String origin,
      final String moduleClass, final String moduleName, final Integer publishedStep) {
    final Map<String, Object> record = new LinkedHashMap<>();
    record.put("batch_step", batchStep);
    record.put("origin", origin);
    record.put("published_step", publishedStep);
    record.put("module_class", moduleClass);
    record.put("module_name", moduleName);
    return record;
  }

  private static void addStep(final BatchQueue queue, final MZmineProcessingModule module,
      final ParameterSet parameters) {
    queue.add(new MZmineProcessingStepImpl<MZmineProcessingModule>(module, parameters));
  }

  private static List<Element> directChildren(final Element parent, final String tagName) {
    final List<Element> children = new ArrayList<>();
    final NodeList nodes = parent.getChildNodes();
    for (int index = 0; index < nodes.getLength(); index++) {
      final Node node = nodes.item(index);
      if (node instanceof Element element && tagName.equals(element.getTagName())) {
        children.add(element);
      }
    }
    return children;
  }

  private static DocumentBuilderFactory secureFactory() throws Exception {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    setOptionalSecurityAttribute(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
    setOptionalSecurityAttribute(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory;
  }

  private static void setOptionalSecurityAttribute(final DocumentBuilderFactory factory,
      final String attribute, final String value) {
    try {
      factory.setAttribute(attribute, value);
    } catch (IllegalArgumentException unsupportedByLegacyParser) {
      // Mandatory entity/DTD features remain enabled and are verified below.
    }
  }

  private static void assertRejectsDoctype(final DocumentBuilderFactory factory) {
    final String maliciousXml = """
        <?xml version="1.0"?>
        <!DOCTYPE batch [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <batch>&xxe;</batch>
        """;
    assertThrows(SAXException.class,
        () -> factory.newDocumentBuilder().parse(new InputSource(new StringReader(maliciousXml))),
        "Secure XML factory must reject DOCTYPE declarations");
  }
}
