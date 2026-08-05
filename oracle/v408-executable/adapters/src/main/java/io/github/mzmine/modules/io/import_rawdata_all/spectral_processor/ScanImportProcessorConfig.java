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
package io.github.mzmine.modules.io.import_rawdata_all.spectral_processor;

import io.github.mzmine.modules.io.import_rawdata_mzml.msdk.data.BuildingMzMLMsScan;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit no-filter, no-advanced-processing configuration for the first parser oracle.
 *
 * <p>The adapter never inspects manufacturer, instrument model, scan definition, or the manually
 * selected source MS level. Source-level selection and centroid processing occur after parsing in a
 * separately governed stage.</p>
 */
public final class ScanImportProcessorConfig {

  @FunctionalInterface
  public interface ScanFilter {
    boolean matches(@NotNull BuildingMzMLMsScan scan);
  }

  @FunctionalInterface
  public interface ScanProcessor {
    @NotNull SimpleSpectralArrays processScan(@NotNull BuildingMzMLMsScan scan,
        @NotNull SimpleSpectralArrays data);
  }

  private static final ScanFilter ACCEPT_ALL = scan -> true;
  private static final ScanProcessor IDENTITY = (scan, data) -> data;

  public ScanImportProcessorConfig() {
  }

  public static @NotNull ScanImportProcessorConfig noProcessing() {
    return new ScanImportProcessorConfig();
  }

  public @NotNull ScanFilter scanFilter() {
    return ACCEPT_ALL;
  }

  public @NotNull ScanProcessor processor() {
    return IDENTITY;
  }

  public boolean isMassDetectActive(int msLevel) {
    return false;
  }
}
