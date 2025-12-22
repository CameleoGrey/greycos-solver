package ai.greycos.solver.quarkus.devui;

import java.util.List;

import ai.greycos.solver.core.api.score.constraint.ConstraintRef;

public class GreycosDevUIProperties { // TODO make record?

  private final GreycosModelProperties greycosModelProperties;
  private final String effectiveSolverConfigXML;
  private final List<ConstraintRef> constraintList;

  public GreycosDevUIProperties(
      GreycosModelProperties greycosModelProperties,
      String effectiveSolverConfigXML,
      List<ConstraintRef> constraintList) {
    this.greycosModelProperties = greycosModelProperties;
    this.effectiveSolverConfigXML = effectiveSolverConfigXML;
    this.constraintList = constraintList;
  }

  public GreycosModelProperties getGreycosModelProperties() {
    return greycosModelProperties;
  }

  public String getEffectiveSolverConfig() {
    return effectiveSolverConfigXML;
  }

  public List<ConstraintRef> getConstraintList() {
    return constraintList;
  }
}
