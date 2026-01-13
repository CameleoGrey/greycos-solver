package ai.greycos.solver.core.config.heuristic.selector.entity;

import jakarta.xml.bind.annotation.XmlEnum;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

/** The manner of sorting {@link PlanningEntity} instances. */
@XmlEnum
public enum EntitySorterManner {
  NONE,
  /**
   * @deprecated use {@link #DESCENDING} instead
   */
  @Deprecated(forRemoval = true, since = "1.28.0")
  DECREASING_DIFFICULTY,
  /**
   * @deprecated use {@link #DESCENDING_IF_AVAILABLE} instead
   */
  @Deprecated(forRemoval = true, since = "1.28.0")
  DECREASING_DIFFICULTY_IF_AVAILABLE,
  DESCENDING,
  DESCENDING_IF_AVAILABLE
}
