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
package io.github.mzmine.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;
import io.github.msdk.MSDKException;
import io.github.msdk.datamodel.ActivationInfo;
import io.github.msdk.datamodel.IsolationInfo;
import io.github.msdk.datamodel.MsScan;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.MzMLFileImportMethod;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.MzMLRawDataFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Emits the open-offline candidate report for direct comparison with the frozen public v4.0.8
 * parser/centroid oracle.
 *
 * <p>The producer calls the fork's own public mzML parser and centroid primitive-array overload. It
 * does not construct a project, open a GUI, infer an instrument vendor, or infer which MS level is
 * the source-data stream. MS1 or MS2 is selected explicitly by the caller after all scans have been
 * imported.</p>
 */
public final class OpenOfflineDifferentialReportMain {

  private static final int SCHEMA_VERSION = 1;
  private static final ObjectMapper JSON = new ObjectMapper();

  private OpenOfflineDifferentialReportMain() {
  }

  public static void main(String[] args) throws Exception {
    Arguments options = Arguments.parse(args);
    validateInput(options);

    MzMLFileImportMethod importer = new MzMLFileImportMethod(
        options.input().toFile(), scan -> true, chromatogram -> false);
    MzMLRawDataFile raw = parse(importer);
    List<MsScan> scans = List.copyOf(raw.getScans());
    if (scans.isEmpty()) {
      throw new IllegalStateException("The open-offline mzML parser returned no scans");
    }

    Map<String, Object> report = report(options, scans);
    Path output = options.output().toAbsolutePath().normalize();
    Path parent = output.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(output, JSON.writeValueAsString(report) + System.lineSeparator(),
        StandardCharsets.UTF_8);
  }

  private static MzMLRawDataFile parse(MzMLFileImportMethod importer) throws MSDKException {
    MzMLRawDataFile raw = importer.execute();
    if (raw == null) {
      throw new IllegalStateException("The open-offline mzML parser returned null");
    }
    return raw;
  }

  private static void validateInput(Arguments options) throws IOException, NoSuchAlgorithmException {
    if (!Files.isRegularFile(options.input()) || !Files.isReadable(options.input())) {
      throw new IllegalArgumentException("Input must be a readable regular file: " + options.input());
    }
    if (options.input().toAbsolutePath().normalize()
        .equals(options.output().toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("Input and output paths must differ");
    }
    long size = Files.size(options.input());
    if (size != options.expectedSize()) {
      throw new IllegalArgumentException(
          "Input byte-size mismatch: expected " + options.expectedSize() + ", got " + size);
    }
    String actualSha = sha256(options.input());
    if (!actualSha.equals(options.expectedSha256())) {
      throw new IllegalArgumentException(
          "Input SHA-256 mismatch: expected " + options.expectedSha256() + ", got " + actualSha);
    }
  }

  private static Map<String, Object> report(Arguments options, List<MsScan> scans)
      throws NoSuchAlgorithmException {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("schema_version", SCHEMA_VERSION);
    root.put("producer", producer(options));
    root.put("input", input(options));
    root.put("settings", settings(options));

    Map<String, Object> stages = new LinkedHashMap<>();
    stages.put("import", importStage(scans));
    stages.put("mass_detection", massDetectionStage(scans, options));
    root.put("stages", stages);
    return root;
  }

  private static Map<String, Object> producer(Arguments options) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("repository", "VitorFrost/mzmine");
    value.put("ref", options.producerRef());
    value.put("commit", options.producerCommit());
    value.put("application_version", "3.9.1-open-offline-parser-centroid-candidate");
    value.put("java_version", System.getProperty("java.version"));
    value.put("operating_system", System.getProperty("os.name"));
    value.put("thread_count", 1);
    value.put("command", options.command());
    return value;
  }

