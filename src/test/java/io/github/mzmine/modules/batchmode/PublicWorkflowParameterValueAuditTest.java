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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Verifies canonical parameter values and serializer idempotence for the frozen public workflow.
 *
 * <p>The comparison ignores indentation, line endings, Boolean case and numerically equivalent
 * decimal formatting. Repeated entries are deduplicated only for an XML parameter explicitly named
 * {@code Chemical elements}, because that parameter represents a set and the published XML contains
 * the same element sequence repeatedly. A slash inserted before a Windows drive letter is normalized
 * only in {@code current_file} elements, reflecting the same path on Unix and Windows. Every other
 * text value remains literal and order-sensitive. The published value is saved once, loaded into a
 * fresh clone and saved again so source migration remains distinct from serializer instability.</p>
 */
class PublicWorkflowParameterValueAuditTest {

  private static final String SETTINGS_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZMINE_SETTINGS";
  private static final Path EXPECTED_INVENTORY = Path.of("datasets", "expected",
      "zenodo_14000687_mzmine_settings_inventory.json");
  private static final Path OUTPUT = Path.of("build", "public_workflow_validation",
      "mzmine_settings_value_audit.json");
  private static final String PARAMETER_TAG = "parameter";
  private static final String CHEMICAL_ELEMENTS_PATH_SUFFIX =
      "/parameter[@name='Chemical elements']";
  private static final Pattern DECIMAL = Pattern.compile(
      "[+-]?(?:(?:\\d+(?:\\.\\d*)?)|(?:\\.\\d+))(?:[eE][+-]?\\d+)?");
  private static final Pattern ELEMENT_SYMBOL = Pattern.compile("[A-Z][a-z]?");
  private static final Pattern WINDOWS_DRIVE_WITH_LEADING_SLASH = Pattern.compile(
      "^/([A-Za-z]:[\\\\/].*)$");

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void auditsCanonicalValuesAndSerializerIdempotence() throws Exception {
    final String configuredPath = System.getenv(SETTINGS_ENVIRONMENT);
    Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
        () -> "Set " + SETTINGS_ENVIRONMENT + "=<path> to run the public value audit");

