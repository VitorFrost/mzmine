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
package io.github.mzmine.modules.dataprocessing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.featuredata.FeatureDataUtils;
import io.github.mzmine.datamodel.features.Feature;
import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.ModularFeatureList;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.MZmineProcessingStep;
import io.github.mzmine.modules.dataprocessing.align_join.JoinAlignerParameters;
import io.github.mzmine.modules.dataprocessing.align_join.JoinAlignerTask;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ADAPChromatogramBuilderParameters;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderModule;
import io.github.mzmine.modules.dataprocessing.featdet_adapchromatogrambuilder.ModularADAPChromatogramBuilderTask;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.FeatureResolverTask;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.GeneralResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_chromatogramdeconvolution.minimumsearch.MinimumSearchFeatureResolverParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionTask;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.SelectedScanTypes;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetectorParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportModule;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportParameters;
import io.github.mzmine.modules.io.import_rawdata_mzml.MSDKmzMLImportTask;
import io.github.mzmine.parameters.parametertypes.OriginalFeatureListHandlingParameter.OriginalFeatureListOption;
import io.github.mzmine.parameters.parametertypes.selectors.FeatureListsSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance;
import io.github.mzmine.parameters.parametertypes.tolerances.RTTolerance.Unit;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * End-to-end deterministic test for the scientific open-offline pipeline.
 *
 * <p>Two human-readable synthetic profiles are converted to mzML, imported, processed by centroid
 * mass detection, connected into chromatograms, resolved by local minimum, and aligned by the
 * MZmine 3.9 join aligner. No login, network, vendor reader, R runtime, or proprietary component is
 * involved.</p>
 */
class OpenOfflineSyntheticChromatogramPipelineTest {

  private static final double DOUBLE_TOLERANCE = 0.0001;
  private static final float FLOAT_TOLERANCE = 0.0001f;
  private static final Instant MODULE_DATE = Instant.parse("2026-08-03T00:00:00Z");
  private static final String PROFILE_RESOURCE = "/open_offline/synthetic_lcms_profile.csv";
  private static final double[] MZ_VALUES = {75.0, 150.0, 300.0, 500.0};

  @Test
  void processesAndAlignsTwoDeterministicSamples() throws IOException {
    Locale.setDefault(Locale.US);
    final Path mzmlA = Files.createTempFile("mzmine-open-offline-a-", ".mzML");
    final Path mzmlB = Files.createTempFile("mzmine-open-offline-b-", ".mzML");

    final List<ProfileScan> profileA = loadProfile();
    final List<ProfileScan> profileB = shiftAndScaleProfile(profileA, 0.03, 1.10);
    writeMzML(mzmlA, profileA);
    writeMzML(mzmlB, profileB);

    final MZmineProject project = new MZmineProjectImpl();
    MZmineCore.getProjectManager().setCurrentProject(project);

    try {
      final ResolvedSample sampleA = processSample(project, mzmlA.toFile(), 1.0,
          0.6f, 9000.0f, 1.0f, 7000.0f);
      final ResolvedSample sampleB = processSample(project, mzmlB.toFile(), 1.10,
          0.63f, 9900.0f, 1.03f, 7700.0f);

      final FeatureList aligned = runJoinAlignment(project, sampleA.resolved(), sampleB.resolved());
      assertAlignedResult(aligned, sampleA, sampleB);
    } finally {
      MZmineCore.getProjectManager().setCurrentProject(new MZmineProjectImpl());
      deleteGeneratedMzML(mzmlA);
      deleteGeneratedMzML(mzmlB);
    }
  }

