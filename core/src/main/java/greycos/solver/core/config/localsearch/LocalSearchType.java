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
  VARIABLE_NEIGHBORHOOD_DESCENT;

  /**
   * @return values eligible for automatically generated blueprints, excluding types that duplicate
   *     another blueprint or require preview opt-in
   */
  public static @NonNull LocalSearchType @NonNull [] getBluePrintTypes() {
    return Arrays.stream(values())
        .filter(
            localSearchType ->
                localSearchType != SIMULATED_ANNEALING
                    && localSearchType != DIVERSIFIED_LATE_ACCEPTANCE)
        .toArray(LocalSearchType[]::new);
  }
}
