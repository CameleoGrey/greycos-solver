package ai.greycos.solver.core.config.heuristic.selector.entity;

import jakarta.xml.bind.annotation.XmlEnum;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;

/** The manner of sorting {@link PlanningEntity} instances. */
@XmlEnum
public enum EntitySorterManner {
  NONE,
  DESCENDING,
  DESCENDING_IF_AVAILABLE
}
