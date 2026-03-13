package ai.greycos.solver.quarkus.deployment.api;

import java.util.Set;

public class GeneratedGizmoClasses {
  private final Set<String> generatedGizmoMemberAccessorClassSet;
  private final Set<String> generatedGizmoSolutionClonerClassSet;

  public GeneratedGizmoClasses(
      Set<String> generatedGizmoMemberAccessorClassSet,
      Set<String> generatedGizmoSolutionClonerClassSet) {
    this.generatedGizmoMemberAccessorClassSet = generatedGizmoMemberAccessorClassSet;
    this.generatedGizmoSolutionClonerClassSet = generatedGizmoSolutionClonerClassSet;
  }

  public Set<String> getGeneratedGizmoMemberAccessorClassSet() {
    return generatedGizmoMemberAccessorClassSet;
  }

  public Set<String> getGeneratedGizmoSolutionClonerClassSet() {
    return generatedGizmoSolutionClonerClassSet;
  }
}
