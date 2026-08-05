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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V408DifferentialReportMainTest {

  @Test
  void settingsMatchTheOpenOfflineMapping() {
    assertEquals("mzml-import-centroid-source-ms1-v1",
        V408DifferentialReportMain.mappingId(1));
    assertEquals("mzml-import-centroid-source-ms2-v1",
        V408DifferentialReportMain.mappingId(2));
    assertEquals(
        "{\"advanced_import\":false,\"denormalize_fragment_scans\":false,"
            + "\"detect_isotopes_below_noise\":false,"
            + "\"mass_detector\":\"Centroid mass detector\",\"noise_level\":0.0,"
            + "\"netcdf_output\":false,\"source_ms_level\":1,"
            + "\"spectral_library_import\":false}",
        V408DifferentialReportMain.canonicalSettings(1, 0d));
    assertThrows(IllegalArgumentException.class,
        () -> V408DifferentialReportMain.canonicalSettings(1, 1d));
  }

  @Test
  void sampleIndexesMatchTheSharedReportContract() {
    assertArrayEquals(new int[0], V408DifferentialReportMain.sampleIndexes(0));
    assertArrayEquals(new int[]{0}, V408DifferentialReportMain.sampleIndexes(1));
    assertArrayEquals(new int[]{0, 1}, V408DifferentialReportMain.sampleIndexes(2));
    assertArrayEquals(new int[]{0, 2, 4}, V408DifferentialReportMain.sampleIndexes(5));
    assertThrows(IllegalArgumentException.class,
        () -> V408DifferentialReportMain.sampleIndexes(-1));
  }

  @Test
  void jsonSerializationIsDeterministicAndEscaped() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schema_version", 1);
    value.put("text", "line\n\"quoted\"");
    value.put("values", List.of(1, 2.5, true));
    assertEquals(
        "{\"schema_version\":1,\"text\":\"line\\n\\\"quoted\\\"\","
            + "\"values\":[1,2.5,true]}",
        V408DifferentialReportMain.toJson(value));
  }

  @Test
  void selectedMsLevelsHaveDifferentCanonicalSettings() {
    String ms1 = V408DifferentialReportMain.canonicalSettings(1, 0d);
    String ms2 = V408DifferentialReportMain.canonicalSettings(2, 0d);
    assertTrue(ms1.contains("\"source_ms_level\":1"));
    assertTrue(ms2.contains("\"source_ms_level\":2"));
    assertTrue(!ms1.equals(ms2));
  }
}
