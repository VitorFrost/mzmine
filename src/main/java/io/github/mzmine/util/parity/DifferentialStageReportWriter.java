/*
 * Copyright (c) 2004-2026 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.mzmine.util.parity;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MassList;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.datamodel.msms.DDAMsMsInfo;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Writes the normalized import and mass-detection report used by the public MZmine v4.0.8
 * differential harness.
 *
 * <p>The writer intentionally depends only on stable public data-model interfaces. It does not
 * initialize the GUI, schedule processing, inspect implementation-specific storage, or change the
 * raw data. This keeps the normalized evidence producer portable across the governed code lines.</p>
 */
public final class DifferentialStageReportWriter {

  public static final int SCHEMA_VERSION = 1;

  private DifferentialStageReportWriter() {
  }

  /** Metadata that identifies the exact executable that produced a report. */
  public record ProducerMetadata(@NotNull String repository, @NotNull String ref,
                                 @NotNull String commit, @NotNull String applicationVersion,
                                 @NotNull String javaVersion, @NotNull String operatingSystem,
                                 int threadCount, @NotNull List<String> command) {

    public ProducerMetadata {
      requireText(repository, "repository");
      requireText(ref, "ref");
      requireSha(commit, 40, "commit");
      requireText(applicationVersion, "applicationVersion");
      requireText(javaVersion, "javaVersion");
      requireText(operatingSystem, "operatingSystem");
      if (threadCount < 1) {
        throw new IllegalArgumentException("threadCount must be positive");
      }
      command = List.copyOf(command);
      if (command.isEmpty() || command.stream().anyMatch(value -> value == null || value.isBlank())) {
        throw new IllegalArgumentException("command must contain non-empty values");
      }
    }
  }

  /** Metadata for the governed input bytes. */
  public record InputMetadata(@NotNull String datasetId, @NotNull String relativePath,
                              long sizeBytes, @NotNull String sha256) {

    public InputMetadata {
      requireText(datasetId, "datasetId");
      requireText(relativePath, "relativePath");
      if (sizeBytes < 1) {
        throw new IllegalArgumentException("sizeBytes must be positive");
      }
      requireSha(sha256, 64, "sha256");
    }
  }

  /** Metadata for the reviewed parameter mapping used by both code lines. */
  public record SettingsMetadata(@NotNull String mappingId, @NotNull String sha256) {

    public SettingsMetadata {
      requireText(mappingId, "mappingId");
      requireSha(sha256, 64, "sha256");
    }
  }

  public static void write(@NotNull Path output, @NotNull ProducerMetadata producer,
      @NotNull InputMetadata input, @NotNull SettingsMetadata settings,
      @NotNull RawDataFile rawDataFile) throws IOException {
    Objects.requireNonNull(output, "output");
    Map<String, Object> report = buildReport(producer, input, settings, rawDataFile);
    Path parent = output.toAbsolutePath().normalize().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(output, toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
  }

  /** Builds a deterministic insertion-ordered report object for tests and alternate serializers. */
  public static @NotNull Map<String, Object> buildReport(@NotNull ProducerMetadata producer,
      @NotNull InputMetadata input, @NotNull SettingsMetadata settings,
      @NotNull RawDataFile rawDataFile) {
    Objects.requireNonNull(producer, "producer");
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(rawDataFile, "rawDataFile");

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("schema_version", SCHEMA_VERSION);
    report.put("producer", producerMap(producer));
    report.put("input", inputMap(input));
    report.put("settings", settingsMap(settings));

    List<Scan> scans = List.copyOf(rawDataFile.getScans());
    Map<String, Object> stages = new LinkedHashMap<>();
    stages.put("import", stage(scans, false));
    stages.put("mass_detection", stage(scans, true));
    report.put("stages", stages);
    return report;
  }

  /** Deterministic JSON serializer for the limited report value types. */
  public static @NotNull String toJson(@Nullable Object value) {
    StringBuilder builder = new StringBuilder(16_384);
    appendJson(builder, value);
    return builder.toString();
  }

  private static Map<String, Object> producerMap(ProducerMetadata producer) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("repository", producer.repository());
    value.put("ref", producer.ref());
    value.put("commit", producer.commit());
    value.put("application_version", producer.applicationVersion());
    value.put("java_version", producer.javaVersion());
    value.put("operating_system", producer.operatingSystem());
    value.put("thread_count", producer.threadCount());
    value.put("command", producer.command());
    return value;
  }

