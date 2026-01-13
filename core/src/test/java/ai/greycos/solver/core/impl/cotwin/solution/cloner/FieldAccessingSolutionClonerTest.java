package ai.greycos.solver.core.impl.cotwin.solution.cloner;

import ai.greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

class FieldAccessingSolutionClonerTest extends AbstractSolutionClonerTest {

  @Override
  protected <Solution_> SolutionCloner<Solution_> createSolutionCloner(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    return new FieldAccessingSolutionCloner<>(solutionDescriptor);
  }
}
