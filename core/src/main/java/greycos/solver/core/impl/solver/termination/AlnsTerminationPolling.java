package greycos.solver.core.impl.solver.termination;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Coordinator-owned ALNS polling that samples known time predicates every 32 consumed probes, or
 * every 32 checks without a consumed probe. The original termination graph still owns all lifecycle
 * state. Unknown or stateful predicates keep the entire graph on its original polling path.
 */
public final class AlnsTerminationPolling<Solution_> {

  private static final int PROBES_PER_TIME_POLL = 32;

  private final AbstractPhaseScope<Solution_> phaseScope;
  private final SolverScope<Solution_> solverScope;
  private final boolean supported;
  private final BooleanSupplier predicate;
  private long generation;
  private int consumedSinceRefresh;
  private int checksWithoutConsumedProbe;

  public AlnsTerminationPolling(
      AbstractPhaseScope<Solution_> phaseScope, PhaseTermination<Solution_> termination) {
    this.phaseScope = Objects.requireNonNull(phaseScope);
    solverScope = phaseScope.getSolverScope();
    Objects.requireNonNull(termination);
    supported = isSupported(termination);
    predicate =
        supported && !Boolean.getBoolean("greycos.solver.alns.strictTimePolling")
            ? compile(termination, true, new IdentityHashMap<>(), new IdentityHashMap<>())
            : () -> termination.isPhaseTerminated(phaseScope);
  }

  /** Checks all live predicates, retaining the existing yielding checkpoint. */
  public boolean checkProbe() {
    solverScope.checkYielding();
    if (solverScope.isYieldingEnabled() || ++checksWithoutConsumedProbe >= PROBES_PER_TIME_POLL) {
      // Yielding and custom operator loops may make progress without consuming a probe.
      invalidate();
    }
    return predicate.getAsBoolean();
  }

  /**
   * Advances only for logical probes consumed by the coordinator, never speculative worker work.
   */
  public void logicalProbeConsumed() {
    checksWithoutConsumedProbe = 0;
    if (++consumedSinceRefresh == PROBES_PER_TIME_POLL) {
      invalidate();
    }
  }

  /** Refreshes time predicates at state boundaries and during waits without completed probes. */
  public boolean checkNow() {
    invalidate();
    return checkProbe();
  }

  /** Invalidates time observations without adding a termination checkpoint or an RNG boundary. */
  public void invalidate() {
    generation++;
    consumedSinceRefresh = 0;
    checksWithoutConsumedProbe = 0;
  }

  /**
   * Whether this graph contains only recognized framework predicates, independent of diagnostics.
   */
  public boolean supportedForRepairAttempts() {
    return supported;
  }

  private boolean isSupported(Termination<Solution_> termination) {
    if (termination instanceof IslandSequenceTermination<Solution_> island) {
      return island.supportedForRepairAttempts();
    }
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      return composite.terminationList.stream().allMatch(this::isSupported);
    }
    if (termination instanceof SolverBridgePhaseTermination<Solution_> bridge) {
      return isSupported(bridge.solverTermination);
    }
    if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      return isSupported(
          ((PhaseToSolverTerminationBridge<Solution_>) termination).getSolverTermination());
    }
    return isTimePredicate(termination)
        || termination instanceof BasicPlumbingTermination
        || termination instanceof ChildThreadPlumbingTermination
        || termination instanceof BestScoreTermination
        || termination instanceof BestScoreFeasibleTermination
        || termination instanceof ScoreCalculationCountTermination
        || termination instanceof MoveCountTermination
        || termination instanceof StepCountTermination
        || termination instanceof UnimprovedStepCountTermination;
  }

  private static boolean isTimePredicate(Termination<?> termination) {
    return termination instanceof TimeMillisSpentTermination
        || termination instanceof UnimprovedTimeMillisSpentTermination
        || termination instanceof UnimprovedTimeMillisSpentScoreDifferenceThresholdTermination;
  }

  private BooleanSupplier compile(
      Termination<Solution_> termination,
      boolean phase,
      Map<Termination<Solution_>, BooleanSupplier> phaseTimePredicates,
      Map<Termination<Solution_>, BooleanSupplier> solverTimePredicates) {
    if (termination instanceof IslandSequenceTermination<Solution_> island) {
      // The bridge owns its enclosing sequence/global scopes. Unwrapping it against the current
      // inner ALNS scope would reset work/time origins and change nested AND/OR semantics.
      return () -> island.isSolverTerminated(solverScope);
    }
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var compiledChildren = new ArrayList<BooleanSupplier>();
      var terminations = phase ? composite.phaseTerminationList : composite.solverTerminationList;
      for (var child : terminations) {
        if (!phase || ((PhaseTermination<Solution_>) child).isApplicableTo(phaseScope.getClass())) {
          compiledChildren.add(compile(child, phase, phaseTimePredicates, solverTimePredicates));
        }
      }
      var children = compiledChildren.toArray(BooleanSupplier[]::new);
      if (composite instanceof OrCompositeTermination) {
        return () -> {
          for (var child : children) {
            if (child.getAsBoolean()) return true;
          }
          return false;
        };
      }
      return () -> {
        for (var child : children) {
          if (!child.getAsBoolean()) return false;
        }
        return true;
      };
    }
    if (termination instanceof SolverBridgePhaseTermination<Solution_> bridge) {
      var solverPredicate =
          compile(bridge.solverTermination, false, phaseTimePredicates, solverTimePredicates);
      if (phase && bridge.solverTermination instanceof PhaseTermination) {
        var phasePredicate =
            compile(bridge.solverTermination, true, phaseTimePredicates, solverTimePredicates);
        return () -> solverPredicate.getAsBoolean() || phasePredicate.getAsBoolean();
      }
      return solverPredicate;
    }
    if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      return compile(
          ((PhaseToSolverTerminationBridge<Solution_>) termination).getSolverTermination(),
          false,
          phaseTimePredicates,
          solverTimePredicates);
    }
    BooleanSupplier original =
        phase
            ? () -> termination.isPhaseTerminated(phaseScope)
            : () -> termination.isSolverTerminated(solverScope);
    if (!isTimePredicate(termination)) return original;
    var cache = phase ? phaseTimePredicates : solverTimePredicates;
    return cache.computeIfAbsent(termination, ignored -> new CachedTimePredicate(original));
  }

  private final class CachedTimePredicate implements BooleanSupplier {
    private final BooleanSupplier original;
    private long observedGeneration = -1;
    private boolean result;

    private CachedTimePredicate(BooleanSupplier original) {
      this.original = original;
    }

    @Override
    public boolean getAsBoolean() {
      if (observedGeneration != generation) {
        result = original.getAsBoolean();
        observedGeneration = generation;
      }
      return result;
    }
  }
}
