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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mzmine.modules.MZmineModule;
import io.github.mzmine.parameters.Parameter;
import io.github.mzmine.parameters.ParameterSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Audits a frozen, allowlisted public MZmine batch without running any processing module.
 *
 * <p>Unlike {@link io.github.mzmine.parameters.impl.SimpleParameterSet#loadValuesFromXML(Element)},
 * this audit records every top-level parameter that cannot be mapped and every load exception. Each
 * known parameter is loaded into a clone and reserialized independently, so one incompatible value
 * cannot hide later findings.</p>
 */
class PublicWorkflowParameterAuditTest {

  private static final String SETTINGS_ENVIRONMENT = "OPEN_OFFLINE_PUBLIC_MZMINE_SETTINGS";
  private static final Path EXPECTED_INVENTORY = Path.of("datasets", "expected",
      "zenodo_14000687_mzmine_settings_inventory.json");
  private static final Path OUTPUT = Path.of("build", "public_workflow_validation",
      "mzmine_settings_parameter_audit.json");
  private static final String PARAMETER_TAG = "parameter";

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .enable(SerializationFeature.INDENT_OUTPUT)
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  @Test
  void auditsPublishedParametersWithoutExecutingWorkflow() throws Exception {
    final String configuredPath = System.getenv(SETTINGS_ENVIRONMENT);
    Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
        () -> "Set " + SETTINGS_ENVIRONMENT + "=<path> to run the public parameter audit");

    final Path settingsPath = Path.of(configuredPath).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(settingsPath), () -> "Missing settings XML: " + settingsPath);

    final ExpectedWorkflow expected = readExpectedWorkflow(EXPECTED_INVENTORY);
    final Document sourceDocument = secureFactory().newDocumentBuilder().parse(settingsPath.toFile());
    final Element root = sourceDocument.getDocumentElement();
    assertEquals("batch", root.getTagName());

    final List<Element> stepElements = directChildren(root, "batchstep");
    assertEquals(expected.steps().size(), stepElements.size());

    final List<Map<String, Object>> stepReports = new ArrayList<>();
    final List<String> allUnknown = new ArrayList<>();
    final List<String> allLoadErrors = new ArrayList<>();

    for (int index = 0; index < stepElements.size(); index++) {
      final Element stepElement = stepElements.get(index);
      final ExpectedStep expectedStep = expected.steps().get(index);
      final String moduleClassName = stepElement.getAttribute("method");
      assertEquals(expectedStep.className(), moduleClassName,
          "Published module order or allowlisted class changed at step " + (index + 1));

      final Class<?> rawModuleClass = Class.forName(moduleClassName, true,
          Thread.currentThread().getContextClassLoader());
      assertTrue(MZmineModule.class.isAssignableFrom(rawModuleClass),
          () -> moduleClassName + " is not an MZmineModule");
      final MZmineModule module = (MZmineModule) rawModuleClass.getDeclaredConstructor().newInstance();
      final Class<? extends ParameterSet> parameterSetClass = module.getParameterSetClass();
      assertTrue(parameterSetClass != null,
          () -> moduleClassName + " unexpectedly has no ParameterSet class");
      final ParameterSet parameterSet = parameterSetClass.getDeclaredConstructor().newInstance();

      final int publishedVersion = Integer.parseInt(stepElement.getAttribute("parameter_version"));
      final int localVersion = parameterSet.getVersion();
      final Map<String, Parameter<?>> nameMap = parameterSet.getNameParameterMap();
      final Set<String> localCanonicalNames = new TreeSet<>();
      Arrays.stream(parameterSet.getParameters()).map(Parameter::getName)
          .forEach(localCanonicalNames::add);

      final List<Map<String, Object>> parameterReports = new ArrayList<>();
      final Set<String> matchedCanonicalNames = new LinkedHashSet<>();
      final List<String> unknownParameters = new ArrayList<>();
      final List<String> loadErrors = new ArrayList<>();

      for (final Element publishedParameter : directChildren(stepElement, PARAMETER_TAG)) {
        final String publishedName = publishedParameter.getAttribute("name");
        final Parameter<?> localParameter = nameMap.get(publishedName);
        final Map<String, Object> parameterReport = new LinkedHashMap<>();
        parameterReport.put("published_name", publishedName);
        parameterReport.put("source_element_paths", structuralPaths(publishedParameter));

        if (localParameter == null) {
          parameterReport.put("mapping", "unknown");
          parameterReport.put("local_parameter_class", null);
          parameterReport.put("load_status", "not-loaded");
          unknownParameters.add(publishedName);
          allUnknown.add((index + 1) + ":" + moduleClassName + ":" + publishedName);
        } else {
          final String canonicalName = localParameter.getName();
          matchedCanonicalNames.add(canonicalName);
          parameterReport.put("canonical_name", canonicalName);
          parameterReport.put("mapping",
              publishedName.equals(canonicalName) ? "direct" : "legacy-alias");
          parameterReport.put("local_parameter_class", localParameter.getClass().getName());

          try {
            final Parameter<?> isolated = localParameter.cloneParameter();
            isolated.loadValueFromXML(publishedParameter);
            final Document outputDocument = secureFactory().newDocumentBuilder().newDocument();
            final Element outputParameter = outputDocument.createElement(PARAMETER_TAG);
            outputParameter.setAttribute("name", canonicalName);
            outputDocument.appendChild(outputParameter);
            isolated.saveValueToXML(outputParameter);

            final Set<String> sourcePaths = structuralPaths(publishedParameter);
            final Set<String> roundTripPaths = structuralPaths(outputParameter);
            final Set<String> pathsMissingAfterRoundTrip = difference(sourcePaths, roundTripPaths);
            final Set<String> pathsAddedByRoundTrip = difference(roundTripPaths, sourcePaths);

            parameterReport.put("load_status", "loaded");
            parameterReport.put("roundtrip_element_paths", roundTripPaths);
            parameterReport.put("paths_missing_after_roundtrip", pathsMissingAfterRoundTrip);
            parameterReport.put("paths_added_by_roundtrip", pathsAddedByRoundTrip);
          } catch (Throwable error) {
            final String message = error.getClass().getName() + ": "
                + String.valueOf(error.getMessage());
            parameterReport.put("load_status", "error");
            parameterReport.put("load_error", message);
            loadErrors.add(publishedName + " -> " + message);
            allLoadErrors.add((index + 1) + ":" + moduleClassName + ":" + publishedName
                + " -> " + message);
          }
        }
        parameterReports.add(parameterReport);
      }

      final Set<String> localParametersAbsentFromPublishedXml = new TreeSet<>(localCanonicalNames);
      localParametersAbsentFromPublishedXml.removeAll(matchedCanonicalNames);

      final Map<String, Object> stepReport = new LinkedHashMap<>();
      stepReport.put("order", index + 1);
      stepReport.put("module_class", moduleClassName);
      stepReport.put("module_name", module.getName());
      stepReport.put("parameter_set_class", parameterSetClass.getName());
      stepReport.put("published_parameter_version", publishedVersion);
      stepReport.put("local_parameter_version", localVersion);
      stepReport.put("version_relation", Integer.compare(publishedVersion, localVersion) == 0
          ? "same" : publishedVersion < localVersion ? "published-older" : "published-newer");
      stepReport.put("published_top_level_parameter_count", parameterReports.size());
      stepReport.put("local_top_level_parameter_count", localCanonicalNames.size());
      stepReport.put("unknown_published_parameters", unknownParameters);
      stepReport.put("local_parameters_absent_from_published_xml",
          localParametersAbsentFromPublishedXml);
      stepReport.put("load_errors", loadErrors);
      stepReport.put("parameters", parameterReports);
      stepReports.add(stepReport);
    }

    final Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", 1);
    report.put("source_file", settingsPath.toString());
    report.put("source_sha256", expected.sourceSha256());
    report.put("published_mzmine_version", root.getAttribute("mzmine_version"));
    report.put("step_count", stepReports.size());
    report.put("unknown_published_parameter_count", allUnknown.size());
    report.put("unknown_published_parameters", allUnknown);
    report.put("parameter_load_error_count", allLoadErrors.size());
    report.put("parameter_load_errors", allLoadErrors);
    report.put("workflow_executed", false);
    report.put("steps", stepReports);

    Files.createDirectories(OUTPUT.getParent());
    MAPPER.writeValue(OUTPUT.toFile(), report);
    System.out.println("PUBLIC_WORKFLOW_PARAMETER_AUDIT=" + OUTPUT.toAbsolutePath());
    System.out.println(MAPPER.writeValueAsString(report));

    assertTrue(allLoadErrors.isEmpty(),
        () -> "Known published parameters failed to load: " + allLoadErrors);
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
          String.valueOf(step.get("class")),
          String.valueOf(step.get("parameter_version"))));
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
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory;
  }

  private static List<Element> directChildren(final Element parent, final String tagName) {
    final List<Element> children = new ArrayList<>();
    final NodeList nodes = parent.getChildNodes();
    for (int i = 0; i < nodes.getLength(); i++) {
      final Node node = nodes.item(i);
      if (node instanceof Element element && tagName.equals(element.getTagName())) {
        children.add(element);
      }
    }
    return children;
  }

  private static Set<String> structuralPaths(final Element root) {
    final Set<String> paths = new TreeSet<>();
    collectStructuralPaths(root, "", paths);
    return paths;
  }

  private static void collectStructuralPaths(final Element element, final String parentPath,
      final Set<String> paths) {
    final String name = element.hasAttribute("name") ? element.getAttribute("name") : "";
    final String method = element.hasAttribute("method") ? element.getAttribute("method") : "";
    final String discriminator = !name.isBlank() ? "[@name='" + name + "']"
        : !method.isBlank() ? "[@method='" + method + "']" : "";
    final String current = parentPath + "/" + element.getTagName() + discriminator;
    paths.add(current);
    final NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      final Node child = children.item(i);
      if (child instanceof Element childElement) {
        collectStructuralPaths(childElement, current, paths);
      }
    }
  }

  private static Set<String> difference(final Set<String> left, final Set<String> right) {
    final Set<String> result = new TreeSet<>(left);
    result.removeAll(right);
    return result;
  }

  private record ExpectedWorkflow(String sourceSha256, List<ExpectedStep> steps) {
  }

  private record ExpectedStep(int order, String className, String parameterVersion) {
  }
}
