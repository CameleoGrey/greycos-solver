package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

final class RuinRecreateMoveIterator<Solution_> extends UpcomingSelectionIterator<Move<Solution_>> {

  private final EntitySelector<Solution_> entitySelector;
  private final GenuineVariableDescriptor<Solution_> variableDescriptor;
  private final RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      constructionHeuristicPhaseBuilder;
  private final SolverScope<Solution_> solverScope;
  private final int minimumRuinedCount;
  private final int maximumRuinedCount;
  private final RandomGenerator workingRandom;

  public RuinRecreateMoveIterator(
      EntitySelector<Solution_> entitySelector,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      SolverScope<Solution_> solverScope,
      int minimumRuinedCount,
      int maximumRuinedCount,
      RandomGenerator workingRandom) {
    this.entitySelector = entitySelector;
    this.variableDescriptor = variableDescriptor;
    this.constructionHeuristicPhaseBuilder = constructionHeuristicPhaseBuilder;
    this.solverScope = solverScope;
    this.minimumRuinedCount = minimumRuinedCount;
    this.maximumRuinedCount = maximumRuinedCount;
    this.workingRandom = workingRandom;
  }

  @Override
  protected Move<Solution_> createUpcomingSelection() {
    var entityIterator = entitySelector.iterator();
    var ruinedCount = workingRandom.nextInt(minimumRuinedCount, maximumRuinedCount + 1);
    var selectedEntityList = new ArrayList<>(ruinedCount);
    var affectedValueSet = new LinkedHashSet<Object>(ruinedCount);
    var selectedEntitySet =
        Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>(ruinedCount));
    for (var i = 0; i < ruinedCount; i++) {
      var remainingAttempts = ruinedCount;
      while (true) {
        if (!entityIterator.hasNext()) {
          // Bail out; cannot select enough unique elements.
          return SelectorBasedNoChangeMove.getInstance();
        }
        var selectedEntity = entityIterator.next();
        if (selectedEntitySet.add(selectedEntity)) {
          selectedEntityList.add(selectedEntity);
          var affectedValue = variableDescriptor.getValue(selectedEntity);
          if (affectedValue != null) {
            affectedValueSet.add(affectedValue);
          }
          break;
        } else {
          remainingAttempts--;
        }
        if (remainingAttempts == 0) {
          // Bail out; cannot select enough unique elements.
          return SelectorBasedNoChangeMove.getInstance();
        }
      }
    }
    return new SelectorBasedRuinRecreateMove<>(
        variableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        selectedEntityList,
        affectedValueSet);
  }
}