  private static ResolvedSample processSample(final MZmineProject project, final File fixture,
      final double intensityScale, final float firstRt, final float firstHeight,
      final float secondRt, final float secondHeight) {
    final RawDataFile raw = importMzML(project, fixture);
    assertEquals(15, raw.getNumOfScans());
    assertEquals(15, raw.getNumOfScans(1));

    runCentroidMassDetection(raw);
    assertMassDetectionResult(raw, intensityScale);

    final FeatureList chromatograms = runChromatogramBuilder(project, raw);
    assertFeatureProperties(chromatograms, firstRt, firstHeight, secondRt, secondHeight);

    final FeatureList resolved = runFeatureResolver(project, chromatograms);
    assertFeatureProperties(resolved, firstRt, firstHeight, secondRt, secondHeight);
    return new ResolvedSample(raw, resolved);
  }

  private static RawDataFile importMzML(final MZmineProject project, final File fixture) {
    final int before = project.getNumberOfDataFiles();
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.getParameter(MSDKmzMLImportParameters.fileNames).setValue(new File[]{fixture});

    final MSDKmzMLImportTask task = new MSDKmzMLImportTask(project, fixture,
        MSDKmzMLImportModule.class, parameters, MODULE_DATE, null);
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(before + 1, project.getNumberOfDataFiles());
    return project.getDataFiles()[before];
  }

  private static void runCentroidMassDetection(final RawDataFile raw) {
    final CentroidMassDetectorParameters detectorParameters =
        new CentroidMassDetectorParameters();
    detectorParameters.setParameter(CentroidMassDetectorParameters.noiseLevel, 100.0);

    final MZmineProcessingStep<MassDetector> detector = new MZmineProcessingStepImpl<>(
        new CentroidMassDetector(), detectorParameters);

    final MassDetectionParameters parameters = new MassDetectionParameters();
    parameters.setParameter(MassDetectionParameters.massDetector, detector);
    parameters.setParameter(MassDetectionParameters.scanSelection, new ScanSelection(1));
    parameters.setParameter(MassDetectionParameters.scanTypes, SelectedScanTypes.SCANS);
    parameters.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    parameters.getParameter(MassDetectionParameters.outFilenameOption).setValue(false);

    final MassDetectionTask task = new MassDetectionTask(raw, parameters, null, MODULE_DATE);
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(1.0, task.getFinishedPercentage(), DOUBLE_TOLERANCE);
  }

  private static void assertMassDetectionResult(final RawDataFile raw,
      final double intensityScale) {
    for (final Scan scan : raw.getScans()) {
      assertNotNull(scan.getMassList(), "Mass list missing for scan " + scan.getScanNumber());
      final double[] masses = scan.getMassList()
          .getMzValues(new double[scan.getMassList().getNumberOfDataPoints()]);
      assertFalse(contains(masses, 75.0), "Noise ion m/z 75 survived mass detection");
      assertTrue(contains(masses, 500.0), "Control ion m/z 500 was removed");
    }

    final Scan first = raw.getScan(0);
    assertArrayEquals(new double[]{500.0},
        first.getMassList().getMzValues(new double[first.getMassList().getNumberOfDataPoints()]),
        DOUBLE_TOLERANCE);
    assertArrayEquals(new double[]{150.0}, first.getMassList()
        .getIntensityValues(new double[first.getMassList().getNumberOfDataPoints()]),
        DOUBLE_TOLERANCE);

    final Scan scanAtPointNine = raw.getScan(8);
    assertArrayEquals(new double[]{150.0, 300.0, 500.0}, scanAtPointNine.getMassList()
        .getMzValues(new double[scanAtPointNine.getMassList().getNumberOfDataPoints()]),
        DOUBLE_TOLERANCE);
    assertArrayEquals(new double[]{500.0 * intensityScale, 4000.0 * intensityScale, 150.0},
        scanAtPointNine.getMassList()
            .getIntensityValues(new double[scanAtPointNine.getMassList().getNumberOfDataPoints()]),
        DOUBLE_TOLERANCE);
  }

