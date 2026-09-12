package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.Collections;
import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

final class RuinRecreateMoveSelector<Solution_> extends GenericMoveSelector<Solution_> {

  private final EntitySelector<Solution_> entitySelector;
  private final GenuineVariableDescriptor<Solution_> variableDescriptor;
  private final RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      constructionHeuristicPhaseBuilder;
  private final CountSupplier minimumSelectedCountSupplier;
  private final CountSupplier maximumSelectedCountSupplier;

  private SolverScope<Solution_> solverScope;

  public RuinRecreateMoveSelector(
      EntitySelector<Solution_> entitySelector,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      CountSupplier minimumSelectedCountSupplier,
      CountSupplier maximumSelectedCountSupplier) {
    super();
    this.entitySelector = entitySelector;
    this.variableDescriptor = variableDescriptor;
    this.constructionHeuristicPhaseBuilder = constructionHeuristicPhaseBuilder;
    this.minimumSelectedCountSupplier = minimumSelectedCountSupplier;
    this.maximumSelectedCountSupplier = maximumSelectedCountSupplier;

    phaseLifecycleSupport.addEventListener(entitySelector);
  }

  @Override
  public long getSize() {
    var entityCount = getEligibleCount();
    return RuinRecreateMoveSelectorSize.count(
        entityCount,
        minimumSelectedCountSupplier.applyAsInt(entityCount),
        maximumSelectedCountSupplier.applyAsInt(entityCount));
  }

  private long getEligibleCount() {
    long count = 0;
    var iterator = entitySelector.endingIterator();
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }
    return count;
  }

  @Override
  public boolean isNeverEnding() {
    return entitySelector.isNeverEnding();
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    this.solverScope = solverScope;
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    this.solverScope = null;
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    var entitySelectorSize = getEligibleCount();
    if (entitySelectorSize == 0) {
      return Collections.emptyIterator();
    }
    return new RuinRecreateMoveIterator<>(
        entitySelector,
        variableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        RuinRecreateMoveSelectorSize.clampCount(
            minimumSelectedCountSupplier.applyAsInt(entitySelectorSize), entitySelectorSize),
        RuinRecreateMoveSelectorSize.clampCount(
            maximumSelectedCountSupplier.applyAsInt(entitySelectorSize), entitySelectorSize),
        workingRandom);
  }
}
