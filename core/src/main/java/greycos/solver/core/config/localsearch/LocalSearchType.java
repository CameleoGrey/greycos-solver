package greycos.solver.core.config.localsearch;

import java.util.Arrays;

import jakarta.xml.bind.annotation.XmlEnum;

import greycos.solver.core.config.solver.PreviewFeature;

import org.jspecify.annotations.NonNull;

@XmlEnum
public enum LocalSearchType {
  HILL_CLIMBING,
  TABU_SEARCH,
  SIMULATED_ANNEALING,
  LATE_ACCEPTANCE,
  /** See {@link PreviewFeature#DIVERSIFIED_LATE_ACCEPTANCE}. */
  DIVERSIFIED_LATE_ACCEPTANCE,
  GREAT_DELUGE,
  VARIABLE_NEIGHBORHOOD_DESCENT,
  /** Requires an explicit {@link GuidedLocalSearchConfig} with a feature provider. */
  GUIDED_LOCAL_SEARCH;

  /**
   * @return values eligible for automatically generated blueprints, excluding types that duplicate
   *     another blueprint, require a feature provider, or require preview opt-in
   */
  public static @NonNull LocalSearchType @NonNull [] getBluePrintTypes() {
    return Arrays.stream(values())
        .filter(
            localSearchType ->
                localSearchType != GUIDED_LOCAL_SEARCH
                    && localSearchType != SIMULATED_ANNEALING
                    && localSearchType != DIVERSIFIED_LATE_ACCEPTANCE)
        .toArray(LocalSearchType[]::new);
  }
}