    final Path settingsPath = Path.of(configuredPath).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(settingsPath), () -> "Missing settings XML: " + settingsPath);

    final ExpectedWorkflow expected = readExpectedWorkflow(EXPECTED_INVENTORY);
    final DocumentBuilderFactory sourceFactory = secureFactory();
    assertRejectsDoctype(sourceFactory);
    final Document sourceDocument = sourceFactory.newDocumentBuilder().parse(settingsPath.toFile());
    final Element root = sourceDocument.getDocumentElement();
    assertEquals("batch", root.getTagName());

    final List<Element> stepElements = directChildren(root, "batchstep");
    assertEquals(expected.steps().size(), stepElements.size());

    final List<Map<String, Object>> stepReports = new ArrayList<>();
    final List<String> unknownParameters = new ArrayList<>();
    final List<String> loadErrors = new ArrayList<>();
    final List<String> sourceRoundTripChanges = new ArrayList<>();
    final List<String> nonIdempotentParameters = new ArrayList<>();
    int auditedParameterCount = 0;
    int auditedSemanticNodeCount = 0;

    for (int stepIndex = 0; stepIndex < stepElements.size(); stepIndex++) {
      final Element stepElement = stepElements.get(stepIndex);
      final ExpectedStep expectedStep = expected.steps().get(stepIndex);
      final String moduleClassName = stepElement.getAttribute("method");
      assertEquals(expectedStep.className(), moduleClassName,
          "Published module order or allowlisted class changed at step " + (stepIndex + 1));

      final Class<?> rawModuleClass = Class.forName(moduleClassName, true,
          Thread.currentThread().getContextClassLoader());
      assertTrue(MZmineModule.class.isAssignableFrom(rawModuleClass),
          () -> moduleClassName + " is not an MZmineModule");
      final MZmineModule module = (MZmineModule) rawModuleClass.getDeclaredConstructor().newInstance();
      final Class<? extends ParameterSet> parameterSetClass = module.getParameterSetClass();
      assertTrue(parameterSetClass != null,
          () -> moduleClassName + " unexpectedly has no ParameterSet class");
      final ParameterSet parameterSet = parameterSetClass.getDeclaredConstructor().newInstance();
      final Map<String, Parameter<?>> parameterByName = parameterSet.getNameParameterMap();

      final List<Map<String, Object>> parameterReports = new ArrayList<>();
      for (final Element publishedParameter : directChildren(stepElement, PARAMETER_TAG)) {
        auditedParameterCount++;
        final String publishedName = publishedParameter.getAttribute("name");
        final String parameterLabel = (stepIndex + 1) + ":" + moduleClassName + ":" + publishedName;
        final Parameter<?> localParameter = parameterByName.get(publishedName);
        final Map<String, Object> parameterReport = new LinkedHashMap<>();
        parameterReport.put("published_name", publishedName);

        if (localParameter == null) {
          parameterReport.put("status", "unknown");
          unknownParameters.add(parameterLabel);
          parameterReports.add(parameterReport);
          continue;
        }

        parameterReport.put("canonical_name", localParameter.getName());
        parameterReport.put("parameter_class", localParameter.getClass().getName());
        try {
          final Parameter<?> firstClone = localParameter.cloneParameter();
          firstClone.loadValueFromXML(publishedParameter);
          final Element firstRoundTrip = saveParameter(firstClone, localParameter.getName());

          final Parameter<?> secondClone = localParameter.cloneParameter();
          secondClone.loadValueFromXML(firstRoundTrip);
          final Element secondRoundTrip = saveParameter(secondClone, localParameter.getName());

          final Map<String, List<String>> sourceValues = semanticValues(publishedParameter);
          final Map<String, List<String>> firstValues = semanticValues(firstRoundTrip);
          final Map<String, List<String>> secondValues = semanticValues(secondRoundTrip);
          final int semanticNodeCount = sourceValues.values().stream().mapToInt(List::size).sum();
          auditedSemanticNodeCount += semanticNodeCount;

          final List<Map<String, Object>> sourceDifferences = compareValues(sourceValues, firstValues);
          final List<Map<String, Object>> idempotenceDifferences = compareValues(firstValues,
              secondValues);
          final boolean sourceEquivalent = sourceDifferences.isEmpty();
          final boolean serializerIdempotent = idempotenceDifferences.isEmpty();

          parameterReport.put("status", "loaded");
          parameterReport.put("semantic_node_count", semanticNodeCount);
          parameterReport.put("source_semantic_sha256", semanticSha256(sourceValues));
          parameterReport.put("roundtrip_1_semantic_sha256", semanticSha256(firstValues));
          parameterReport.put("roundtrip_2_semantic_sha256", semanticSha256(secondValues));
          parameterReport.put("source_roundtrip_semantically_equal", sourceEquivalent);
          parameterReport.put("serializer_idempotent", serializerIdempotent);
          parameterReport.put("source_roundtrip_differences", sourceDifferences);
          parameterReport.put("idempotence_differences", idempotenceDifferences);

          if (!sourceEquivalent) {
            sourceRoundTripChanges.add(parameterLabel);
          }
          if (!serializerIdempotent) {
            nonIdempotentParameters.add(parameterLabel);
          }
        } catch (Throwable error) {
          final String message = error.getClass().getName() + ": "
              + String.valueOf(error.getMessage());
          parameterReport.put("status", "error");
          parameterReport.put("error", message);
          loadErrors.add(parameterLabel + " -> " + message);
        }
        parameterReports.add(parameterReport);
      }

      final Map<String, Object> stepReport = new LinkedHashMap<>();
      stepReport.put("order", stepIndex + 1);
      stepReport.put("module_class", moduleClassName);
      stepReport.put("module_name", module.getName());
      stepReport.put("parameter_set_class", parameterSetClass.getName());
      stepReport.put("published_parameter_version",
          Integer.parseInt(stepElement.getAttribute("parameter_version")));
      stepReport.put("local_parameter_version", parameterSet.getVersion());
      stepReport.put("parameters", parameterReports);
      stepReports.add(stepReport);
    }

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 3);
    report.put("comparison_policy",
        "Element paths, attributes and direct values; numeric formatting, Boolean case, repeated Chemical elements set entries and leading slash before Windows drive in current_file normalized");
    report.put("chemical_element_normalization_scope",
        "Only parameter elements explicitly named Chemical elements; first-seen order preserved");
    report.put("windows_drive_normalization_scope",
        "Only current_file elements matching /<drive>:/...; path body and separators remain significant");
    report.put("source_file", settingsPath.toString());
    report.put("source_sha256", expected.sourceSha256());
    report.put("published_mzmine_version", root.getAttribute("mzmine_version"));
    report.put("step_count", stepReports.size());
    report.put("audited_top_level_parameter_count", auditedParameterCount);
    report.put("audited_semantic_node_count", auditedSemanticNodeCount);
    report.put("unknown_parameter_count", unknownParameters.size());
    report.put("unknown_parameters", unknownParameters);
    report.put("load_error_count", loadErrors.size());
    report.put("load_errors", loadErrors);
    report.put("source_roundtrip_change_count", sourceRoundTripChanges.size());
    report.put("source_roundtrip_changes", sourceRoundTripChanges);
    report.put("non_idempotent_parameter_count", nonIdempotentParameters.size());
    report.put("non_idempotent_parameters", nonIdempotentParameters);
    report.put("workflow_executed", false);
    report.put("steps", stepReports);

    Files.createDirectories(OUTPUT.getParent());
    MAPPER.writeValue(OUTPUT.toFile(), report);
    System.out.println("PUBLIC_WORKFLOW_PARAMETER_VALUE_AUDIT=" + OUTPUT.toAbsolutePath());
    System.out.println(MAPPER.writeValueAsString(report));

    assertTrue(unknownParameters.isEmpty(),
        () -> "Unknown published parameters: " + unknownParameters);
    assertTrue(loadErrors.isEmpty(), () -> "Published parameter load errors: " + loadErrors);
    assertTrue(sourceRoundTripChanges.isEmpty(),
        () -> "Published values changed after canonical round-trip: " + sourceRoundTripChanges);
    assertTrue(nonIdempotentParameters.isEmpty(),
        () -> "Parameter serializers are not idempotent: " + nonIdempotentParameters);
  }

  @Test
  void canonicalizesOnlyExplicitChemicalElementSets() {
    final String path = "/parameter[@name='Mass detector']/module[@name='Centroid']"
        + CHEMICAL_ELEMENTS_PATH_SUFFIX;
    assertEquals("H,C,N,O,S",
        canonicalScalar("H,C,N,O,S,H,C,N,O,S", path));
    assertEquals("H,C,N,O,S",
        canonicalScalar("H,C,N,O,S", path));
    assertEquals("H,C,N,O,S,H,C,N,O,S",
        canonicalScalar("H,C,N,O,S,H,C,N,O,S", "/parameter[@name='Free text']"));
  }

  @Test
  void canonicalizesOnlyWindowsDrivePrefixInCurrentFileElements() {
    final String currentFilePath =
        "/parameter[@name='Output netCDF filename (optional)']/current_file";
    assertEquals("C:\\Program Files\\MZmine",
        canonicalScalar("/C:\\Program Files\\MZmine", currentFilePath));
    assertEquals("C:\\Program Files\\MZmine",
        canonicalScalar("C:\\Program Files\\MZmine", currentFilePath));
    assertEquals("/C:\\Program Files\\MZmine",
        canonicalScalar("/C:\\Program Files\\MZmine", "/parameter[@name='Free text']"));
  }

  private static Element saveParameter(final Parameter<?> parameter, final String canonicalName)
      throws Exception {
    final Document document = secureFactory().newDocumentBuilder().newDocument();
    final Element output = document.createElement(PARAMETER_TAG);
    output.setAttribute("name", canonicalName);
    document.appendChild(output);
    parameter.saveValueToXML(output);
    return output;
  }

  private static Map<String, List<String>> semanticValues(final Element root) {
    final Map<String, List<String>> values = new TreeMap<>();
    collectSemanticValues(root, "", values);
    return values;
  }

  private static void collectSemanticValues(final Element element, final String parentPath,
      final Map<String, List<String>> values) {
    final String currentPath = parentPath + "/" + element.getTagName()
        + pathDiscriminator(element);
    final Map<String, String> attributes = new TreeMap<>();
    final NamedNodeMap rawAttributes = element.getAttributes();
    for (int index = 0; index < rawAttributes.getLength(); index++) {
      final Node attribute = rawAttributes.item(index);
      if (!"name".equals(attribute.getNodeName()) && !"method".equals(attribute.getNodeName())) {
        attributes.put(attribute.getNodeName(),
            canonicalScalar(attribute.getNodeValue(), currentPath + "/@" + attribute.getNodeName()));
      }
    }

    final StringBuilder directText = new StringBuilder();
    final NodeList children = element.getChildNodes();
    for (int index = 0; index < children.getLength(); index++) {
      final Node child = children.item(index);
      if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
        directText.append(child.getNodeValue());
      }
    }

    final String token = "attributes=" + attributes + ";text="
        + canonicalScalar(directText.toString(), currentPath);
    values.computeIfAbsent(currentPath, ignored -> new ArrayList<>()).add(token);

    for (int index = 0; index < children.getLength(); index++) {
      final Node child = children.item(index);
      if (child instanceof Element childElement) {
        collectSemanticValues(childElement, currentPath, values);
      }
    }
  }

  private static String pathDiscriminator(final Element element) {
    if (element.hasAttribute("name") && !element.getAttribute("name").isBlank()) {
      return "[@name='" + element.getAttribute("name") + "']";
    }
    if (element.hasAttribute("method") && !element.getAttribute("method").isBlank()) {
      return "[@method='" + element.getAttribute("method") + "']";
    }
    return "";
  }

  private static String canonicalScalar(final String rawValue, final String path) {
    final String value = rawValue == null ? ""
        : rawValue.replace("\r\n", "\n").replace('\r', '\n').trim();
    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
      return value.toLowerCase(Locale.ROOT);
    }
    if (path.endsWith("/current_file")) {
      final var driveMatch = WINDOWS_DRIVE_WITH_LEADING_SLASH.matcher(value);
      if (driveMatch.matches()) {
        return driveMatch.group(1);
      }
    }
    if (path.endsWith(CHEMICAL_ELEMENTS_PATH_SUFFIX)) {
      final String canonicalElements = canonicalChemicalElements(value);
      if (canonicalElements != null) {
        return canonicalElements;
      }
    }
    if (DECIMAL.matcher(value).matches()) {
      try {
        final BigDecimal decimal = new BigDecimal(value).stripTrailingZeros();
        return decimal.signum() == 0 ? "0" : decimal.toPlainString();
      } catch (NumberFormatException ignored) {
        // Preserve the original value when it is not a valid BigDecimal after all.
      }
    }
    return value;
  }

  private static String canonicalChemicalElements(final String value) {
    if (!value.contains(",")) {
      return null;
    }
    final Set<String> unique = new LinkedHashSet<>();
    for (final String rawToken : value.split(",", -1)) {
      final String token = rawToken.trim();
      if (token.isEmpty() || !ELEMENT_SYMBOL.matcher(token).matches()) {
        return null;
      }
      unique.add(token);
    }
    return String.join(",", unique);
  }

  private static List<Map<String, Object>> compareValues(
      final Map<String, List<String>> left, final Map<String, List<String>> right) {
    final Set<String> paths = new TreeSet<>(left.keySet());
    paths.addAll(right.keySet());
    final List<Map<String, Object>> differences = new ArrayList<>();
    for (final String path : paths) {
      final List<String> leftValues = left.getOrDefault(path, List.of());
      final List<String> rightValues = right.getOrDefault(path, List.of());
      if (!Objects.equals(leftValues, rightValues)) {
        final Map<String, Object> difference = new LinkedHashMap<>();
        difference.put("path", path);
        difference.put("source", leftValues);
        difference.put("roundtrip", rightValues);
        differences.add(difference);
      }
    }
    return differences;
  }

  private static String semanticSha256(final Map<String, List<String>> values) throws Exception {
    final MessageDigest digest = MessageDigest.getInstance("SHA-256");
    final byte[] canonicalJson = MAPPER.writeValueAsString(values).getBytes(StandardCharsets.UTF_8);
    return HexFormat.of().formatHex(digest.digest(canonicalJson));
  }

  private static ExpectedWorkflow readExpectedWorkflow(final Path path) throws Exception {
    final Map<?, ?> root = MAPPER.readValue(path.toFile(), Map.class);
    final String sourceSha = String.valueOf(root.get("source_sha256"));
    final Collection<?> rawSteps = (Collection<?>) root.get("steps");
    final List<ExpectedStep> steps = new ArrayList<>();
    for (final Object rawStep : rawSteps) {
      final Map<?, ?> step = (Map<?, ?>) rawStep;
      steps.add(new ExpectedStep(
          ((Number) step.get("order")).intValue(),
          String.valueOf(step.get("class"))));
    }
    steps.sort(Comparator.comparingInt(ExpectedStep::order));
    return new ExpectedWorkflow(sourceSha, List.copyOf(steps));
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
      // Mandatory entity/DTD features remain enabled and are verified by assertRejectsDoctype.
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

  private record ExpectedWorkflow(String sourceSha256, List<ExpectedStep> steps) {
  }

  private record ExpectedStep(int order, String className) {
  }
}
