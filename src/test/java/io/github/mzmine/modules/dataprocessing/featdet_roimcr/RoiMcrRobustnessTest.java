/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoiMcrRobustnessTest {

  @Test
  void classifiesComponentsStableAcrossSmallPerturbations() {
    final List<RoiMcrRobustness.Signature> reference = List.of(
        signature(0, 0.40, 100, new double[]{100, 200, 300},
            new double[]{1000, 600, 200}),
        signature(1, 0.62, 100, new double[]{150, 250, 350},
            new double[]{900, 500, 180}));
    final List<RoiMcrRobustness.Variant> variants = List.of(
        new RoiMcrRobustness.Variant("noise-low", 2, List.of(
            signature(0, 0.41, 100, new double[]{100.002, 200.002, 300.002},
                new double[]{980, 620, 190}),
            signature(1, 0.61, 100, new double[]{150.001, 250.001, 350.001},
                new double[]{910, 480, 190}))),
        new RoiMcrRobustness.Variant("noise-high", 2, List.of(
            signature(0, 0.39, 100, new double[]{99.999, 199.999, 299.999},
                new double[]{1020, 590, 210}),
            signature(1, 0.63, 100, new double[]{150.003, 250.003, 350.003},
                new double[]{880, 520, 175}))),
        new RoiMcrRobustness.Variant("weight-low", 2, List.of(
            signature(0, 0.40, 100, new double[]{100, 200, 300},
                new double[]{995, 605, 205}),
            signature(1, 0.62, 100, new double[]{150, 250, 350},
                new double[]{905, 495, 185}))),
        new RoiMcrRobustness.Variant("weight-high", 2, List.of(
            signature(0, 0.42, 100, new double[]{100.004, 200.004, 300.004},
                new double[]{970, 630, 180}),
            signature(1, 0.60, 100, new double[]{150.004, 250.004, 350.004},
                new double[]{930, 470, 195}))));

    final RoiMcrRobustness.Assessment assessment = RoiMcrRobustness.assess(reference, 2,
        variants, 0.01, 10, 0.70, 0.75);

    assertEquals(1d, assessment.rankAgreement(), 1e-12);
    assertTrue(assessment.overallStability() > 0.90);
    assessment.components().forEach(component -> {
      assertEquals(RoiMcrRobustness.Confidence.STABLE, component.confidence());
      assertEquals(1d, component.supportFraction(), 1e-12);
      assertTrue(component.meanSimilarity() > 0.90);
    });
  }

  @Test
  void marksDisappearingMinorComponentParameterSensitive() {
    final RoiMcrRobustness.Signature dominant = signature(0, 0.40, 100,
        new double[]{100, 200}, new double[]{1000, 500});
    final RoiMcrRobustness.Signature minor = signature(1, 0.64, 100,
        new double[]{150, 250}, new double[]{300, 180});
    final List<RoiMcrRobustness.Variant> variants = List.of(
        new RoiMcrRobustness.Variant("v1", 2, List.of(dominant, minor)),
        new RoiMcrRobustness.Variant("v2", 1, List.of(dominant)),
        new RoiMcrRobustness.Variant("v3", 1, List.of(dominant)),
        RoiMcrRobustness.Variant.failed("v4"));

    final RoiMcrRobustness.Assessment assessment = RoiMcrRobustness.assess(
        List.of(dominant, minor), 2, variants, 0.01, 10, 0.70, 0.75);

    assertEquals(RoiMcrRobustness.Confidence.PARAMETER_SENSITIVE,
        assessment.component(1).confidence());
    assertTrue(assessment.component(1).supportFraction() < 0.5);
    assertTrue(assessment.rankAgreement() < 0.5);
  }

  @Test
  void blankEvidenceUsesShapeSpectrumAndIntensity() {
    final RoiMcrRobustness.Signature sample = signature(0, 0.50, 100,
        new double[]{100, 200, 300}, new double[]{1000, 600, 200});
    final RoiMcrRobustness.Signature strongBlank = signature(0, 0.51, 70,
        new double[]{100.002, 200.002, 300.002}, new double[]{800, 500, 180});
    final RoiMcrRobustness.BlankEvidence associated = RoiMcrRobustness.compareToBlank(sample,
        List.of(strongBlank), 0.01, 10, 0.60);
    assertEquals("blank-associated", associated.classification());
    assertTrue(associated.blankBurden() > 0.5);

    final RoiMcrRobustness.Signature unrelated = signature(0, 0.20, 100,
        new double[]{500, 600}, new double[]{900, 700});
    final RoiMcrRobustness.BlankEvidence unmatched = RoiMcrRobustness.compareToBlank(sample,
        List.of(unrelated), 0.01, 10, 0.60);
    assertEquals("unmatched-needs-review", unmatched.classification());
    assertEquals(1d, unmatched.sampleSpecificity(), 1e-12);
  }

  private static RoiMcrRobustness.Signature signature(int component, double apex,
      double relativeHeight, double[] mz, double[] loadings) {
    final double[] profile = new double[101];
    final double center = apex * 100;
    for (int index = 0; index < profile.length; index++) {
      profile[index] = Math.exp(-0.5 * Math.pow((index - center) / 9d, 2));
    }
    double spectrumSum = 0;
    double spectrumMaximum = 0;
    for (double loading : loadings) {
      spectrumSum += loading;
      spectrumMaximum = Math.max(spectrumMaximum, loading);
    }
    double profileArea = 0;
    for (double value : profile) {
      profileArea += value;
    }
    return new RoiMcrRobustness.Signature(component, apex, profile, mz, loadings,
        relativeHeight * spectrumMaximum, relativeHeight * profileArea * spectrumSum);
  }
}
