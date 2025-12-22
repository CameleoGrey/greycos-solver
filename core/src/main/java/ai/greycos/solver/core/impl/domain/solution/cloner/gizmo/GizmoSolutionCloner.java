package ai.greycos.solver.core.impl.domain.solution.cloner.gizmo;

import ai.greycos.solver.core.api.domain.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;

public interface GizmoSolutionCloner<Solution_> extends SolutionCloner<Solution_> {
  void setSolutionDescriptor(SolutionDescriptor<Solution_> descriptor);
}
