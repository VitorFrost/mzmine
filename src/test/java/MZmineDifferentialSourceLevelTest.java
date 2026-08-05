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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MZmineDifferentialSourceLevelTest {

  @Test
  void acceptsOnlyManualMs1OrMs2Selections() {
    assertEquals(1, MZmineDifferentialReportAcceptanceTest.parseSourceMsLevel(null));
    assertEquals(1, MZmineDifferentialReportAcceptanceTest.parseSourceMsLevel("1"));
    assertEquals(2, MZmineDifferentialReportAcceptanceTest.parseSourceMsLevel("2"));
    assertThrows(IllegalArgumentException.class,
        () -> MZmineDifferentialReportAcceptanceTest.parseSourceMsLevel("0"));
    assertThrows(IllegalArgumentException.class,
        () -> MZmineDifferentialReportAcceptanceTest.parseSourceMsLevel("3"));
  }

  @Test
  void sourceSelectionsHaveDistinctProvenance() {
    assertEquals("mzml-import-centroid-source-ms1-v1",
        MZmineDifferentialReportAcceptanceTest.settingsMappingId(1));
    assertEquals("mzml-import-centroid-source-ms2-v1",
        MZmineDifferentialReportAcceptanceTest.settingsMappingId(2));

    String ms1 = MZmineDifferentialReportAcceptanceTest.settingsCanonicalJson(1);
    String ms2 = MZmineDifferentialReportAcceptanceTest.settingsCanonicalJson(2);
    assertTrue(ms1.contains("\"source_ms_level\":1"));
    assertTrue(ms2.contains("\"source_ms_level\":2"));
    assertTrue(!ms1.equals(ms2));
  }
}
