package ai.greycos.solver.quarkus.devui;

import java.util.List;

import ai.greycos.solver.core.api.score.constraint.ConstraintRef;

public class GreyCOSDevUIProperties { // TODO make record?

  private final GreyCOSModelProperties greycosModelProperties;
  private final String effectiveSolverConfigXML;
  private final List<ConstraintRef> constraintList;

  public GreyCOSDevUIProperties(
      GreyCOSModelProperties greycosModelProperties,
      String effectiveSolverConfigXML,
      List<ConstraintRef> constraintList) {
    this.greycosModelProperties = greycosModelProperties;
    this.effectiveSolverConfigXML = effectiveSolverConfigXML;
    this.constraintList = constraintList;
  }

  public GreyCOSModelProperties getGreyCOSModelProperties() {
    return greycosModelProperties;
  }

  public String getEffectiveSolverConfig() {
    return effectiveSolverConfigXML;
  }

  public List<ConstraintRef> getConstraintList() {
    return constraintList;
  }
}
