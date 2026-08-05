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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class OpenOfflineDifferentialReportMainTest {

  @Test
  void samplesFirstMiddleAndLastPointsDeterministically() {
    assertArrayEquals(new int[0], OpenOfflineDifferentialReportMain.sampleIndexes(0));
    assertArrayEquals(new int[]{0}, OpenOfflineDifferentialReportMain.sampleIndexes(1));
    assertArrayEquals(new int[]{0, 1}, OpenOfflineDifferentialReportMain.sampleIndexes(2));
    assertArrayEquals(new int[]{0, 2, 4}, OpenOfflineDifferentialReportMain.sampleIndexes(5));
    assertArrayEquals(new int[]{0, 3, 5}, OpenOfflineDifferentialReportMain.sampleIndexes(6));
  }

  @Test
  void usesTheSameGovernedSettingsIdentityAsTheV408Oracle() throws Exception {
    String canonical = OpenOfflineDifferentialReportMain.canonicalSettings(1, 0d);
    assertEquals("mzml-import-centroid-source-ms1-v1",
        OpenOfflineDifferentialReportMain.mappingId(1));
    assertEquals(
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(canonical.getBytes(StandardCharsets.UTF_8))),
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(OpenOfflineDifferentialReportMain.canonicalSettings(1, 0d)
                .getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void acceptsOnlyManualMs1OrMs2AndZeroNoiseForTheFirstMapping() {
    assertEquals("mzml-import-centroid-source-ms2-v1",
        OpenOfflineDifferentialReportMain.mappingId(2));
    assertThrows(IllegalArgumentException.class,
        () -> OpenOfflineDifferentialReportMain.canonicalSettings(0, 0d));
    assertThrows(IllegalArgumentException.class,
        () -> OpenOfflineDifferentialReportMain.canonicalSettings(3, 0d));
    assertThrows(IllegalArgumentException.class,
        () -> OpenOfflineDifferentialReportMain.canonicalSettings(1, 1d));
  }
}
