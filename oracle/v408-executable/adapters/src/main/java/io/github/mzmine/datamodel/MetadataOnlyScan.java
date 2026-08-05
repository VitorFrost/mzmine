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
package io.github.mzmine.datamodel;

import com.google.common.collect.Range;
import io.github.mzmine.datamodel.msms.MsMsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Focused metadata contract implemented by the frozen public {@code BuildingMzMLMsScan} record.
 *
 * <p>This is not the application {@code Scan} hierarchy. It declares only the methods overridden by
 * the parser record and therefore introduces no mass-list, feature-list, project, JavaFX, or GUI
 * behavior.</p>
 */
public abstract class MetadataOnlyScan {

  public abstract int getNumberOfDataPoints();

  public abstract @Nullable Double getTIC();

  public abstract double[] getMzValues(@NotNull double[] dst);

  public abstract double[] getIntensityValues(@NotNull double[] dst);

  public abstract @NotNull MassSpectrumType getSpectrumType();

  public abstract @NotNull Range<Double> getScanningMZRange();

  public abstract @NotNull Range<Double> getDataPointMZRange();

  public abstract @Nullable RawDataFile getDataFile();

  public abstract int getScanNumber();

  public abstract String getScanDefinition();

  public abstract int getMSLevel();

  public abstract float getRetentionTime();

  public abstract @Nullable Float getInjectionTime();

  public abstract @Nullable MsMsInfo getMsMsInfo();
}