  private static FeatureList runChromatogramBuilder(final MZmineProject project,
      final RawDataFile raw) {
    final int before = project.getNumberOfFeatureLists();
    final ADAPChromatogramBuilderParameters parameters =
        new ADAPChromatogramBuilderParameters();
    parameters.setParameter(ADAPChromatogramBuilderParameters.scanSelection,
        new ScanSelection(1));
    parameters.setParameter(ADAPChromatogramBuilderParameters.minimumConsecutiveScans, 3);
    parameters.setParameter(ADAPChromatogramBuilderParameters.minGroupIntensity, 500.0);
    parameters.setParameter(ADAPChromatogramBuilderParameters.minHighestPoint, 500.0);
    parameters.setParameter(ADAPChromatogramBuilderParameters.mzTolerance,
        new MZTolerance(0.005, 10.0));
    parameters.setParameter(ADAPChromatogramBuilderParameters.suffix, "synthetic-eics");

    final ModularADAPChromatogramBuilderTask task =
        ModularADAPChromatogramBuilderTask.forChromatography(project, raw, parameters, null,
            MODULE_DATE, ModularADAPChromatogramBuilderModule.class);
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(1.0, task.getFinishedPercentage(), DOUBLE_TOLERANCE);
    assertEquals(before + 1, project.getNumberOfFeatureLists());
    return project.getCurrentFeatureLists().get(before);
  }

  private static FeatureList runFeatureResolver(final MZmineProject project,
      final FeatureList chromatograms) {
    final int before = project.getNumberOfFeatureLists();
    final MinimumSearchFeatureResolverParameters parameters =
        new MinimumSearchFeatureResolverParameters();
    parameters.setParameter(GeneralResolverParameters.SUFFIX, "resolved");
    parameters.setParameter(GeneralResolverParameters.handleOriginal,
        OriginalFeatureListOption.KEEP);
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

    final FeatureResolverTask task = new FeatureResolverTask(project, null, chromatograms,
        parameters, FeatureDataUtils.DEFAULT_CENTER_FUNCTION, MODULE_DATE);
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(1.0, task.getFinishedPercentage(), DOUBLE_TOLERANCE);
    assertEquals(before + 1, project.getNumberOfFeatureLists());
    return project.getCurrentFeatureLists().get(before);
  }