  private static Map<String, Object> inputMap(InputMetadata input) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("dataset_id", input.datasetId());
    value.put("relative_path", input.relativePath());
    value.put("size_bytes", input.sizeBytes());
    value.put("sha256", input.sha256());
    return value;
  }

  private static Map<String, Object> settingsMap(SettingsMetadata settings) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("mapping_id", settings.mappingId());
    value.put("sha256", settings.sha256());
    return value;
  }

  private static Map<String, Object> stage(List<Scan> scans, boolean massDetection) {
    List<Map<String, Object>> records = new ArrayList<>();
    long totalPoints = 0;
    Map<String, Integer> msLevelCounts = new LinkedHashMap<>();

    for (int ordinal = 0; ordinal < scans.size(); ordinal++) {
      Scan scan = scans.get(ordinal);
      MassSpectrum spectrum = massDetection ? scan.getMassList() : scan;
      if (spectrum == null) {
        continue;
      }
      records.add(scanRecord(ordinal, scan, spectrum));
      totalPoints += spectrum.getNumberOfDataPoints();
      String level = Integer.toString(scan.getMSLevel());
      msLevelCounts.merge(level, 1, Integer::sum);
    }

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("record_count", records.size());
    summary.put("total_point_count", totalPoints);
    summary.put("ms_level_counts", msLevelCounts);

    Map<String, Object> stage = new LinkedHashMap<>();
    stage.put("record_key", "scan_key");
    stage.put("records", records);
    stage.put("summary", summary);
    return stage;
  }

  private static Map<String, Object> scanRecord(int ordinal, Scan scan, MassSpectrum spectrum) {
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("scan_key", String.format(Locale.ROOT, "%08d", ordinal));
    record.put("scan_number", scan.getScanNumber());
    record.put("ms_level", scan.getMSLevel());
    record.put("polarity", scan.getPolarity().name());
    record.put("spectrum_type", spectrum.getSpectrumType().name());
    record.put("point_count", spectrum.getNumberOfDataPoints());
    record.put("retention_time_minutes", finiteOrNull(scan.getRetentionTime()));

    SpectrumSummary summary = summarize(spectrum);
    record.put("mz_min", summary.mzMin());
    record.put("mz_max", summary.mzMax());
    record.put("intensity_sum", summary.intensitySum());
    record.put("sampled_points", summary.sampledPoints());
    record.put("precursor", precursor(scan.getMsMsInfo()));
    return record;
  }

  private static SpectrumSummary summarize(MassSpectrum spectrum) {
    int size = spectrum.getNumberOfDataPoints();
    if (size == 0) {
      return new SpectrumSummary(null, null, 0d, List.of());
    }

    double mzMin = Double.POSITIVE_INFINITY;
    double mzMax = Double.NEGATIVE_INFINITY;
    double intensitySum = 0d;
    for (int index = 0; index < size; index++) {
      double mz = requireFinite(spectrum.getMzValue(index), "m/z", index);
      double intensity = requireFinite(spectrum.getIntensityValue(index), "intensity", index);
      mzMin = Math.min(mzMin, mz);
      mzMax = Math.max(mzMax, mz);
      intensitySum += intensity;
    }
    requireFinite(intensitySum, "intensity sum", -1);

    List<Map<String, Object>> sampledPoints = new ArrayList<>();
    for (int index : sampleIndexes(size)) {
      Map<String, Object> point = new LinkedHashMap<>();
      point.put("index", index);
      point.put("mz", requireFinite(spectrum.getMzValue(index), "sample m/z", index));
      point.put("intensity",
          requireFinite(spectrum.getIntensityValue(index), "sample intensity", index));
      sampledPoints.add(point);
    }
    return new SpectrumSummary(mzMin, mzMax, intensitySum, sampledPoints);
  }

  static int[] sampleIndexes(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    if (size == 0) {
      return new int[0];
    }
    if (size == 1) {
      return new int[]{0};
    }
    if (size == 2) {
      return new int[]{0, 1};
    }
    return new int[]{0, size / 2, size - 1};
  }

  private static @Nullable Map<String, Object> precursor(@Nullable MsMsInfo info) {
    if (info == null) {
      return null;
    }

    Map<String, Object> precursor = new LinkedHashMap<>();
    precursor.put("ms_level", info.getMsLevel());
    precursor.put("activation_method", info.getActivationMethod().name());
    precursor.put("activation_energy", finiteOrNull(info.getActivationEnergy()));

    Range<Double> window = info.getIsolationWindow();
    precursor.put("isolation_window_lower",
        window != null && window.hasLowerBound() ? finiteOrNull(window.lowerEndpoint()) : null);
    precursor.put("isolation_window_upper",
        window != null && window.hasUpperBound() ? finiteOrNull(window.upperEndpoint()) : null);

    if (info instanceof DDAMsMsInfo dda) {
      precursor.put("isolation_mz", finiteOrNull(dda.getIsolationMz()));
      precursor.put("charge", dda.getPrecursorCharge());
      Scan parent = dda.getParentScan();
      precursor.put("parent_scan_number", parent == null ? null : parent.getScanNumber());
    }
    return precursor;
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

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }

  private static void requireSha(String value, int length, String field) {
    requireText(value, field);
    if (value.length() != length || !value.matches("[0-9a-f]+")) {
      throw new IllegalArgumentException(field + " must be a lowercase hexadecimal SHA");
    }
  }

  @SuppressWarnings("unchecked")
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
      throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass());
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

  private record SpectrumSummary(@Nullable Double mzMin, @Nullable Double mzMax,
                                 double intensitySum,
                                 @NotNull List<Map<String, Object>> sampledPoints) {
  }
}
