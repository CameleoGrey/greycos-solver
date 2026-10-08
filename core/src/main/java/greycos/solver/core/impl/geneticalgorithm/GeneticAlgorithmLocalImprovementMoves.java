package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Run-local bounded-probe neighborhoods. Basic changes sample the recipient's existing range
 * directly, avoiding solution-wide materialization of large entity-provided ranges. Native list
 * selectors retain their normal range, pinning and assignment semantics.
 */
@NullMarked
public final class GeneticAlgorithmLocalImprovementMoves<Solution_>
    implements PhaseLifecycleListener<Solution_> {

  private final List<MoveSelector<Solution_>> configuredListSelectors;
  private List<MoveSelector<Solution_>> listSelectors = List.of();
  private List<GeneticAlgorithmSlot<Solution_>> movableSlots = List.of();
  private final List<Iterator<Move<Solution_>>> listIterators = new ArrayList<>();
  private final List<Integer> availableFamilies = new ArrayList<>();
  private boolean iteratorsInitialized;
  private int solvingStartedCount;
  private int phaseStartedCount;
  private int stepStartedCount;

  public GeneticAlgorithmLocalImprovementMoves(List<MoveSelector<Solution_>> listSelectors) {
    configuredListSelectors = List.copyOf(listSelectors);
  }

  /** Called once per run, before forwarding solving and phase lifecycle events. */
  public void initialize(GeneticAlgorithmWorkspace<Solution_, ?> workspace) {
    movableSlots = workspace.slots().stream().filter(GeneticAlgorithmSlot::movable).toList();
    var listModel = workspace.listModel();
    boolean movableListOwner = false;
    if (listModel != null && listModel.movableValueIds().length > 0) {
      for (int owner = 0; owner < listModel.ownerCount(); owner++) {
        if (listModel.ownerMovable(owner)) {
          movableListOwner = true;
          break;
        }
      }
    }
    listSelectors = movableListOwner ? configuredListSelectors : List.of();
    reset();
  }

  /**
   * Returns one selected probe, including no-ops, or null when every family is exhausted. Sampling
   * is uniform over the available families; basic changes are uniform over movable slots.
   */
  public @Nullable Move<Solution_> nextMove(RandomGenerator random) {
    if (!iteratorsInitialized) {
      if (!movableSlots.isEmpty()) {
        availableFamilies.add(-1);
      }
      for (var selector : listSelectors) {
        var iterator = selector.iterator();
        int index = listIterators.size();
        listIterators.add(iterator);
        if (iterator.hasNext()) {
          availableFamilies.add(index);
        }
      }
      iteratorsInitialized = true;
    }
    while (!availableFamilies.isEmpty()) {
      int selected = random.nextInt(availableFamilies.size());
      int family = availableFamilies.get(selected);
      if (family == -1) {
        var slot = movableSlots.get(random.nextInt(movableSlots.size()));
        var value = slot.valueRange().get(random.nextLong(slot.valueRange().getSize()));
        return new SelectorBasedChangeMove<>(slot.variableDescriptor(), slot.entity(), value);
      }
      var iterator = listIterators.get(family);
      if (iterator.hasNext()) {
        return iterator.next();
      }
      availableFamilies.remove(selected);
    }
    return null;
  }

  /** Discard selections captured before a child materialization or accepted improvement. */
  public void reset() {
    listIterators.clear();
    availableFamilies.clear();
    iteratorsInitialized = false;
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    for (var selector : listSelectors) {
      solvingStartedCount++;
      selector.solvingStarted(solverScope);
    }
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    for (var selector : listSelectors) {
      phaseStartedCount++;
      selector.phaseStarted(phaseScope);
    }
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    reset();
    for (var selector : listSelectors) {
      stepStartedCount++;
      selector.stepStarted(stepScope);
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    int count = stepStartedCount;
    stepStartedCount = 0;
    reset();
    endSelectors(count, selector -> selector.stepEnded(stepScope));
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    int count = phaseStartedCount;
    phaseStartedCount = 0;
    reset();
    movableSlots = List.of();
    endSelectors(count, selector -> selector.phaseEnded(phaseScope));
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    int count = solvingStartedCount;
    solvingStartedCount = 0;
    try {
      endSelectors(count, selector -> selector.solvingEnded(solverScope));
    } finally {
      listSelectors = List.of();
    }
  }

  private void endSelectors(int count, Consumer<MoveSelector<Solution_>> action) {
    Throwable failure = null;
    for (int i = 0; i < count; i++) {
      try {
        action.accept(listSelectors.get(i));
      } catch (RuntimeException | Error cleanupFailure) {
        if (failure == null) {
          failure = cleanupFailure;
        } else if (failure != cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
    }
    if (failure instanceof RuntimeException exception) {
      throw exception;
    }
    if (failure instanceof Error error) {
      throw error;
    }
  }
}
