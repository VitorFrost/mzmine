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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.main.MZmineCore;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionModule;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.MassDetectionParameters;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetectorParameters;
import io.github.mzmine.modules.impl.MZmineProcessingStepImpl;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportModule;
import io.github.mzmine.modules.io.import_rawdata_all.AllSpectralDataImportParameters;
import io.github.mzmine.modules.io.import_spectral_library.SpectralLibraryImportParameters;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.parameters.parametertypes.selectors.RawDataFilesSelection;
import io.github.mzmine.parameters.parametertypes.selectors.ScanSelection;
import io.github.mzmine.util.parity.DifferentialStageReportWriter;
import io.github.mzmine.util.parity.DifferentialStageReportWriter.InputMetadata;
import io.github.mzmine.util.parity.DifferentialStageReportWriter.ProducerMetadata;
import io.github.mzmine.util.parity.DifferentialStageReportWriter.SettingsMetadata;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Opt-in real-data acceptance test for the normalized import and selected survey-scan centroid
 * mass-detection report.
 *
 * <p>The selected survey level is explicit and limited to one level per execution. This supports
 * vendor-converted single-quadrupole full scans represented as either MS1 or MS2 without silently
 * mixing true fragment spectra into the same evidence report.</p>
 *
 * <p>The ordinary test suite skips this test unless all required environment variables are present.
 * This preserves the repository's no-large-data policy while providing one reproducible executable
 * entry point for the governed public mzML fixture.</p>
 */
class MZmineDifferentialReportAcceptanceTest {

  private static final String DEFAULT_DATASET_ID =
      "zenodo-14001110-banane-30ngml-001";
  private static final String DEFAULT_RELATIVE_PATH =
      "zenodo/14001110/Banane_30ngmL_001.mzML";
  private static final double CENTROID_NOISE_LEVEL = 0d;

  @AfterEach
  void cleanProject() {
    MZmineTestUtil.cleanProject();
  }

  @Test
  @Timeout(value = 15, unit = MINUTES)
  void generateConfiguredReport() throws Exception {
    Map<String, String> environment = System.getenv();
    String inputValue = environment.get("MZMINE_PARITY_INPUT");
    String outputValue = environment.get("MZMINE_PARITY_OUTPUT");
    String ref = environment.get("MZMINE_PARITY_REF");
    String commit = environment.get("MZMINE_PARITY_COMMIT");

    Assumptions.assumeTrue(inputValue != null && !inputValue.isBlank(),
        "MZMINE_PARITY_INPUT is not configured");
    Assumptions.assumeTrue(outputValue != null && !outputValue.isBlank(),
        "MZMINE_PARITY_OUTPUT is not configured");
    Assumptions.assumeTrue(ref != null && !ref.isBlank(),
        "MZMINE_PARITY_REF is not configured");
    Assumptions.assumeTrue(commit != null && !commit.isBlank(),
        "MZMINE_PARITY_COMMIT is not configured");

    int surveyMsLevel = parseSurveyMsLevel(environment.get("MZMINE_PARITY_SURVEY_MS_LEVEL"));
    Path input = Path.of(inputValue).toAbsolutePath().normalize();
    Path output = Path.of(outputValue).toAbsolutePath().normalize();
    assertTrue(Files.isRegularFile(input), "Configured parity input must be a regular file");
    assertTrue(Files.isReadable(input), "Configured parity input must be readable");
    assertFalse(input.equals(output), "Input and output paths must be different");

    String expectedSha = environment.get("MZMINE_PARITY_EXPECTED_SHA256");
    String actualSha = sha256(input);
    if (expectedSha != null && !expectedSha.isBlank()) {
      assertEquals(expectedSha, actualSha, "Governed input SHA-256 changed");
    }

    MZmineTestUtil.cleanProject();
    importMzml(input);

    List<RawDataFile> rawFiles = MZmineCore.getProjectManager().getCurrentProject()
        .getCurrentRawDataFiles();
    assertEquals(1, rawFiles.size(), "Exactly one raw data file must be imported");
    RawDataFile rawDataFile = rawFiles.get(0);
    assertTrue(rawDataFile.getNumOfScans() > 0, "Imported raw data file has no scans");

    runCentroidMassDetection(rawDataFile, surveyMsLevel);
    long selectedScans = rawDataFile.getScans().stream()
        .filter(scan -> scan.getMSLevel() == surveyMsLevel).count();
    long selectedMassLists = rawDataFile.getScans().stream()
        .filter(scan -> scan.getMSLevel() == surveyMsLevel && scan.getMassList() != null).count();
    assertTrue(selectedScans > 0,
        "No scans exist for the configured survey MS level " + surveyMsLevel);
    assertEquals(selectedScans, selectedMassLists,
        "Every selected survey scan must receive a mass list");

    int threadCount = parsePositiveInt(environment.get("MZMINE_PARITY_THREADS"), 1);
    ProducerMetadata producer = new ProducerMetadata(
        environment.getOrDefault("MZMINE_PARITY_REPOSITORY", "VitorFrost/mzmine"),
        ref,
        commit,
        environment.getOrDefault("MZMINE_PARITY_APPLICATION_VERSION", "3.9.1"),
        System.getProperty("java.version"),
        System.getProperty("os.name"),
        threadCount,
        List.of("gradle-test", getClass().getName() + ".generateConfiguredReport"));
    InputMetadata inputMetadata = new InputMetadata(
        environment.getOrDefault("MZMINE_PARITY_DATASET_ID", DEFAULT_DATASET_ID),
        environment.getOrDefault("MZMINE_PARITY_RELATIVE_PATH", DEFAULT_RELATIVE_PATH),
        Files.size(input),
        actualSha);
    SettingsMetadata settingsMetadata = new SettingsMetadata(
        settingsMappingId(surveyMsLevel), sha256(settingsCanonicalJson(surveyMsLevel)));

    DifferentialStageReportWriter.write(
        output, producer, inputMetadata, settingsMetadata, rawDataFile);
    assertTrue(Files.isRegularFile(output), "Differential report was not written");
    String report = Files.readString(output, StandardCharsets.UTF_8);
    assertTrue(report.contains("\"schema_version\":1"));
    assertTrue(report.contains("\"mass_detection\""));
    assertTrue(report.contains(actualSha));
    assertTrue(report.contains(settingsMappingId(surveyMsLevel)));
  }

