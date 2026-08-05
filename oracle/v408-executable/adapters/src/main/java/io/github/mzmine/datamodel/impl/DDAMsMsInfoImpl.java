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
package io.github.mzmine.datamodel.impl;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.MetadataOnlyScan;
import io.github.mzmine.datamodel.msms.ActivationMethod;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Constructor-compatible boundary for an application object intentionally excluded from the oracle.
 *
 * <p>The normalized report reads isolation and precursor values directly from the unchanged public
 * parser record. Calling this application conversion path is therefore a configuration error and
 * fails explicitly instead of fabricating an object.</p>
 */
public final class DDAMsMsInfoImpl implements MsMsInfo {

  public DDAMsMsInfoImpl(@Nullable Double precursorMz, @Nullable Integer precursorCharge,
      @Nullable Float activationEnergy, @Nullable MetadataOnlyScan msMsScan,
      @Nullable MetadataOnlyScan parentScan, int msLevel,
      @Nullable ActivationMethod activationMethod, @Nullable Range<Double> isolationWindow) {
    throw new UnsupportedOperationException(
        "Application MsMsInfo conversion is outside the v4.0.8 parser oracle; use isolation records directly");
  }
}
