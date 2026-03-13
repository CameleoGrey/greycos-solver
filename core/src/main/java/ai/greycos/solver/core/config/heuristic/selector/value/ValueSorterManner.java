package ai.greycos.solver.core.config.heuristic.selector.value;

import jakarta.xml.bind.annotation.XmlEnum;

import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;

/** The manner of sorting values for a {@link PlanningVariable}. */
@XmlEnum
public enum ValueSorterManner {
  NONE(true),
  ASCENDING(false),
  ASCENDING_IF_AVAILABLE(true),
  DESCENDING(false),
  DESCENDING_IF_AVAILABLE(true);

  private final boolean nonePossible;

  ValueSorterManner(boolean nonePossible) {
    this.nonePossible = nonePossible;
  }

  /**
   * @return true if {@link #NONE} is an option, such as when the other option is not available.
   */
  public boolean isNonePossible() {
    return nonePossible;
  }
}
