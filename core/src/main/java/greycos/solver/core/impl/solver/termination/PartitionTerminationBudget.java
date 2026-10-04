package greycos.solver.core.impl.solver.termination;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.Nullable;

/**
 * Binds an enclosing partition phase's termination tree to independent child solvers. Only elapsed
 * time, absolute best scores and cancellation are observed on the parent. Every other leaf owns its
 * usual child-local counters and lifecycle state.
 *
 * <p>The parent calls {@link #refresh()} before starting children, after merging an improvement,
 * and at least every 10 ms while waiting. Children read one immutable snapshot per evaluation and
 * never inspect the mutable parent scopes.
 */
public final class PartitionTerminationBudget<Solution_> {

  private final AbstractPhaseScope<Solution_> parentPhaseScope;
  private final List<SharedLeaf<Solution_>> sharedLeaves = new ArrayList<>();
  private final Definition<Solution_> definition;
  private volatile List<Progress> progress = List.of();

  public PartitionTerminationBudget(
      PhaseTermination<Solution_> termination, AbstractPhaseScope<Solution_> parentPhaseScope) {
    this.parentPhaseScope = Objects.requireNonNull(parentPhaseScope);
    definition = compile(Objects.requireNonNull(termination), false);
    refresh();
  }

  /** Validate before solving, including leaves hidden inside solver bridges and composites. */
  public static void validate(Termination<?> termination, String configurationPath) {
    if (termination instanceof SolverBridgePhaseTermination<?> bridge) {
      validate(bridge.solverTermination, "solver.termination");
    } else if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      validate(
          ((PhaseToSolverTerminationBridge<?>) termination).getSolverTermination(),
          "solver.termination");
    } else if (termination instanceof AbstractCompositeTermination<?> composite) {
      for (int i = 0; i < composite.terminationList.size(); i++) {
        validate(composite.terminationList.get(i), configurationPath + ".termination[" + i + "]");
      }
    } else if (!isShared(termination)
        && !(termination instanceof ChildThreadSupportingTermination<?, ?>)) {
      throw new IllegalArgumentException(
          "The partitioned search termination class (%s) at (%s) does not support independent child termination.\n"
                  .formatted(termination.getClass().getName(), configurationPath)
              + "Implement ChildThreadSupportingTermination with an independent child instance, "
              + "or move this termination to an explicitly configured child phase.");
    }
  }

  /** Publish only from the parent solver thread. */
  public void refresh() {
    progress = sharedLeaves.stream().map(leaf -> leaf.capture(parentPhaseScope)).toList();
  }

  /** True only when shared leaves alone prove the complete expression true. */
  public boolean isDefinitelyTerminated() {
    return definition.definitelyTerminated(progress);
  }

  public UniversalTermination<Solution_> createChildTermination(
      SolverScope<Solution_> childSolverScope) {
    var localLeaves = new ArrayList<Termination<Solution_>>();
    var root = definition.bind(childSolverScope, localLeaves);
    return new PartitionTermination<>(this, root, List.copyOf(localLeaves));
  }

  List<Progress> progress() {
    return progress;
  }

  private static boolean isShared(Termination<?> termination) {
    return termination instanceof TimeMillisSpentTermination
        || termination instanceof BestScoreTermination
        || termination instanceof BestScoreFeasibleTermination
        || termination instanceof BasicPlumbingTermination
        || termination instanceof ChildThreadPlumbingTermination;
  }

  private Definition<Solution_> compile(Termination<Solution_> termination, boolean solverOrigin) {
    if (termination instanceof SolverBridgePhaseTermination<Solution_> bridge) {
      return compile(bridge.solverTermination, true);
    }
    if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      return compile(
          ((PhaseToSolverTerminationBridge<Solution_>) termination).getSolverTermination(), true);
    }
    if (termination instanceof AbstractCompositeTermination<Solution_> composite) {
      var children = composite.terminationList.stream().map(t -> compile(t, solverOrigin)).toList();
      boolean and = composite instanceof AndCompositeTermination;
      return new Definition<>() {
        @Override
        public boolean definitelyTerminated(List<Progress> snapshot) {
          if (children.isEmpty()) {
            return false;
          }
          return and
              ? children.stream().allMatch(c -> c.definitelyTerminated(snapshot))
              : children.stream().anyMatch(c -> c.definitelyTerminated(snapshot));
        }

        @Override
        public Node<Solution_> bind(
            SolverScope<Solution_> scope, List<Termination<Solution_>> leaves) {
          return new CompositeNode<>(
              and, children.stream().map(c -> c.bind(scope, leaves)).toList());
        }
      };
    }
    if (isShared(termination)) {
      int index = sharedLeaves.size();
      sharedLeaves.add(new SharedLeaf<>(termination, solverOrigin));
      return new Definition<>() {
        @Override
        public boolean definitelyTerminated(List<Progress> snapshot) {
          return snapshot.get(index).terminated();
        }

        @Override
        public Node<Solution_> bind(
            SolverScope<Solution_> scope, List<Termination<Solution_>> leaves) {
          return (snapshot, childScope, phaseScope, gradientOnly) -> snapshot.get(index);
        }
      };
    }
    validate(termination, "partitionedSearch.termination");
    return new Definition<>() {
      @Override
      public boolean definitelyTerminated(List<Progress> snapshot) {
        return false; // Unknown: parent merge counts cannot stand in for child work.
      }

      @Override
      public Node<Solution_> bind(
          SolverScope<Solution_> scope, List<Termination<Solution_>> leaves) {
        var copy =
            termination instanceof IslandSequenceTermination<Solution_> island
                ? island.createPartitionChildTermination(scope)
                : ChildThreadSupportingTermination
                    .<Solution_, SolverScope<Solution_>>assertChildThreadSupport(termination)
                    .createChildThreadTermination(scope, ChildThreadType.PART_THREAD);
        leaves.add(copy);
        return new LocalNode<>(copy, solverOrigin);
      }
    };
  }

  record Progress(boolean terminated, double gradient, boolean applicable) {
    static final Progress UNKNOWN = new Progress(false, -1.0, true);
    static final Progress INAPPLICABLE = new Progress(false, -1.0, false);
  }

  private record SharedLeaf<Solution_>(Termination<Solution_> termination, boolean solverOrigin) {
    Progress capture(AbstractPhaseScope<Solution_> phaseScope) {
      var solverScope = phaseScope.getSolverScope();
      boolean terminated =
          solverOrigin
              ? termination.isSolverTerminated(solverScope)
              : termination.isPhaseTerminated(phaseScope);
      double gradient;
      if (termination instanceof BestScoreTermination
          || termination instanceof BestScoreFeasibleTermination) {
        boolean initializedPhaseBaseline =
            !solverOrigin
                && phaseScope.getStartingScore() != null
                && phaseScope.getStartingScore().isFullyAssigned();
        if (initializedPhaseBaseline) {
          gradient = termination.calculatePhaseTimeGradient(phaseScope);
        } else {
          // A phase that starts uninitialized has no usable score baseline yet. Its first
          // initialized parent incumbent establishes the solver baseline during this phase.
          gradient =
              solverScope.getStartingInitializedScore() == null
                  ? 0.0
                  : termination.calculateSolverTimeGradient(solverScope);
        }
      } else {
        gradient =
            solverOrigin
                ? termination.calculateSolverTimeGradient(solverScope)
                : termination.calculatePhaseTimeGradient(phaseScope);
      }
      // A zero elapsed-time limit is immediately complete, including at exactly its start.
      if (Double.isNaN(gradient)
          && termination instanceof TimeMillisSpentTermination<?> spent
          && spent.getTimeMillisSpentLimit() == 0) {
        gradient = terminated ? 1.0 : 0.0;
      }
      return new Progress(terminated, gradient, true);
    }
  }

  private interface Definition<Solution_> {
    boolean definitelyTerminated(List<Progress> snapshot);

    Node<Solution_> bind(SolverScope<Solution_> scope, List<Termination<Solution_>> leaves);
  }

  interface Node<Solution_> {
    Progress evaluate(
        List<Progress> snapshot,
        SolverScope<Solution_> solverScope,
        @Nullable AbstractPhaseScope<Solution_> phaseScope,
        boolean gradientOnly);
  }

  private record CompositeNode<Solution_>(boolean and, List<Node<Solution_>> children)
      implements Node<Solution_> {
    @Override
    public Progress evaluate(
        List<Progress> snapshot,
        SolverScope<Solution_> solverScope,
        @Nullable AbstractPhaseScope<Solution_> phaseScope,
        boolean gradientOnly) {
      boolean terminated = and && !children.isEmpty();
      double gradient = -1.0;
      for (var child : children) {
        var next = child.evaluate(snapshot, solverScope, phaseScope, gradientOnly);
        if (!next.applicable()) {
          if (!gradientOnly && and) {
            return new Progress(false, -1.0, true);
          }
          continue;
        }
        if (!gradientOnly && next.terminated() != and) {
          return new Progress(!and, -1.0, true);
        }
        terminated = and ? terminated && next.terminated() : terminated || next.terminated();
        gradient = TerminationGradient.combine(and, gradient, next.gradient());
      }
      return new Progress(terminated, gradient, true);
    }
  }

  private record LocalNode<Solution_>(Termination<Solution_> termination, boolean solverOrigin)
      implements Node<Solution_> {
    @Override
    public Progress evaluate(
        List<Progress> snapshot,
        SolverScope<Solution_> solverScope,
        @Nullable AbstractPhaseScope<Solution_> phaseScope,
        boolean gradientOnly) {
      // A nested partition adapter retains its own leaf origins. Pass the current child phase
      // through for predicates, while preserving the existing solver-origin gradient endpoint.
      if (!gradientOnly
          && phaseScope != null
          && termination instanceof PartitionTermination<Solution_> partitionTermination) {
        return new Progress(partitionTermination.isPhaseTerminated(phaseScope), -1.0, true);
      }
      if (phaseScope != null
          && !(solverOrigin && termination instanceof SolverTermination)
          && termination instanceof PhaseTermination<Solution_> phaseTermination
          && !phaseTermination.isApplicableTo(phaseScope.getClass())) {
        return Progress.INAPPLICABLE;
      }
      if (phaseScope == null || solverOrigin && termination instanceof SolverTermination) {
        return termination instanceof SolverTermination
            ? new Progress(
                !gradientOnly && termination.isSolverTerminated(solverScope),
                gradientOnly ? termination.calculateSolverTimeGradient(solverScope) : -1.0,
                true)
            : Progress.UNKNOWN;
      }
      return new Progress(
          !gradientOnly && termination.isPhaseTerminated(phaseScope),
          gradientOnly ? termination.calculatePhaseTimeGradient(phaseScope) : -1.0,
          true);
    }
  }
}
