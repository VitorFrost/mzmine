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
package io.github.mzmine.modules.io.import_rawdata_mzml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.project.impl.MZmineProjectImpl;
import io.github.mzmine.taskcontrol.TaskStatus;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verifies that the open-offline baseline can parse a small mzML file without GUI interaction. */
class OpenOfflineMzMLImportTest {

  private static final Path FIXTURE =
      Path.of("src", "test", "resources", "open_offline", "sciex_two_scan.mzML");

  @Test
  void importsTwoScanMzMLWithExpectedSpectralData() {
    final File fixture = FIXTURE.toFile();
    assertTrue(fixture.isFile(), () -> "Missing mzML fixture: " + fixture.getAbsolutePath());

    final MZmineProject project = new MZmineProjectImpl();
    final MSDKmzMLImportParameters parameters = new MSDKmzMLImportParameters();
    parameters.getParameter(MSDKmzMLImportParameters.fileNames)
        .setValue(new File[]{fixture});

    final MSDKmzMLImportTask task = new MSDKmzMLImportTask(project, fixture,
        MSDKmzMLImportModule.class, parameters, Instant.parse("2026-08-03T00:00:00Z"), null);

    task.run();

    assertEquals(TaskStatus.FINISHED, task.getStatus(), task::getErrorMessage);

    final RawDataFile[] files = project.getDataFiles();
    assertEquals(1, files.length);

    final RawDataFile raw = files[0];
    try {
      assertEquals(2, raw.getNumOfScans());

      final Scan ms1 = raw.getScan(0);
      assertNotNull(ms1);
      assertEquals(1, ms1.getMSLevel());
      assertEquals(3, ms1.getNumberOfDataPoints());
      assertRange(ms1.getDataPointMZRange(), 100.0, 300.0);

      final Scan ms2 = raw.getScan(1);
      assertNotNull(ms2);
      assertEquals(2, ms2.getMSLevel());
      assertEquals(2, ms2.getNumberOfDataPoints());
      assertRange(ms2.getDataPointMZRange(), 105.0, 150.0);
    } finally {
      raw.close();
    }
  }

  private static void assertRange(final Range<Double> range, final double lower,
      final double upper) {
    assertNotNull(range);
    assertEquals(lower, range.lowerEndpoint(), 0.0001);
    assertEquals(upper, range.upperEndpoint(), 0.0001);
  }
}
