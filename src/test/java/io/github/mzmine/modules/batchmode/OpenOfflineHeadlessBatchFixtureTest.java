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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverModule;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetectorParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyCSVExportModule;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyCSVExportParameters;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowCommonElement;
import io.github.mzmine.modules.io.export_features_csv_legacy.LegacyExportRowDataFileElement;
import io.github.mzmine.modules.io.export_features_gnps.fbmn.FeatureListRowsFilter;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelectionType;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Generates the portable artifacts used by the separate-process headless batch CI check. */
class OpenOfflineHeadlessBatchFixtureTest {

  static final Path OUTPUT_DIRECTORY = Path.of("build", "open_offline_headless").toAbsolutePath();
  static final Path MZML_FILE = OUTPUT_DIRECTORY.resolve("synthetic_batch.mzML");
  static final Path BATCH_FILE = OUTPUT_DIRECTORY.resolve("synthetic_batch.mzbatch");
  static final Path CSV_FILE = OUTPUT_DIRECTORY.resolve("synthetic_batch_features.csv");

  @Test
  void serializesPortableBatchFromRealParameterSets() throws Exception {
    Files.createDirectories(OUTPUT_DIRECTORY);
    Files.deleteIfExists(CSV_FILE);
    OpenOfflineSyntheticMzML.write(MZML_FILE);

    final BatchQueue queue = new BatchQueue();
    addStep(queue, new MSDKmzMLImportModule(), importParameters());
    addStep(queue, new MassDetectionModule(), massDetectionParameters());
    addStep(queue, new ModularADAPChromatogramBuilderModule(), chromatogramParameters());
    addStep(queue, new MinimumSearchFeatureResolverModule(), resolverParameters());
    addStep(queue, new LegacyCSVExportModule(), exportParameters());

    final Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .newDocument();
    final Element root = document.createElement("batch");
    document.appendChild(root);
    queue.saveToXml(root);

    final var transformer = TransformerFactory.newInstance().newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.transform(new DOMSource(document), new StreamResult(BATCH_FILE.toFile()));

    assertTrue(Files.isRegularFile(MZML_FILE));
    assertTrue(Files.size(MZML_FILE) > 1_000);
    assertTrue(Files.isRegularFile(BATCH_FILE));
    final String xml = Files.readString(BATCH_FILE);
    assertEquals(5, queue.size());
    assertTrue(xml.contains(MSDKmzMLImportModule.class.getName()));
    assertTrue(xml.contains(MassDetectionModule.class.getName()));
    assertTrue(xml.contains(ModularADAPChromatogramBuilderModule.class.getName()));
    assertTrue(xml.contains(MinimumSearchFeatureResolverModule.class.getName()));
    assertTrue(xml.contains(LegacyCSVExportModule.class.getName()));
    assertTrue(xml.contains(MZML_FILE.toFile().getAbsolutePath()));
    assertTrue(xml.contains(CSV_FILE.toFile().getAbsolutePath()));
    assertFalse(Files.exists(CSV_FILE), "Export must be produced only by the child CLI process");
  }

  private static ParameterSet importParameters() {
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.setParameter(MSDKmzMLImportParameters.fileNames,
        new File[]{MZML_FILE.toFile().getAbsoluteFile()});
    return parameters;
  }

  private static ParameterSet massDetectionParameters() {
    final CentroidMassDetectorParameters detectorParameters =
        new CentroidMassDetectorParameters();
    detectorParameters.setParameter(CentroidMassDetectorParameters.noiseLevel, 100.0);

    final MassDetectionParameters parameters = new MassDetectionParameters();
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES));
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.scanTypes, SelectedScanTypes.SCANS);
    parameters.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    parameters.setParameter(MassDetectionParameters.massDetector,
        new MZmineProcessingStepImpl<MassDetector>(new CentroidMassDetector(), detectorParameters));
    parameters.getParameter(MassDetectionParameters.outFilenameOption).setValue(false);
    return parameters;
  }

  private static ParameterSet chromatogramParameters() {
    final ADAPChromatogramBuilderParameters parameters =
        new ADAPChromatogramBuilderParameters();
    parameters.setParameter(ADAPChromatogramBuilderParameters.dataFiles,
        new RawDataFilesSelection(RawDataFilesSelectionType.BATCH_LAST_FILES));
    parameters.setParameter(ADAPChromatogramBuilderParameters.scanSelection,
        new ScanSelection(1));
    parameters.setParameter(ADAPChromatogramBuilderParameters.minimumConsecutiveScans, 3);
    parameters.setParameter(ADAPChromatogramBuilderParameters.minGroupIntensity, 500.0);
    parameters.setParameter(ADAPChromatogramBuilderParameters.minHighestPoint, 500.0);
    parameters.setParameter(ADAPChromatogramBuilderParameters.mzTolerance,
        new MZTolerance(0.005, 10.0));
    parameters.setParameter(ADAPChromatogramBuilderParameters.suffix, "batch-eics");
    return parameters;
  }

  private static ParameterSet resolverParameters() {
    final MinimumSearchFeatureResolverParameters parameters =
        new MinimumSearchFeatureResolverParameters();
    parameters.setParameter(GeneralResolverParameters.PEAK_LISTS,
        new FeatureListsSelection(FeatureListsSelectionType.BATCH_LAST_FEATURELISTS));
    parameters.setParameter(GeneralResolverParameters.SUFFIX, "batch-resolved");
    parameters.setParameter(GeneralResolverParameters.handleOriginal,
        OriginalFeatureListOption.REMOVE);
    parameters.getParameter(GeneralResolverParameters.groupMS2Parameters).setValue(false);
    parameters.setParameter(
        MinimumSearchFeatureResolverParameters.CHROMATOGRAPHIC_THRESHOLD_LEVEL, 0.0);
    parameters.setParameter(MinimumSearchFeatureResolverParameters.SEARCH_RT_RANGE, 0.2);
    parameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_RELATIVE_HEIGHT, 0.0);
    parameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_ABSOLUTE_HEIGHT, 500.0);
    parameters.setParameter(MinimumSearchFeatureResolverParameters.MIN_RATIO, 1.2);
    parameters.setParameter(MinimumSearchFeatureResolverParameters.PEAK_DURATION,
        Range.closed(0.2, 1.5));
    parameters.setParameter(GeneralResolverParameters.MIN_NUMBER_OF_DATAPOINTS, 3);
    return parameters;
  }

  private static ParameterSet exportParameters() {
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

  private static void addStep(final BatchQueue queue, final MZmineProcessingModule module,
      final ParameterSet parameters) {
    queue.add(new MZmineProcessingStepImpl<MZmineProcessingModule>(module, parameters));
  }
}
