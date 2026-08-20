/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoiMcrProvenanceTest {

  @Test
  void parsesStructuredFeatureComment() {
    final String comment = "ROI-MCR component=2; rank=3; ROI=17; assignment=0.8123; "
        + "restart_stability=0.940000; perturbation_stability=0.830000; "
        + "perturbation_support=0.750000; perturbation_variants=4; "
        + "rank_agreement=0.500000; confidence=stable; original_explained=0.981000";

    final RoiMcrProvenance.Component component = RoiMcrProvenance.parse(comment).orElseThrow();
    assertEquals(2, component.component());
    assertEquals(3, component.rank());
    assertEquals(17, component.roi());
    assertEquals(0.8123, component.assignment(), 1e-12);
    assertEquals(0.83, component.perturbationStability(), 1e-12);
    assertEquals(0.75, component.perturbationSupport(), 1e-12);
    assertEquals("stable", component.confidence());
  }

  @Test
  void rejectsLegacyOrIncompleteComments() {
    assertTrue(RoiMcrProvenance.parse("component=1").isEmpty());
    assertTrue(RoiMcrProvenance.parse("ROI-MCR component=1; rank=2").isEmpty());
  }
}
