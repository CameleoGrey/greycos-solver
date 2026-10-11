package greycos.solver.math.impl.cotwin;

import java.util.ArrayList;

import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;

import org.jspecify.annotations.NullMarked;

/** Copies assignments and their container while sharing immutable model definitions. */
@NullMarked
public final class MathSolutionCloner implements SolutionCloner<MathSolution> {

  @Override
  public MathSolution cloneSolution(MathSolution original) {
    var decisions = new ArrayList<MathDecision>(original.getDecisions().size());
    for (var decision : original.getDecisions()) {
      decisions.add(decision.copy());
    }
    return new MathSolution(original.compiledModel(), decisions, original.getScore());
  }
}