  private static FeatureList runJoinAlignment(final MZmineProject project,
      final FeatureList first, final FeatureList second) {
    final int before = project.getNumberOfFeatureLists();
    final JoinAlignerParameters parameters = new JoinAlignerParameters();
    parameters.setParameter(JoinAlignerParameters.peakLists,
        new FeatureListsSelection((ModularFeatureList) first, (ModularFeatureList) second));
    parameters.setParameter(JoinAlignerParameters.peakListName, "synthetic aligned");
    parameters.setParameter(JoinAlignerParameters.MZTolerance, new MZTolerance(0.005, 10.0));
    parameters.setParameter(JoinAlignerParameters.MZWeight, 3.0);
    parameters.setParameter(JoinAlignerParameters.RTTolerance,
        new RTTolerance(0.08f, Unit.MINUTES));
    parameters.setParameter(JoinAlignerParameters.RTWeight, 1.0);
    parameters.getParameter(JoinAlignerParameters.mobilityTolerance).setValue(false);
    parameters.setParameter(JoinAlignerParameters.mobilityWeight, 0.0);
    parameters.setParameter(JoinAlignerParameters.SameChargeRequired, false);
    parameters.setParameter(JoinAlignerParameters.SameIDRequired, false);
    parameters.getParameter(JoinAlignerParameters.compareIsotopePattern).setValue(false);
    parameters.getParameter(JoinAlignerParameters.compareSpectraSimilarity).setValue(false);
    parameters.setParameter(JoinAlignerParameters.handleOriginal, OriginalFeatureListOption.KEEP);

    final JoinAlignerTask task = new JoinAlignerTask(project, parameters, null, MODULE_DATE);
    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);
    assertEquals(1.0, task.getFinishedPercentage(), DOUBLE_TOLERANCE);
    assertEquals(before + 1, project.getNumberOfFeatureLists());
    return project.getCurrentFeatureLists().get(before);
  }

  private static void assertFeatureProperties(final FeatureList featureList,
      final float firstRt, final float firstHeight, final float secondRt,
      final float secondHeight) {
    assertEquals(2, featureList.getNumberOfRows());

    final List<FeatureListRow> rows = sortedRows(featureList);
    assertSingleFeatureRow(rows.get(0), 150.0, firstRt, firstHeight);
    assertSingleFeatureRow(rows.get(1), 300.0, secondRt, secondHeight);
  }

  private static void assertSingleFeatureRow(final FeatureListRow row, final double mz,
      final float rt, final float height) {
    assertEquals(1, row.getNumberOfFeatures());
    assertEquals(mz, row.getAverageMZ(), DOUBLE_TOLERANCE);
    assertEquals(rt, row.getAverageRT(), FLOAT_TOLERANCE);
    assertEquals(height, row.getAverageHeight(), FLOAT_TOLERANCE);
    assertNotNull(row.getAverageArea());
    assertTrue(row.getAverageArea() > 0.0f);
  }

  private static void assertAlignedResult(final FeatureList aligned,
      final ResolvedSample sampleA, final ResolvedSample sampleB) {
    assertEquals(2, aligned.getNumberOfRows());
    assertEquals(2, aligned.getNumberOfRawDataFiles());

    final List<FeatureListRow> rows = sortedRows(aligned);
    assertAlignedRow(rows.get(0), 150.0, sampleA.raw(), 0.6f, 9000.0f,
        sampleB.raw(), 0.63f, 9900.0f);
    assertAlignedRow(rows.get(1), 300.0, sampleA.raw(), 1.0f, 7000.0f,
        sampleB.raw(), 1.03f, 7700.0f);
  }

  private static void assertAlignedRow(final FeatureListRow row, final double mz,
      final RawDataFile rawA, final float rtA, final float heightA,
      final RawDataFile rawB, final float rtB, final float heightB) {
    assertEquals(2, row.getNumberOfFeatures());
    assertEquals(mz, row.getAverageMZ(), DOUBLE_TOLERANCE);

    final Feature featureA = row.getFeature(rawA);
    final Feature featureB = row.getFeature(rawB);
    assertNotNull(featureA);
    assertNotNull(featureB);
    assertEquals(rtA, featureA.getRT(), FLOAT_TOLERANCE);
    assertEquals(heightA, featureA.getHeight(), FLOAT_TOLERANCE);
    assertEquals(rtB, featureB.getRT(), FLOAT_TOLERANCE);
    assertEquals(heightB, featureB.getHeight(), FLOAT_TOLERANCE);
  }

  private static List<FeatureListRow> sortedRows(final FeatureList featureList) {
    final List<FeatureListRow> rows = new ArrayList<>(featureList.getRows());
    rows.sort(Comparator.comparingDouble(FeatureListRow::getAverageMZ));
    return rows;
  }

  private static boolean contains(final double[] values, final double target) {
    for (final double value : values) {
      if (Math.abs(value - target) <= DOUBLE_TOLERANCE) {
        return true;
      }
    }
    return false;
  }

  private static List<ProfileScan> loadProfile() throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(
            OpenOfflineSyntheticChromatogramPipelineTest.class.getResourceAsStream(
                PROFILE_RESOURCE), "Missing profile fixture " + PROFILE_RESOURCE), UTF_8))) {
      final List<ProfileScan> rows = reader.lines().skip(1).filter(line -> !line.isBlank())
          .map(ProfileScan::parse).toList();
      assertEquals(15, rows.size());
      return rows;
    }
  }

  private static List<ProfileScan> shiftAndScaleProfile(final List<ProfileScan> source,
      final double rtShift, final double signalScale) {
    return source.stream().map(row -> new ProfileScan(row.scan(), row.rtMinutes() + rtShift,
        row.mz75Intensity(), row.mz150Intensity() * signalScale,
        row.mz300Intensity() * signalScale, row.mz500Intensity())).toList();
  }

  private static void deleteGeneratedMzML(final Path mzml) {
    try {
      Files.deleteIfExists(mzml);
    } catch (IOException lockedMappedFileOnWindows) {
      mzml.toFile().deleteOnExit();
    }
  }

  private static void writeMzML(final Path output, final List<ProfileScan> profile)
      throws IOException {
    final StringBuilder xml = new StringBuilder(48_000);
    xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        .append("<mzML xmlns=\"http://psi.hupo.org/ms/mzml\" version=\"1.1.0\" ")
        .append("id=\"synthetic_open_offline_lcms\">\n")
        .append("  <cvList count=\"2\">\n")
        .append("    <cv id=\"MS\" fullName=\"Proteomics Standards Initiative Mass Spectrometry Ontology\" version=\"4.1.30\" URI=\"https://raw.githubusercontent.com/HUPO-PSI/psi-ms-CV/master/psi-ms.obo\"/>\n")
        .append("    <cv id=\"UO\" fullName=\"Unit Ontology\" version=\"09:04:2014\" URI=\"https://raw.githubusercontent.com/bio-ontology-research-group/unit-ontology/master/unit.obo\"/>\n")
        .append("  </cvList>\n")
        .append("  <fileDescription><fileContent>\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n")
        .append("  </fileContent></fileDescription>\n")
        .append("  <softwareList count=\"1\"><software id=\"open_offline_fixture_generator\" version=\"1.0\">\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000799\" name=\"custom unreleased software tool\" value=\"Open Offline Fixture Generator\"/>\n")
        .append("  </software></softwareList>\n")
        .append("  <instrumentConfigurationList count=\"1\"><instrumentConfiguration id=\"IC1\">\n")
        .append("    <cvParam cvRef=\"MS\" accession=\"MS:1000031\" name=\"instrument model\" value=\"synthetic LC-MS\"/>\n")
        .append("  </instrumentConfiguration></instrumentConfigurationList>\n")
        .append("  <dataProcessingList count=\"1\"><dataProcessing id=\"dp1\">\n")
        .append("    <processingMethod order=\"0\" softwareRef=\"open_offline_fixture_generator\">\n")
        .append("      <cvParam cvRef=\"MS\" accession=\"MS:1000544\" name=\"Conversion to mzML\" value=\"synthetic deterministic fixture\"/>\n")
        .append("    </processingMethod>\n")
        .append("  </dataProcessing></dataProcessingList>\n")
        .append("  <run id=\"synthetic_open_offline_lcms\" defaultInstrumentConfigurationRef=\"IC1\">\n")
        .append("    <spectrumList count=\"").append(profile.size())
        .append("\" defaultDataProcessingRef=\"dp1\">\n");

    for (int index = 0; index < profile.size(); index++) {
      appendSpectrum(xml, profile.get(index), index);
    }

    xml.append("    </spectrumList>\n")
        .append("  </run>\n")
        .append("</mzML>\n");
    Files.writeString(output, xml.toString(), UTF_8);
  }

  private static void appendSpectrum(final StringBuilder xml, final ProfileScan row,
      final int index) {
    final double[] intensities = row.intensities();
    int basePeakIndex = 0;
    double totalIonCurrent = 0.0;
    for (int i = 0; i < intensities.length; i++) {
      totalIonCurrent += intensities[i];
      if (intensities[i] > intensities[basePeakIndex]) {
        basePeakIndex = i;
      }
    }

    final String encodedMzs = encodeDoubles(MZ_VALUES);
    final String encodedIntensities = encodeDoubles(intensities);

    xml.append("      <spectrum index=\"").append(index).append("\" id=\"scan=")
        .append(row.scan()).append("\" defaultArrayLength=\"4\">\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000511\" name=\"ms level\" value=\"1\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000130\" name=\"positive scan\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000579\" name=\"MS1 spectrum\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000127\" name=\"centroid spectrum\" value=\"\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000504\" name=\"base peak m/z\" value=\"")
        .append(MZ_VALUES[basePeakIndex]).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000505\" name=\"base peak intensity\" value=\"")
        .append(intensities[basePeakIndex]).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000285\" name=\"total ion current\" value=\"")
        .append(totalIonCurrent).append("\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000528\" name=\"lowest observed m/z\" value=\"75.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("        <cvParam cvRef=\"MS\" accession=\"MS:1000527\" name=\"highest observed m/z\" value=\"500.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("        <scanList count=\"1\"><cvParam cvRef=\"MS\" accession=\"MS:1000795\" name=\"no combination\" value=\"\"/>\n")
        .append("          <scan instrumentConfigurationRef=\"IC1\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000016\" name=\"scan start time\" value=\"")
        .append(row.rtMinutes()).append("\" unitCvRef=\"UO\" unitAccession=\"UO:0000031\" unitName=\"minute\"/>\n")
        .append("            <scanWindowList count=\"1\"><scanWindow>\n")
        .append("              <cvParam cvRef=\"MS\" accession=\"MS:1000501\" name=\"scan window lower limit\" value=\"50.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("              <cvParam cvRef=\"MS\" accession=\"MS:1000500\" name=\"scan window upper limit\" value=\"550.0\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("            </scanWindow></scanWindowList>\n")
        .append("          </scan></scanList>\n")
        .append("        <binaryDataArrayList count=\"2\">\n")
        .append("          <binaryDataArray encodedLength=\"").append(encodedMzs.length())
        .append("\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000514\" name=\"m/z array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000040\" unitName=\"m/z\"/>\n")
        .append("            <binary>").append(encodedMzs).append("</binary>\n")
        .append("          </binaryDataArray>\n")
        .append("          <binaryDataArray encodedLength=\"")
        .append(encodedIntensities.length()).append("\">\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000523\" name=\"64-bit float\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000576\" name=\"no compression\" value=\"\"/>\n")
        .append("            <cvParam cvRef=\"MS\" accession=\"MS:1000515\" name=\"intensity array\" value=\"\" unitCvRef=\"MS\" unitAccession=\"MS:1000131\" unitName=\"number of detector counts\"/>\n")
        .append("            <binary>").append(encodedIntensities).append("</binary>\n")
        .append("          </binaryDataArray>\n")
        .append("        </binaryDataArrayList>\n")
        .append("      </spectrum>\n");
  }

  private static String encodeDoubles(final double[] values) {
    final ByteBuffer buffer = ByteBuffer.allocate(values.length * Double.BYTES)
        .order(ByteOrder.LITTLE_ENDIAN);
    for (final double value : values) {
      buffer.putDouble(value);
    }
    return Base64.getEncoder().encodeToString(buffer.array());
  }

  private record ProfileScan(int scan, double rtMinutes, double mz75Intensity,
                             double mz150Intensity, double mz300Intensity,
                             double mz500Intensity) {

    static ProfileScan parse(final String csvLine) {
      final String[] values = csvLine.split(",", -1);
      if (values.length != 6) {
        throw new IllegalArgumentException("Expected six columns: " + csvLine);
      }
      return new ProfileScan(Integer.parseInt(values[0]), Double.parseDouble(values[1]),
          Double.parseDouble(values[2]), Double.parseDouble(values[3]),
          Double.parseDouble(values[4]), Double.parseDouble(values[5]));
    }

    double[] intensities() {
      return new double[]{mz75Intensity, mz150Intensity, mz300Intensity, mz500Intensity};
    }
  }

  private record ResolvedSample(RawDataFile raw, FeatureList resolved) {
  }
}
