package greycos.solver.core.impl.constructionheuristic.placer;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.IterableFilteringValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

public class QueuedValuePlacer<Solution_> extends AbstractEntityPlacer<Solution_>
    implements EntityPlacer<Solution_> {

  protected final IterableValueSelector<Solution_> valueSelector;
  protected final MoveSelector<Solution_> moveSelector;
  private Solution_ workingSolution;

  public QueuedValuePlacer(
      EntityPlacerFactory<Solution_> factory,
      HeuristicConfigPolicy<Solution_> configPolicy,
      IterableValueSelector<Solution_> valueSelector,
      MoveSelector<Solution_> moveSelector) {
    super(factory, configPolicy);
    this.valueSelector = valueSelector;
    this.moveSelector = moveSelector;
    phaseLifecycleSupport.addEventListener(valueSelector);
    phaseLifecycleSupport.addEventListener(moveSelector);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    workingSolution = phaseScope.getWorkingSolution();
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseEnded(phaseScope);
    } finally {
      workingSolution = null;
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      super.solvingEnded(solverScope);
    } finally {
      workingSolution = null;
    }
  }

  @Override
  public Iterator<Placement<Solution_>> iterator() {
    return new QueuedValuePlacingIterator();
  }

  public boolean hasListChangeMoveSelector() {
    return valueSelector.getVariableDescriptor().isListVariable();
  }

  @Override
  public List<MoveSelector<Solution_>> getCandidateMoveSelectors() {
    return List.of(moveSelector);
  }

  private class QueuedValuePlacingIterator extends UpcomingSelectionIterator<Placement<Solution_>> {

    private Iterator<Object> valueIterator;
    private final boolean trackOptionalProgress =
        valueSelector.getVariableDescriptor() instanceof BasicVariableDescriptor<?> descriptor
            && descriptor.allowsUnassigned()
            && !valueSelector.isNeverEnding();
    private long reinitializableCountAtPassStart = -1;

    private long countReinitializableEntities() {
      var descriptor = valueSelector.getVariableDescriptor();
      var count = new long[1];
      descriptor
          .getEntityDescriptor()
          .visitAllEntities(
              workingSolution,
              entity -> {
                if (descriptor.isReinitializable(entity)) {
                  count[0]++;
                }
              });
      return count[0];
    }

    private QueuedValuePlacingIterator() {
      valueIterator = Collections.emptyIterator();
    }

    @Override
    protected Placement<Solution_> createUpcomingSelection() {
      // If all values are used, there can still be entities uninitialized
      if (!valueIterator.hasNext()) {
        if (trackOptionalProgress) {
          var count = countReinitializableEntities();
          if (reinitializableCountAtPassStart >= 0 && count >= reinitializableCountAtPassStart) {
            return noUpcomingSelection();
          }
          reinitializableCountAtPassStart = count;
        }
        valueIterator = valueSelector.iterator();
        if (!valueIterator.hasNext()) {
          return noUpcomingSelection();
        }
      }
      valueIterator.next();
      var moveIterator = moveSelector.iterator();
      // Because the valueSelector is entity independent, there is always a move if there's still an
      // entity
      if (!moveIterator.hasNext()) {
        return noUpcomingSelection();
      }
      return new Placement<>(moveIterator);
    }
  }

  @Override
  public EntityPlacer<Solution_> rebuildWithFilter(SelectionFilter<Solution_, Object> filter) {
    return new QueuedValuePlacer<>(
        factory,
        configPolicy,
        (IterableFilteringValueSelector<Solution_>)
            FilteringValueSelector.of(valueSelector, filter),
        moveSelector);
  }
}
