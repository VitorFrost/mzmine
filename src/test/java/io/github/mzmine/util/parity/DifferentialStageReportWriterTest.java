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
package io.github.mzmine.util.parity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DifferentialStageReportWriterTest {

  @Test
  void sampleIndexesAreStableAndBounded() {
    assertArrayEquals(new int[0], DifferentialStageReportWriter.sampleIndexes(0));
    assertArrayEquals(new int[]{0}, DifferentialStageReportWriter.sampleIndexes(1));
    assertArrayEquals(new int[]{0, 1}, DifferentialStageReportWriter.sampleIndexes(2));
    assertArrayEquals(new int[]{0, 2, 4}, DifferentialStageReportWriter.sampleIndexes(5));
    assertThrows(IllegalArgumentException.class,
        () -> DifferentialStageReportWriter.sampleIndexes(-1));
  }

  @Test
  void serializerPreservesInsertionOrderAndEscapesControlCharacters() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schema_version", 1);
    value.put("message", "line 1\n\"line 2\"");
    value.put("values", List.of(true, 2, 3.5));

    assertEquals(
        "{\"schema_version\":1,\"message\":\"line 1\\n\\\"line 2\\\"\",\"values\":[true,2,3.5]}",
        DifferentialStageReportWriter.toJson(value));
  }

  @Test
  void metadataRejectsInvalidProvenance() {
    assertThrows(IllegalArgumentException.class,
        () -> new DifferentialStageReportWriter.ProducerMetadata(
            "VitorFrost/mzmine", "open-offline-main", "not-a-sha", "3.9.1", "20",
            "Linux", 1, List.of("java")));
    assertThrows(IllegalArgumentException.class,
        () -> new DifferentialStageReportWriter.InputMetadata(
            "dataset", "file.mzML", 0, "0".repeat(64)));
    assertThrows(IllegalArgumentException.class,
        () -> new DifferentialStageReportWriter.SettingsMetadata("mapping", "A".repeat(64)));
  }

  @Test
  void serializerRejectsNonFiniteNumbers() {
    assertThrows(IllegalArgumentException.class,
        () -> DifferentialStageReportWriter.toJson(Double.NaN));
    assertThrows(IllegalArgumentException.class,
        () -> DifferentialStageReportWriter.toJson(Double.POSITIVE_INFINITY));
  }
}
