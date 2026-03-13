package ai.greycos.solver.core.preview.api.move.builtin;

import java.util.Objects;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.SolutionView;
import ai.greycos.solver.core.preview.api.neighborhood.MoveProvider;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStream;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStreamFactory;
import ai.greycos.solver.core.preview.api.neighborhood.stream.function.BiNeighborhoodsPredicate;
import ai.greycos.solver.core.preview.api.neighborhood.stream.joiner.NeighborhoodsJoiners;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class ListSwapMoveProvider<Solution_, Entity_, Value_> implements MoveProvider<Solution_> {

  private final PlanningListVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel;

  public ListSwapMoveProvider(
      PlanningListVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel) {
    this.variableMetaModel = Objects.requireNonNull(variableMetaModel);
  }

  @Override
  public MoveStream<Solution_> build(MoveStreamFactory<Solution_> moveStreamFactory) {
    var assignedValueStream =
        moveStreamFactory
            .forEach(variableMetaModel.type(), false)
            .filter(
                (solutionView, value) ->
                    solutionView.getPositionOf(variableMetaModel, value) instanceof PositionInList)
            .map(
                (solutionView, value) ->
                    new FullElementPosition<>(
                        value,
                        solutionView.getPositionOf(variableMetaModel, value).ensureAssigned()));
    var predicate =
        (BiNeighborhoodsPredicate<
                Solution_, FullElementPosition<Value_>, FullElementPosition<Value_>>)
            this::isValidSwap;
    return moveStreamFactory
        .pick(assignedValueStream)
        .pick(assignedValueStream, NeighborhoodsJoiners.filtering(predicate))
        .asMove(this::buildMove);
  }

  private Move<Solution_> buildMove(
      SolutionView<Solution_> solutionView,
      FullElementPosition<Value_> a,
      FullElementPosition<Value_> b) {
    return Moves.swap(variableMetaModel, a.elementPosition, b.elementPosition);
  }

  private boolean isValidSwap(
      SolutionView<Solution_> solutionView,
      FullElementPosition<Value_> leftPosition,
      FullElementPosition<Value_> rightPosition) {
    if (Objects.equals(leftPosition, rightPosition)) {
      return false;
    }
    return solutionView.isValueInRange(
            variableMetaModel, rightPosition.entity(), leftPosition.value())
        && solutionView.isValueInRange(
            variableMetaModel, leftPosition.entity(), rightPosition.value());
  }

  @NullMarked
  private record FullElementPosition<Value_>(Value_ value, PositionInList elementPosition) {

    public <Entity_> Entity_ entity() {
      return elementPosition.entity();
    }

    @Override
    public String toString() {
      return value + "@" + elementPosition;
    }
  }
}
