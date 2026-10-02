package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SplittableRandom;

import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.multistage.MultistageDefinition;
import greycos.solver.core.impl.multistage.MultistageMoveRequest;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

/** Selects bounded, indexed requests; evaluation threads prepare their actual operations. */
public final class MultistageMoveSelector<Solution_> extends AbstractMoveSelector<Solution_> {

  private final MultistageDefinition<Solution_> definition;
  private final boolean randomSelection;
  private final int candidateCountLimit;

  private InnerScoreDirector<Solution_, ?> coordinatorDirector;
  private long phaseSeed;
  private long stepSeed;
  private long stepEpoch;
  private long populationSize;
  private long attemptCount;
  private long size;
  private boolean stepActive;

  public MultistageMoveSelector(
      MultistageDefinition<Solution_> definition,
      boolean randomSelection,
      int candidateCountLimit) {
    this.definition = Objects.requireNonNull(definition);
    this.randomSelection = randomSelection;
    if (candidateCountLimit < 1) {
      throw new IllegalArgumentException(
          "The multistage candidateCountLimit (%d) must be positive."
              .formatted(candidateCountLimit));
    }
    this.candidateCountLimit = candidateCountLimit;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    coordinatorDirector = phaseScope.getScoreDirector();
    phaseSeed = workingRandom.nextLong();
    try {
      refreshPopulation();
    } catch (RuntimeException | Error failure) {
      try {
        closeCoordinator();
      } catch (RuntimeException | Error cleanupFailure) {
        if (cleanupFailure != failure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      throw failure;
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    var stepDirector = stepScope.getScoreDirector();
    if (coordinatorDirector != null && coordinatorDirector != stepDirector) {
      closeCoordinator();
    }
    coordinatorDirector = stepDirector;
    refreshPopulation();
    stepSeed = mix(phaseSeed + stepScope.getStepIndex());
    attemptCount = 0;
    stepEpoch++;
    stepActive = true;
  }

  private void refreshPopulation() {
    populationSize = definition.candidateCount(coordinatorDirector);
    if (populationSize < 0) {
      throw new IllegalStateException(
          "The multistage provider for (%s) returned a negative candidateCount (%d)."
              .formatted(definition, populationSize));
    }
    size =
        populationSize == 0
            ? 0
            : randomSelection ? candidateCountLimit : Math.min(populationSize, candidateCountLimit);
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    stepActive = false;
    super.stepEnded(stepScope);
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    closeCoordinator();
    super.phaseEnded(phaseScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      closeCoordinator();
    } finally {
      super.solvingEnded(solverScope);
    }
  }

  private void closeCoordinator() {
    stepActive = false;
    size = 0;
    var director = coordinatorDirector;
    coordinatorDirector = null;
    if (director != null) {
      definition.invalidateEvaluationContexts();
      definition.closeEvaluationContext(director);
    }
  }

  @Override
  public boolean isNeverEnding() {
    return false;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public Iterator<Move<Solution_>> iterator() {
    if (!stepActive) {
      throw new IllegalStateException(
          "The multistage selector cannot select candidates outside an active step.");
    }
    var iteratorEpoch = stepEpoch;
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return stepActive && iteratorEpoch == stepEpoch && attemptCount < size;
      }

      @Override
      public Move<Solution_> next() {
        if (!hasNext()) {
          throw new NoSuchElementException(
              "The multistage candidate attempt budget is exhausted or its step has ended.");
        }
        // The budget belongs to the step, even if a composite creates several iterators.
        var ordinal = attemptCount++;
        var candidateRandom = new SplittableRandom(mix(stepSeed + ordinal));
        var candidateIndex = randomSelection ? candidateRandom.nextLong(populationSize) : ordinal;
        return new MultistageMoveRequest<>(definition, candidateIndex, candidateRandom.nextLong());
      }
    };
  }

  private static long mix(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }

  @Override
  public String toString() {
    return "Multistage(%s)".formatted(definition);
  }
}
