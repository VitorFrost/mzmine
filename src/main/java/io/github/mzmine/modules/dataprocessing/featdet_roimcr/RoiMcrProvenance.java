/*
 * Copyright (c) 2026 Vitor Marchesan and contributors
 * SPDX-License-Identifier: MIT
 */
package io.github.mzmine.modules.dataprocessing.featdet_roimcr;

import io.github.mzmine.datamodel.features.FeatureList;
import io.github.mzmine.datamodel.features.FeatureListRow;
import io.github.mzmine.datamodel.features.types.annotations.CommentType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Parses and summarizes the structured provenance written to ROI-MCR feature rows. */
final class RoiMcrProvenance {

  private RoiMcrProvenance() {
  }

  record Component(int component, int rank, int roi, double assignment,
                   double restartStability, double perturbationStability,
                   double perturbationSupport, int perturbationVariants,
                   double rankAgreement, String confidence, double originalExplained) {
  }

  record Summary(int componentCount, Map<String, Integer> confidenceCounts,
                 double meanRestartStability, double meanPerturbationStability,
                 double meanPerturbationSupport, double meanRankAgreement,
                 int minimumPerturbationVariants, int maximumPerturbationVariants) {

    Summary {
      confidenceCounts = Map.copyOf(confidenceCounts);
    }

    Map<String, Object> toMap() {
      final Map<String, Object> map = new LinkedHashMap<>();
      map.put("component_count", componentCount);
      map.put("confidence_counts", confidenceCounts);
      map.put("mean_restart_stability", meanRestartStability);
      map.put("mean_perturbation_stability", meanPerturbationStability);
      map.put("mean_perturbation_support", meanPerturbationSupport);
      map.put("mean_rank_agreement", meanRankAgreement);
      map.put("minimum_perturbation_variants", minimumPerturbationVariants);
      map.put("maximum_perturbation_variants", maximumPerturbationVariants);
      return map;
    }
  }

  static Optional<Component> parse(String comment) {
    if (comment == null || !comment.startsWith("ROI-MCR ")) {
      return Optional.empty();
    }
    final Map<String, String> values = new LinkedHashMap<>();
    for (String field : comment.substring("ROI-MCR ".length()).split(";")) {
      final int separator = field.indexOf('=');
      if (separator > 0) {
        values.put(field.substring(0, separator).trim(), field.substring(separator + 1).trim());
      }
    }
    try {
      return Optional.of(new Component(integer(values, "component"), integer(values, "rank"),
          integer(values, "ROI"), decimal(values, "assignment"),
          decimal(values, "restart_stability"), decimal(values, "perturbation_stability"),
          decimal(values, "perturbation_support"), integer(values, "perturbation_variants"),
          decimal(values, "rank_agreement"), required(values, "confidence"),
          decimal(values, "original_explained")));
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }

  static Summary summarize(FeatureList featureList) {
    final Map<Integer, Component> components = new TreeMap<>();
    if (featureList != null) {
      for (FeatureListRow row : featureList.getRows()) {
        parse(row.get(CommentType.class)).ifPresent(component ->
            components.putIfAbsent(component.component(), component));
      }
    }
    if (components.isEmpty()) {
      return new Summary(0, Map.of(), 0, 0, 0, 0, 0, 0);
    }

    final Map<String, Integer> confidenceCounts = new TreeMap<>();
    double restart = 0;
    double perturbation = 0;
    double support = 0;
    double rankAgreement = 0;
    int minimumVariants = Integer.MAX_VALUE;
    int maximumVariants = 0;
    for (Component component : components.values()) {
      confidenceCounts.merge(component.confidence().toLowerCase(Locale.ROOT), 1, Integer::sum);
      restart += component.restartStability();
      perturbation += component.perturbationStability();
      support += component.perturbationSupport();
      rankAgreement += component.rankAgreement();
      minimumVariants = Math.min(minimumVariants, component.perturbationVariants());
      maximumVariants = Math.max(maximumVariants, component.perturbationVariants());
    }
    final double count = components.size();
    return new Summary(components.size(), confidenceCounts, restart / count,
        perturbation / count, support / count, rankAgreement / count, minimumVariants,
        maximumVariants);
  }

  private static String required(Map<String, String> values, String key) {
    final String value = values.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing ROI-MCR provenance field " + key);
    }
    return value;
  }

  private static int integer(Map<String, String> values, String key) {
    return Integer.parseInt(required(values, key));
  }

  private static double decimal(Map<String, String> values, String key) {
    return Double.parseDouble(required(values, key));
  }
}
