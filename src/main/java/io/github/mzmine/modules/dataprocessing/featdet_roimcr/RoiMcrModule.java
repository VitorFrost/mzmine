/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.modules.MZmineModuleCategory;
import io.github.mzmine.modules.MZmineProcessingModule;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.Task;
import io.github.mzmine.util.ExitCode;
import io.github.mzmine.util.MemoryMapStorage;
import java.time.Instant;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/** Direct ROI-MCR feature detection from scan mass lists. */
public final class RoiMcrModule implements MZmineProcessingModule {

  @Override
  public @NotNull String getName() {
    return "ROI-MCR LC-MS feature detection (experimental)";
  }

  @Override
  public @NotNull String getDescription() {
    return "Tracks m/z regions of interest directly across scans, resolves local mixtures with "
        + "constrained MCR-ALS, and creates ion features without a prior chromatogram resolver.";
  }

  @Override
  public @NotNull ExitCode runModule(@NotNull MZmineProject project,
      @NotNull ParameterSet parameters, @NotNull Collection<Task> tasks,
      @NotNull Instant moduleCallDate) {
    final MemoryMapStorage storage = MemoryMapStorage.forFeatureList();
    final RawDataFile[] files = parameters.getValue(RoiMcrParameters.dataFiles)
        .getMatchingRawDataFiles();
    for (RawDataFile file : files) {
      tasks.add(new RoiMcrTask(project, file, parameters.cloneParameterSet(true), storage,
          moduleCallDate));
    }
    return ExitCode.OK;
  }

  @Override
  public @NotNull MZmineModuleCategory getModuleCategory() {
    return MZmineModuleCategory.EIC_DETECTION;
  }

  @Override
  public @NotNull Class<? extends ParameterSet> getParameterSetClass() {
    return RoiMcrParameters.class;
  }
}
