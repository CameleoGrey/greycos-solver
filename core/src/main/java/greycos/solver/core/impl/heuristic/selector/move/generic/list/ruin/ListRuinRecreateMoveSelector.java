package greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.generic.CountSupplier;
import greycos.solver.core.impl.heuristic.selector.move.generic.GenericMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateMoveSelectorSize;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

final class ListRuinRecreateMoveSelector<Solution_> extends GenericMoveSelector<Solution_> {

  private final IterableValueSelector<Solution_> valueSelector;
  private final ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      constructionHeuristicPhaseBuilder;
  private final CountSupplier minimumSelectedCountSupplier;
  private final CountSupplier maximumSelectedCountSupplier;

  private SolverScope<Solution_> solverScope;
  private ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;

  public ListRuinRecreateMoveSelector(
      IterableValueSelector<Solution_> valueSelector,
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      CountSupplier minimumSelectedCountSupplier,
      CountSupplier maximumSelectedCountSupplier) {
    super();
    this.valueSelector =
        ListChangeMoveSelector.filterPinnedListPlanningVariableValuesWithIndex(
            FilteringValueSelector.ofAssigned(valueSelector, this::getListVariableStateSupply),
            this::getListVariableStateSupply);
    this.listVariableDescriptor = listVariableDescriptor;
    this.constructionHeuristicPhaseBuilder = constructionHeuristicPhaseBuilder;
    this.minimumSelectedCountSupplier = minimumSelectedCountSupplier;
    this.maximumSelectedCountSupplier = maximumSelectedCountSupplier;

    phaseLifecycleSupport.addEventListener(this.valueSelector);
  }

  private ListVariableStateSupply<Solution_, Object, Object> getListVariableStateSupply() {
    return Objects.requireNonNull(
        listVariableStateSupply,
        "Impossible state: The listVariableStateSupply is not initialized yet.");
  }

  @Override
  public long getSize() {
    var valueCount = getEligibleCount();
    return RuinRecreateMoveSelectorSize.count(
        valueCount,
        minimumSelectedCountSupplier.applyAsInt(valueCount),
        maximumSelectedCountSupplier.applyAsInt(valueCount));
  }

  private long getEligibleCount() {
    long count = 0;
    var iterator = valueSelector.endingIterator(null);
    while (iterator.hasNext()) {
      iterator.next();
      count++;
    }
    return count;
  }

  @Override
  public boolean isNeverEnding() {
    return valueSelector.isNeverEnding();
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    this.solverScope = solverScope;
    this.listVariableStateSupply =
        solverScope
            .getScoreDirector()
            .getSupplyManager()
            .demand(listVariableDescriptor.getStateDemand());
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    this.listVariableStateSupply = null;
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    this.solverScope = null;
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    var valueSelectorSize = getEligibleCount();
    if (valueSelectorSize == 0) {
      return Collections.emptyIterator();
    }
    return new ListRuinRecreateMoveIterator<>(
        valueSelector,
        constructionHeuristicPhaseBuilder,
        solverScope,
        listVariableStateSupply,
        RuinRecreateMoveSelectorSize.clampCount(
            minimumSelectedCountSupplier.applyAsInt(valueSelectorSize), valueSelectorSize),
        RuinRecreateMoveSelectorSize.clampCount(
            maximumSelectedCountSupplier.applyAsInt(valueSelectorSize), valueSelectorSize),
        workingRandom);
  }
}
