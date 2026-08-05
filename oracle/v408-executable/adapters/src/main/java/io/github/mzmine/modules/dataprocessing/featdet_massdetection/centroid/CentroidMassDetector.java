/*
 * Copyright (c) 2004-2022 The MZmine Development Team
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
package io.github.mzmine.modules.dataprocessing.featdet_massdetection.centroid;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

/**
 * Public MZmine v4.0.8 centroid primitive-array method slice.
 *
 * <p>This class intentionally contains only the exact overload executed by the first differential
 * oracle. The MassSpectrum, GUI parameter, and isotope-below-noise paths are outside the governed
 * source closure.</p>
 */
public final class CentroidMassDetector {

  public double[][] getMassValues(double[] mzs, double[] intensities, double noiseLevel) {
    assert mzs.length == intensities.length;

    final int points = mzs.length;
    final DoubleArrayList pickedMZs = new DoubleArrayList(points);
    final DoubleArrayList pickedIntensities = new DoubleArrayList(points);

    for (int i = 0; i < points; i++) {
      if (intensities[i] >= noiseLevel) {
        pickedMZs.add(mzs[i]);
        pickedIntensities.add(intensities[i]);
      }
    }
    return new double[][]{pickedMZs.toDoubleArray(), pickedIntensities.toDoubleArray()};
  }
}
