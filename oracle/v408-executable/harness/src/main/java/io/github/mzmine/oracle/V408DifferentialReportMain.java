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
package io.github.mzmine.oracle;

import com.google.common.collect.Range;
import io.github.msdk.MSDKException;
import io.github.msdk.datamodel.ActivationInfo;
import io.github.msdk.datamodel.IsolationInfo;
import io.github.mzmine.datamodel.MassSpectrumType;
import io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid.CentroidMassDetector;
import io.github.mzmine.modules.io.import_rawdata_all.spectral_processor.ScanImportProcessorConfig;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.MzMLFileImportMethod;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.BuildingMzMLMsScan;
import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.MzMLRawDataFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Executes the frozen public MZmine v4.0.8 parser and centroid array method on governed mzML bytes.
 *
 * <p>The source MS level is an explicit user selection applied after parsing. It is never inferred
 * from manufacturer, instrument model, precursor metadata, or acquisition terminology.</p>
 */
public final class V408DifferentialReportMain {

  private static final int SCHEMA_VERSION = 1;
  private static final String ORACLE_COMMIT =
      "8029f930d28c0447f0acf2bcabef0a79865ad434";

  private V408DifferentialReportMain() {
  }

  public static void main(String[] args) throws Exception {
    Arguments options = Arguments.parse(args);
    validateInput(options);

    MzMLFileImportMethod importer = new MzMLFileImportMethod(
        Instant.EPOCH,
        options.input().toFile(),
        null,
        ScanImportProcessorConfig.noProcessing());
    MzMLRawDataFile raw = parse(importer);
    List<BuildingMzMLMsScan> scans = List.copyOf(raw.getScans());
    if (scans.isEmpty()) {
      throw new IllegalStateException("The governed mzML parser returned no scans");
    }

    Map<String, Object> report = report(options, scans);
    Path parent = options.output().toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(
        options.output(), toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  private static MzMLRawDataFile parse(MzMLFileImportMethod importer) throws MSDKException {
    MzMLRawDataFile raw = importer.parseMzMl();
    if (raw == null) {
      throw new IllegalStateException("The governed mzML parser returned null");
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

  private static Map<String, Object> report(
      Arguments options, List<BuildingMzMLMsScan> scans) throws NoSuchAlgorithmException {
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
    value.put("repository", "mzmine/mzmine");
    value.put("ref", "v4.0.8");
    value.put("commit", ORACLE_COMMIT);
    value.put("application_version", "4.0.8-public-parser-centroid-oracle");
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

  private static Map<String, Object> importStage(List<BuildingMzMLMsScan> scans) {
    List<Map<String, Object>> records = new ArrayList<>(scans.size());
    long totalPointCount = 0;
    Map<String, Integer> msLevelCounts = new LinkedHashMap<>();
    for (int ordinal = 0; ordinal < scans.size(); ordinal++) {
      BuildingMzMLMsScan scan = scans.get(ordinal);
      SpectralArrays arrays = arrays(scan);
      records.add(record(ordinal, scan, arrays, spectrumType(scan)));
      totalPointCount += arrays.size();
      msLevelCounts.merge(Integer.toString(scan.getMSLevel()), 1, Integer::sum);
    }
    return stage(records, totalPointCount, msLevelCounts);
  }

  private static Map<String, Object> massDetectionStage(
      List<BuildingMzMLMsScan> scans, Arguments options) {
    CentroidMassDetector detector = new CentroidMassDetector();
    List<Map<String, Object>> records = new ArrayList<>();
    long totalPointCount = 0;
    Map<String, Integer> msLevelCounts = new LinkedHashMap<>();
    for (int ordinal = 0; ordinal < scans.size(); ordinal++) {
      BuildingMzMLMsScan scan = scans.get(ordinal);
      if (scan.getMSLevel() != options.sourceMsLevel()) {
        continue;
      }
      SpectralArrays imported = arrays(scan);
      double[][] detected = detector.getMassValues(
          imported.mzs(), imported.intensities(), options.noiseLevel());
      SpectralArrays arrays = new SpectralArrays(detected[0], detected[1]);
      records.add(record(ordinal, scan, arrays, "CENTROIDED"));
      totalPointCount += arrays.size();
      msLevelCounts.merge(Integer.toString(scan.getMSLevel()), 1, Integer::sum);
    }
    if (records.isEmpty()) {
      throw new IllegalStateException(
          "No scans exist for manually selected source MS level " + options.sourceMsLevel());
    }
    return stage(records, totalPointCount, msLevelCounts);
  }

  private static Map<String, Object> stage(
      List<Map<String, Object>> records,
      long totalPointCount,
      Map<String, Integer> msLevelCounts) {
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

  private static Map<String, Object> record(
      int ordinal, BuildingMzMLMsScan scan, SpectralArrays arrays, String spectrumType) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("scan_key", String.format(Locale.ROOT, "%08d", ordinal));
    value.put("scan_number", scan.getScanNumber());
    value.put("ms_level", scan.getMSLevel());
    value.put("polarity", scan.getPolarity().name());
    value.put("spectrum_type", spectrumType);
    value.put("point_count", arrays.size());
    value.put("retention_time_minutes", finiteOrNull(scan.getRetentionTime()));

    SpectrumSummary summary = summarize(arrays);
    value.put("mz_min", summary.mzMin());
    value.put("mz_max", summary.mzMax());
    value.put("intensity_sum", summary.intensitySum());
    value.put("sampled_points", summary.sampledPoints());
    value.put("precursor", precursor(scan));
    return value;
  }

  private static String spectrumType(BuildingMzMLMsScan scan) {
    MassSpectrumType type = scan.getSpectrumType();
    return type == null ? "UNKNOWN" : type.name();
  }

  private static SpectralArrays arrays(BuildingMzMLMsScan scan) {
    return new SpectralArrays(
        copy(scan.getDoubleBufferMzValues()),
        copy(scan.getDoubleBufferIntensityValues()));
  }

  private static double[] copy(DoubleBuffer source) {
    DoubleBuffer copy = source.asReadOnlyBuffer();
    copy.position(0);
    double[] result = new double[copy.limit()];
    copy.get(result);
    return result;
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

  private static @Nullable Map<String, Object> precursor(BuildingMzMLMsScan scan) {
    List<IsolationInfo> isolations = scan.getIsolations();
    if (isolations.isEmpty()) {
      return null;
    }
    IsolationInfo isolation = isolations.get(0);
    ActivationInfo activation = isolation.getActivationInfo();
    Range<Double> window = isolation.getIsolationMzRange();

    Map<String, Object> value = new LinkedHashMap<>();
    value.put("ms_level", scan.getMSLevel());
    value.put("activation_method",
        activation == null ? "UNKNOWN" : activation.getActivationType().name());
    value.put("activation_energy",
        activation == null ? null : finiteOrNull(activation.getActivationEnergy()));
    value.put("isolation_window_lower",
        window.hasLowerBound() ? finiteOrNull(window.lowerEndpoint()) : null);
    value.put("isolation_window_upper",
        window.hasUpperBound() ? finiteOrNull(window.upperEndpoint()) : null);
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

  static String toJson(@Nullable Object value) {
    StringBuilder builder = new StringBuilder(16_384);
    appendJson(builder, value);
    return builder.toString();
  }

  private static void appendJson(StringBuilder builder, @Nullable Object value) {
    if (value == null) {
      builder.append("null");
    } else if (value instanceof String text) {
      appendString(builder, text);
    } else if (value instanceof Boolean bool) {
      builder.append(bool);
    } else if (value instanceof Number number) {
      double asDouble = number.doubleValue();
      if ((number instanceof Double || number instanceof Float) && !Double.isFinite(asDouble)) {
        throw new IllegalArgumentException("JSON numbers must be finite");
      }
      builder.append(number);
    } else if (value instanceof Map<?, ?> map) {
      builder.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("JSON object keys must be strings");
        }
        if (!first) {
          builder.append(',');
        }
        first = false;
        appendString(builder, key);
        builder.append(':');
        appendJson(builder, entry.getValue());
      }
      builder.append('}');
    } else if (value instanceof Iterable<?> iterable) {
      builder.append('[');
      boolean first = true;
      for (Object element : iterable) {
        if (!first) {
          builder.append(',');
        }
        first = false;
        appendJson(builder, element);
      }
      builder.append(']');
    } else if (value instanceof int[] values) {
      builder.append('[');
      for (int index = 0; index < values.length; index++) {
        if (index > 0) {
          builder.append(',');
        }
        builder.append(values[index]);
      }
      builder.append(']');
    } else {
      throw new IllegalArgumentException("Unsupported JSON type: " + value.getClass().getName());
    }
  }

  private static void appendString(StringBuilder builder, String value) {
    builder.append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (character < 0x20) {
            builder.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
          } else {
            builder.append(character);
          }
        }
      }
    }
    builder.append('"');
  }

  private record SpectralArrays(double[] mzs, double[] intensities) {

    private SpectralArrays {
      Objects.requireNonNull(mzs, "mzs");
      Objects.requireNonNull(intensities, "intensities");
      if (mzs.length != intensities.length) {
        throw new IllegalArgumentException("m/z and intensity arrays must have equal length");
      }
    }

    int size() {
      return mzs.length;
    }
  }

  private record SpectrumSummary(
      @Nullable Double mzMin,
      @Nullable Double mzMax,
      double intensitySum,
      List<Map<String, Object>> sampledPoints) {
  }

  private record Arguments(
      Path input,
      Path output,
      String datasetId,
      String relativePath,
      long expectedSize,
      String expectedSha256,
      int sourceMsLevel,
      double noiseLevel,
      List<String> command) {

    static Arguments parse(String[] args) {
      Map<String, String> values = new LinkedHashMap<>();
      for (int index = 0; index < args.length; index += 2) {
        if (index + 1 >= args.length || !args[index].startsWith("--")) {
          throw new IllegalArgumentException(
              "Arguments must be supplied as --name value pairs");
        }
        if (values.put(args[index], args[index + 1]) != null) {
          throw new IllegalArgumentException("Duplicate argument: " + args[index]);
        }
      }
      String input = required(values, "--input");
      String output = required(values, "--output");
      String datasetId = required(values, "--dataset-id");
      String relativePath = required(values, "--relative-path");
      long expectedSize = Long.parseLong(required(values, "--expected-size"));
      String expectedSha = required(values, "--expected-sha256");
      int sourceMsLevel = Integer.parseInt(values.getOrDefault("--source-ms-level", "1"));
      double noiseLevel = Double.parseDouble(values.getOrDefault("--noise-level", "0.0"));
      if (sourceMsLevel != 1 && sourceMsLevel != 2) {
        throw new IllegalArgumentException("--source-ms-level must be exactly 1 or 2");
      }
      if (expectedSize < 1) {
        throw new IllegalArgumentException("--expected-size must be positive");
      }
      if (!expectedSha.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException(
            "--expected-sha256 must be lowercase 64-character hexadecimal");
      }
      if (datasetId.isBlank() || relativePath.isBlank()) {
        throw new IllegalArgumentException("Dataset identifiers must not be blank");
      }
      return new Arguments(
          Path.of(input).toAbsolutePath().normalize(),
          Path.of(output).toAbsolutePath().normalize(),
          datasetId,
          relativePath,
          expectedSize,
          expectedSha,
          sourceMsLevel,
          noiseLevel,
          List.copyOf(List.of(args)));
    }

    private static String required(Map<String, String> values, String key) {
      String value = values.remove(key);
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("Missing required argument: " + key);
      }
      return value;
    }
  }
}
