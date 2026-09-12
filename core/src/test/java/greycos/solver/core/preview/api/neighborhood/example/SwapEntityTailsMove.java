package greycos.solver.core.preview.api.neighborhood.example;

import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.jspecify.annotations.NullMarked;

/**
 * Swaps every value at index &gt;= {@code cutIndex} between two entities' list variables - a
 * VRP-flavoured "swap two routes' tails" move.
 */
@NullMarked
record SwapEntityTailsMove(
    PlanningListVariableMetaModel<TestdataListSolution, TestdataListEntity, TestdataListValue>
        variableMetaModel,
    TestdataListEntity leftEntity,
    TestdataListEntity rightEntity,
    int cutIndex)
    implements Move<TestdataListSolution> {

  @Override
  public void execute(MutableSolutionView<TestdataListSolution> solutionView) {
    var overlap =
        Math.min(
            solutionView.countValues(variableMetaModel, leftEntity),
            solutionView.countValues(variableMetaModel, rightEntity));
    for (var i = cutIndex; i < overlap; i++) {
      solutionView.swapValuesBetweenLists(variableMetaModel, leftEntity, i, rightEntity, i);
    }
  }

  @Override
  public String describe() {
    return "SwapEntityTails(%s, %s, cut=%d)".formatted(leftEntity, rightEntity, cutIndex);
  }
}
