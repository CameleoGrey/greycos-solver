package ai.greycos.solver.core.impl.cotwin.solution.cloner.gizmo;

import ai.greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

public interface GizmoSolutionCloner<Solution_> extends SolutionCloner<Solution_> {
  void setSolutionDescriptor(SolutionDescriptor<Solution_> descriptor);
}
