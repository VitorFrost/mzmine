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
package io.github.mzmine.datamodel.featuredata.impl;

import io.github.mzmine.util.MemoryMapStorage;
import java.nio.DoubleBuffer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Null-storage array buffer operation used by the first executable parser oracle.
 *
 * <p>No memory mapping, persistence, cleanup, or unavailable storage behavior is implemented.
 * Supplying non-null storage fails explicitly.</p>
 */
public final class StorageUtils {

  private StorageUtils() {
  }

  public static @NotNull DoubleBuffer storeValuesToDoubleBuffer(
      @Nullable MemoryMapStorage storage, double @NotNull [] values) {
    if (storage != null) {
      throw new UnsupportedOperationException(
          "The v4.0.8 executable oracle supports only the public null-storage path");
    }
    return DoubleBuffer.wrap(Arrays.copyOf(values, values.length)).asReadOnlyBuffer();
  }
}
