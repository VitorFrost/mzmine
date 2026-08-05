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
package io.github.mzmine.util;

/**
 * Deliberately behavior-free boundary type for the public MZmine v4.0.8 parser oracle.
 *
 * <p>The frozen public parser accepts {@code MemoryMapStorage} as a nullable constructor and method
 * argument, but the type is absent from the frozen public source tree. The governed non-IMS oracle
 * always supplies {@code null}; therefore no constructor, factory, buffer, lifecycle, cleanup, or
 * persistence behavior is implemented here.</p>
 *
 * <p>This class is not a reconstruction of the unavailable implementation and must not be used by
 * the open-offline application runtime. Its only permitted use is compiling the frozen scientific
 * source slice whose null-storage path is covered by differential validation.</p>
 */
public final class MemoryMapStorage {

  private MemoryMapStorage() {
    throw new UnsupportedOperationException(
        "The v4.0.8 public parser oracle supports only a null MemoryMapStorage reference");
  }
}
