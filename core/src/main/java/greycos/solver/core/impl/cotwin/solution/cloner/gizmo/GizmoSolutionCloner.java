package greycos.solver.core.impl.cotwin.solution.cloner.gizmo;

import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

public interface GizmoSolutionCloner<Solution_> extends SolutionCloner<Solution_> {
  void setSolutionDescriptor(SolutionDescriptor<Solution_> descriptor);
}