  private static void importMzml(Path input) throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(AllSpectralDataImportModule.class).cloneParameterSet();
    parameters.setParameter(AllSpectralDataImportParameters.fileNames,
        new File[]{input.toFile()});
    parameters.setParameter(AllSpectralDataImportParameters.advancedImport, false);
    parameters.setParameter(SpectralLibraryImportParameters.dataBaseFiles, new File[0]);

    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(
            10, MINUTES, AllSpectralDataImportModule.class, parameters),
        "mzML import did not finish successfully");
  }

  private static void runCentroidMassDetection(RawDataFile rawDataFile, int surveyMsLevel)
      throws InterruptedException {
    ParameterSet parameters = MZmineCore.getConfiguration()
        .getModuleParameters(MassDetectionModule.class).cloneParameterSet();
    ParameterSet centroidParameters = new CentroidMassDetectorParameters();
    centroidParameters.setParameter(CentroidMassDetectorParameters.noiseLevel,
        CENTROID_NOISE_LEVEL);
    centroidParameters.setParameter(CentroidMassDetectorParameters.detectIsotopes, false);
    parameters.setParameter(MassDetectionParameters.massDetector,
        new MZmineProcessingStepImpl<>(new CentroidMassDetector(), centroidParameters));
    parameters.setParameter(MassDetectionParameters.dataFiles,
        new RawDataFilesSelection(new RawDataFile[]{rawDataFile}));
    parameters.setParameter(MassDetectionParameters.scanSelection,
        new ScanSelection(surveyMsLevel));
    parameters.setParameter(MassDetectionParameters.denormalizeMSnScans, false);
    parameters.setParameter(MassDetectionParameters.outFilenameOption, false);

    assertInstanceOf(CentroidMassDetector.class,
        parameters.getValue(MassDetectionParameters.massDetector).getModule(),
        "The differential mapping requires the centroid detector");
    assertEquals(TaskResult.FINISHED,
        MZmineTestUtil.callModuleWithTimeout(
            10, MINUTES, MassDetectionModule.class, parameters),
        "MS" + surveyMsLevel + " survey centroid mass detection did not finish successfully");
  }

  static int parseSurveyMsLevel(String value) {
    int parsed = parsePositiveInt(value, 1);
    if (parsed != 1 && parsed != 2) {
      throw new IllegalArgumentException(
          "MZMINE_PARITY_SURVEY_MS_LEVEL must be exactly 1 or 2");
    }
    return parsed;
  }

  static String settingsMappingId(int surveyMsLevel) {
    return "mzml-import-centroid-survey-ms" + surveyMsLevel + "-v1";
  }

  static String settingsCanonicalJson(int surveyMsLevel) {
    return "{\"advanced_import\":false,\"denormalize_fragment_scans\":false,"
        + "\"detect_isotopes_below_noise\":false,"
        + "\"mass_detector\":\"Centroid mass detector\",\"noise_level\":0.0,"
        + "\"netcdf_output\":false,\"spectral_library_import\":false,"
        + "\"survey_ms_level\":" + surveyMsLevel + "}";
  }

  private static int parsePositiveInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    int parsed = Integer.parseInt(value);
    if (parsed < 1) {
      throw new IllegalArgumentException("MZMINE_PARITY_THREADS must be positive");
    }
    return parsed;
  }

  private static String sha256(Path path)
      throws IOException, NoSuchAlgorithmException {
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

  private static String sha256(String value) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}