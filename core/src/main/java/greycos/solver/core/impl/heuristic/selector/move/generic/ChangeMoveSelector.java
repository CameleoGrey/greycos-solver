package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.IterableSelector;
import greycos.solver.core.impl.heuristic.selector.common.iterator.AbstractOriginalChangeIterator;
import greycos.solver.core.impl.heuristic.selector.common.iterator.AbstractRandomChangeIterator;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

public class ChangeMoveSelector<Solution_> extends AbstractGenericMoveSelector<Solution_> {

  protected final EntitySelector<Solution_> entitySelector;
  protected final ValueSelector<Solution_> valueSelector;
  protected final boolean randomSelection;
  private final boolean reinitializeVariableFiltering;
  private AbstractPhaseScope<Solution_> phaseScope;

  public ChangeMoveSelector(
      EntitySelector<Solution_> entitySelector,
      ValueSelector<Solution_> valueSelector,
      boolean randomSelection) {
    this(entitySelector, valueSelector, randomSelection, false);
  }

  public ChangeMoveSelector(
      EntitySelector<Solution_> entitySelector,
      ValueSelector<Solution_> valueSelector,
      boolean randomSelection,
      boolean reinitializeVariableFiltering) {
    this.reinitializeVariableFiltering = reinitializeVariableFiltering;
    this.entitySelector = entitySelector;
    this.valueSelector = valueSelector;
    this.randomSelection = randomSelection;
    phaseLifecycleSupport.addEventListener(entitySelector);
    phaseLifecycleSupport.addEventListener(valueSelector);
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    this.phaseScope = phaseScope;
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseEnded(phaseScope);
    } finally {
      this.phaseScope = null;
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      super.solvingEnded(solverScope);
    } finally {
      phaseScope = null;
    }
  }

  private boolean isTerminated() {
    return Thread.currentThread().isInterrupted()
        || phaseScope != null
            && phaseScope.getTermination() != null
            && phaseScope.getTermination().isPhaseTerminated(phaseScope);
  }

  private boolean noReinitializableEntitiesRemain() {
    if (!reinitializeVariableFiltering || phaseScope == null) {
      return false;
    }
    var descriptor = valueSelector.getVariableDescriptor();
    var solutionDescriptor = phaseScope.getScoreDirector().getSolutionDescriptor();
    var workingSolution = phaseScope.getWorkingSolution();
    var hasReinitializableEntity = new boolean[1];
    // This is a finite superset, not the configured selection stream. Reading it neither changes
    // mimic recordings nor consumes randomness. A positive result is deliberately inconclusive
    // for filtered/nearby sources; only an empty superset proves exhaustion.
    phaseScope
        .getScoreDirector()
        .getSolutionDescriptor()
        .visitEntitiesByEntityClass(
            phaseScope.getWorkingSolution(),
            entitySelector.getEntityDescriptor().getEntityClass(),
            entity -> {
              if (isTerminated()) {
                hasReinitializableEntity[0] = true;
                return true;
              }
              if (descriptor.getEntityDescriptor().getEntityClass().isInstance(entity)
                  && solutionDescriptor
                      .findEntityDescriptorOrFail(entity.getClass())
                      .isMovable(workingSolution, entity)
                  && descriptor.isReinitializable(entity)) {
                hasReinitializableEntity[0] = true;
                return true;
              }
              return false;
            });
    return !hasReinitializableEntity[0];
  }

  @Override
  public boolean supportsPhaseAndSolverCaching() {
    return true;
  }

  public EntitySelector<Solution_> getEntitySelector() {
    return entitySelector;
  }

  public ValueSelector<Solution_> getValueSelector() {
    return valueSelector;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public boolean isNeverEnding() {
    return randomSelection || entitySelector.isNeverEnding() || valueSelector.isNeverEnding();
  }

  @Override
  public long getSize() {
    if (valueSelector instanceof IterableSelector) {
      return entitySelector.getSize() * ((IterableSelector<Solution_, ?>) valueSelector).getSize();
    } else {
      long size = 0;
      for (Iterator<?> it = entitySelector.endingIterator(); it.hasNext(); ) {
        Object entity = it.next();
        size += valueSelector.getSize(entity);
      }
      return size;
    }
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    final GenuineVariableDescriptor<Solution_> variableDescriptor =
        valueSelector.getVariableDescriptor();
    if (!randomSelection) {
      return new AbstractOriginalChangeIterator<>(entitySelector, valueSelector) {
        @Override
        protected Move<Solution_> newChangeSelection(Object entity, Object toValue) {
          return new SelectorBasedChangeMove<>(variableDescriptor, entity, toValue);
        }
      };
    } else {
      return new AbstractRandomChangeIterator<>(
          entitySelector,
          valueSelector,
          this::noReinitializableEntitiesRemain,
          this::isTerminated) {
        @Override
        protected Move<Solution_> newChangeSelection(Object entity, Object toValue) {
          return new SelectorBasedChangeMove<>(variableDescriptor, entity, toValue);
        }
      };
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + entitySelector + ", " + valueSelector + ")";
  }
}
