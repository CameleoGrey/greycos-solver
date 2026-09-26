package greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin;

import java.util.Collections;
import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.generic.CountSupplier;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateMoveSelectorSize;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.AbstractGenericListMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NonNull;

final class ListRuinRecreateMoveSelector<Solution_>
    extends AbstractGenericListMoveSelector<Solution_> {

  private final IterableValueSelector<Solution_> valueSelector;
  private final RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      constructionHeuristicPhaseBuilder;
  private final CountSupplier minimumSelectedCountSupplier;
  private final CountSupplier maximumSelectedCountSupplier;

  private SolverScope<Solution_> solverScope;

  public ListRuinRecreateMoveSelector(
      IterableValueSelector<Solution_> valueSelector,
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      CountSupplier minimumSelectedCountSupplier,
      CountSupplier maximumSelectedCountSupplier) {
    super(listVariableDescriptor);
    this.valueSelector =
        ListChangeMoveSelector.filterPinnedListPlanningVariableValuesWithIndex(
            FilteringValueSelector.ofAssigned(valueSelector, this::getListVariableState),
            this::getListVariableState);
    this.constructionHeuristicPhaseBuilder = constructionHeuristicPhaseBuilder;
    this.minimumSelectedCountSupplier = minimumSelectedCountSupplier;
    this.maximumSelectedCountSupplier = maximumSelectedCountSupplier;

    phaseLifecycleSupport.addEventListener(this.valueSelector);
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
  public void phaseStarted(@NonNull AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    this.solverScope = phaseScope.getSolverScope();
  }

  @Override
  public void phaseEnded(@NonNull AbstractPhaseScope<Solution_> phaseScope) {
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
        listVariableState,
        RuinRecreateMoveSelectorSize.clampCount(
            minimumSelectedCountSupplier.applyAsInt(valueSelectorSize), valueSelectorSize),
        RuinRecreateMoveSelectorSize.clampCount(
            maximumSelectedCountSupplier.applyAsInt(valueSelectorSize), valueSelectorSize),
        workingRandom);
  }
}