  private static Map<String, Object> input(Arguments options) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("dataset_id", options.datasetId());
    value.put("relative_path", options.relativePath());
    value.put("size_bytes", options.expectedSize());
    value.put("sha256", options.expectedSha256());
    return value;
  }

  private static Map<String, Object> settings(Arguments options)
      throws NoSuchAlgorithmException {
    String canonical = canonicalSettings(options.sourceMsLevel(), options.noiseLevel());
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("mapping_id", mappingId(options.sourceMsLevel()));
    value.put("sha256", sha256(canonical));
    return value;
  }

  static String mappingId(int sourceMsLevel) {
    return "mzml-import-centroid-source-ms" + sourceMsLevel + "-v1";
  }

  static String canonicalSettings(int sourceMsLevel, double noiseLevel) {
    if (sourceMsLevel != 1 && sourceMsLevel != 2) {
      throw new IllegalArgumentException("source MS level must be 1 or 2");
    }
    if (Double.compare(noiseLevel, 0d) != 0) {
      throw new IllegalArgumentException(
          "The first governed differential mapping requires noise level 0.0");
    }
    return "{\"advanced_import\":false,\"denormalize_fragment_scans\":false,"
        + "\"detect_isotopes_below_noise\":false,"
        + "\"mass_detector\":\"Centroid mass detector\",\"noise_level\":0.0,"
        + "\"netcdf_output\":false,\"source_ms_level\":" + sourceMsLevel + ","
        + "\"spectral_library_import\":false}";
  }

  private static Map<String, Object> importStage(List<MsScan> scans) {
    List<Map<String, Object>> records = new ArrayList<>(scans.size());
    long totalPointCount = 0;
    Map<String, Integer> msLevelCounts = new LinkedHashMap<>();
    for (int ordinal = 0; ordinal < scans.size(); ordinal++) {
      MsScan scan = scans.get(ordinal);
      SpectralArrays arrays = arrays(scan);
      records.add(record(ordinal, scan, arrays, spectrumType(scan)));
      totalPointCount += arrays.size();
      msLevelCounts.merge(Integer.toString(scan.getMsLevel()), 1, Integer::sum);
    }
    return stage(records, totalPointCount, msLevelCounts);
  }

  private static Map<String, Object> massDetectionStage(
      List<MsScan> scans, Arguments options) {
    CentroidMassDetector detector = new CentroidMassDetector();
    List<Map<String, Object>> records = new ArrayList<>();
    long totalPointCount = 0;
    Map<String, Integer> msLevelCounts = new LinkedHashMap<>();
    for (int ordinal = 0; ordinal < scans.size(); ordinal++) {
      MsScan scan = scans.get(ordinal);
      if (scan.getMsLevel() != options.sourceMsLevel()) {
        continue;
      }
      SpectralArrays imported = arrays(scan);
      double[][] detected = detector.getMassValues(
          imported.mzs(), imported.intensities(), options.noiseLevel());
      SpectralArrays arrays = new SpectralArrays(detected[0], detected[1]);
      records.add(record(ordinal, scan, arrays, "CENTROIDED"));
      totalPointCount += arrays.size();
      msLevelCounts.merge(Integer.toString(scan.getMsLevel()), 1, Integer::sum);
    }
    if (records.isEmpty()) {
      throw new IllegalStateException(
          "No scans exist for manually selected source MS level " + options.sourceMsLevel());
    }
    return stage(records, totalPointCount, msLevelCounts);
  }

  private static Map<String, Object> stage(List<Map<String, Object>> records,
      long totalPointCount, Map<String, Integer> msLevelCounts) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("record_count", records.size());
    summary.put("total_point_count", totalPointCount);
    summary.put("ms_level_counts", msLevelCounts);

    Map<String, Object> value = new LinkedHashMap<>();
    value.put("record_key", "scan_key");
    value.put("records", records);
    value.put("summary", summary);
    return value;
  }

  private static Map<String, Object> record(int ordinal, MsScan scan, SpectralArrays arrays,
      String spectrumType) {
    Integer scanNumber = scan.getScanNumber();
    if (scanNumber == null || scanNumber < 0) {
      throw new IllegalStateException("Invalid scan number at ordinal " + ordinal + ": " + scanNumber);
    }

    Map<String, Object> value = new LinkedHashMap<>();
    value.put("scan_key", String.format(Locale.ROOT, "%08d", ordinal));
    value.put("scan_number", scanNumber);
    value.put("ms_level", scan.getMsLevel());
    value.put("polarity", scan.getPolarity().name());
    value.put("spectrum_type", spectrumType);
    value.put("point_count", arrays.size());
    value.put("retention_time_minutes", retentionTimeMinutes(scan));

    SpectrumSummary summary = summarize(arrays);
    value.put("mz_min", summary.mzMin());
    value.put("mz_max", summary.mzMax());
    value.put("intensity_sum", summary.intensitySum());
    value.put("sampled_points", summary.sampledPoints());
    value.put("precursor", precursor(scan));
    return value;
  }

  private static @Nullable Double retentionTimeMinutes(MsScan scan) {
    Float seconds = scan.getRetentionTime();
    if (seconds == null || !Float.isFinite(seconds)) {
      return null;
    }
    return seconds.doubleValue() / 60d;
  }

  private static String spectrumType(MsScan scan) {
    return scan.getSpectrumType() == null ? "UNKNOWN" : scan.getSpectrumType().name();
  }

  private static SpectralArrays arrays(MsScan scan) {
    int count = scan.getNumberOfDataPoints();
    double[] mzs = scan.getMzValues(new double[count]);
    float[] sourceIntensities = scan.getIntensityValues(new float[count]);
    if (mzs.length < count || sourceIntensities.length < count) {
      throw new IllegalStateException("Parser returned arrays shorter than point count for scan "
          + scan.getScanNumber());
    }
    double[] intensities = new double[count];
    System.arraycopy(mzs, 0, mzs, 0, count);
    for (int index = 0; index < count; index++) {
      intensities[index] = sourceIntensities[index];
    }
    if (mzs.length != count) {
      double[] exactMzs = new double[count];
      System.arraycopy(mzs, 0, exactMzs, 0, count);
      mzs = exactMzs;
    }
    return new SpectralArrays(mzs, intensities);
  }

  private static SpectrumSummary summarize(SpectralArrays arrays) {
    if (arrays.size() == 0) {
      return new SpectrumSummary(null, null, 0d, List.of());
    }
    double mzMin = Double.POSITIVE_INFINITY;
    double mzMax = Double.NEGATIVE_INFINITY;
    double intensitySum = 0d;
    for (int index = 0; index < arrays.size(); index++) {
      double mz = requireFinite(arrays.mzs()[index], "m/z", index);
      double intensity = requireFinite(arrays.intensities()[index], "intensity", index);
      mzMin = Math.min(mzMin, mz);
      mzMax = Math.max(mzMax, mz);
      intensitySum += intensity;
    }
    requireFinite(intensitySum, "intensity sum", -1);

    List<Map<String, Object>> sampledPoints = new ArrayList<>();
    for (int index : sampleIndexes(arrays.size())) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("index", index);
      point.put("mz", requireFinite(arrays.mzs()[index], "sample m/z", index));
      point.put("intensity",
          requireFinite(arrays.intensities()[index], "sample intensity", index));
      sampledPoints.add(point);
    }
    return new SpectrumSummary(mzMin, mzMax, intensitySum, sampledPoints);
  }

  static int[] sampleIndexes(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    return switch (size) {
      case 0 -> new int[0];
      case 1 -> new int[]{0};
      case 2 -> new int[]{0, 1};
      default -> new int[]{0, size / 2, size - 1};
    };
  }

  private static @Nullable Map<String, Object> precursor(MsScan scan) {
    List<IsolationInfo> isolations = scan.getIsolations();
    if (isolations == null || isolations.isEmpty()) {
      return null;
    }
    IsolationInfo isolation = isolations.get(0);
    ActivationInfo activation = isolation.getActivationInfo();
    Range<Double> window = isolation.getIsolationMzRange();

    Map<String, Object> value = new LinkedHashMap<>();
    value.put("ms_level", scan.getMsLevel());
    value.put("activation_method",
        activation == null ? "UNKNOWN" : activation.getActivationType().name());
    value.put("activation_energy",
        activation == null ? null : finiteOrNull(activation.getActivationEnergy()));
    value.put("isolation_window_lower",
        window != null && window.hasLowerBound() ? finiteOrNull(window.lowerEndpoint()) : null);
    value.put("isolation_window_upper",
        window != null && window.hasUpperBound() ? finiteOrNull(window.upperEndpoint()) : null);
    value.put("isolation_mz", finiteOrNull(isolation.getPrecursorMz()));
    value.put("charge", isolation.getPrecursorCharge());
    value.put("parent_scan_number", isolation.getPrecursorScanNumber());
    return value;
  }

  private static @Nullable Double finiteOrNull(@Nullable Number value) {
    if (value == null) {
      return null;
    }
    double number = value.doubleValue();
    return Double.isFinite(number) ? number : null;
  }

  private static double requireFinite(double value, String field, int index) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(
          field + " must be finite" + (index >= 0 ? " at index " + index : ""));
    }
    return value;
  }

  private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
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

  private record SpectralArrays(double[] mzs, double[] intensities) {
    SpectralArrays {
      if (mzs.length != intensities.length) {
        throw new IllegalArgumentException("m/z and intensity arrays must have equal length");
      }
    }

    int size() {
      return mzs.length;
    }
  }

  private record SpectrumSummary(@Nullable Double mzMin, @Nullable Double mzMax,
                                 double intensitySum,
                                 List<Map<String, Object>> sampledPoints) {
  }

  private record Arguments(Path input, Path output, String datasetId, String relativePath,
                           long expectedSize, String expectedSha256, int sourceMsLevel,
                           double noiseLevel, String producerRef, String producerCommit,
                           List<String> command) {

    static Arguments parse(String[] args) {
      Map<String, String> values = new LinkedHashMap<>();
      List<String> command = new ArrayList<>();
      command.add("OpenOfflineDifferentialReportMain");
      for (int index = 0; index < args.length; index += 2) {
        if (index + 1 >= args.length || !args[index].startsWith("--")) {
          throw new IllegalArgumentException("Arguments must be --name value pairs");
        }
        String key = args[index].substring(2);
        if (values.put(key, args[index + 1]) != null) {
          throw new IllegalArgumentException("Duplicate argument --" + key);
        }
        command.add(args[index]);
        command.add(args[index + 1]);
      }

      String expectedSha = require(values, "expected-sha256").toLowerCase(Locale.ROOT);
      if (!expectedSha.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("expected-sha256 must be 64 lowercase hexadecimal chars");
      }
      String producerCommit = require(values, "producer-commit").toLowerCase(Locale.ROOT);
      if (!producerCommit.matches("[0-9a-f]{40}")) {
        throw new IllegalArgumentException("producer-commit must be 40 lowercase hexadecimal chars");
      }
      int sourceLevel = Integer.parseInt(require(values, "source-ms-level"));
      double noise = Double.parseDouble(require(values, "noise-level"));
      canonicalSettings(sourceLevel, noise);

      return new Arguments(
          Path.of(require(values, "input")),
          Path.of(require(values, "output")),
          require(values, "dataset-id"),
          require(values, "relative-path"),
          Long.parseLong(require(values, "expected-size")),
          expectedSha,
          sourceLevel,
          noise,
          require(values, "producer-ref"),
          producerCommit,
          List.copyOf(command));
    }

    private static String require(Map<String, String> values, String key) {
      String value = values.get(key);
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Missing required argument --" + key);
      }
      return value;
    }
  }
}
