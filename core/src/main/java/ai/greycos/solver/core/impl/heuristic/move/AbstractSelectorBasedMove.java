package ai.greycos.solver.core.impl.heuristic.move;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.ValueRangeDescriptor;
import ai.greycos.solver.core.impl.move.MoveDirector;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.NullMarked;

/**
 * Abstract superclass for selector-generated moves.
 *
 * <p>This exists to distinguish selector-based legacy moves from neighborhoods-based moves while
 * both styles coexist in the codebase.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public abstract class AbstractSelectorBasedMove<Solution_>
    implements ai.greycos.solver.core.preview.api.move.Move<Solution_> {

  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public final void execute(MutableSolutionView<Solution_> solutionView) {
    var moveDirector = (MoveDirector<Solution_, ?>) solutionView;
    var scoreDirector = moveDirector.getScoreDirector();
    execute((VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector);
    scoreDirector.triggerVariableListeners();
  }

  protected abstract void execute(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector);

  @Override
  public String describe() {
    var description = ((Move<Solution_>) this).getSimpleMoveTypeDescription();
    return description.startsWith("SelectorBased")
        ? description.substring("SelectorBased".length())
        : description;
  }

  protected <Value_> ValueRange<Value_> extractValueRangeFromEntity(
      ScoreDirector<Solution_> scoreDirector,
      ValueRangeDescriptor<Solution_> valueRangeDescriptor,
      Object entity) {
    var castScoreDirector = (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    return castScoreDirector.getValueRangeManager().getFromEntity(valueRangeDescriptor, entity);
  }

  public static <E> List<E> rebaseList(List<E> externalObjectList, Lookup lookup) {
    var rebasedObjectList = new ArrayList<E>(externalObjectList.size());
    for (var object : externalObjectList) {
      rebasedObjectList.add(lookup.lookUpWorkingObject(object));
    }
    return rebasedObjectList;
  }

  public static <E> Set<E> rebaseSet(Set<E> externalObjectSet, Lookup lookup) {
    var rebasedObjectSet = new LinkedHashSet<E>(externalObjectSet.size());
    for (var object : externalObjectSet) {
      rebasedObjectSet.add(lookup.lookUpWorkingObject(object));
    }
    return rebasedObjectSet;
  }
}
