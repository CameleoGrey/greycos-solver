package greycos.solver.core.impl.solver.termination;

import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.Nullable;

/**
 * Thread-confined evaluator of an island sequence budget. Descendant evaluators share the original
 * island's cumulative work and history, while retaining their own phase applicability and latch.
 */
public final class IslandSequenceTermination<Solution_>
    extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final IslandTerminationBudget<Solution_> budget;
  private final SolverScope<Solution_> solverScope;
  private final IslandWorkQuota<Solution_> quota;
  private final boolean scoresComparableToOwner;
  private final IslandWorkQuota<Solution_>.@Nullable Member member;
  private final IslandTerminationBudget<Solution_>.ProgressScope globalScope;
  private final IslandTerminationBudget.Node<Solution_> root;
  private IslandTerminationBudget.Progress globalProgress;
  private IslandWorkQuota.Snapshot workProgress;
  private @Nullable AbstractPhaseScope<Solution_> currentPhase;
  private boolean started;
  private boolean ended;
  private boolean terminated;
  private boolean searchActive;

  IslandSequenceTermination(
      IslandTerminationBudget<Solution_> budget, SolverScope<Solution_> scope) {
    this(budget, scope, new IslandWorkQuota<>(scope, budget.phaseStartMillis()), true, true);
  }

  private IslandSequenceTermination(
      IslandTerminationBudget<Solution_> budget,
      SolverScope<Solution_> scope,
      IslandWorkQuota<Solution_> quota,
      boolean contributes,
      boolean scoresComparableToOwner) {
    this.budget = budget;
    solverScope = scope;
    this.quota = quota;
    this.scoresComparableToOwner = scoresComparableToOwner;
    member = contributes ? quota.register(scope) : null;
    workProgress = quota.snapshot();
    globalScope = budget.newProgressScope();
    globalProgress = budget.progress();
    root = budget.bind(this);
  }

  IslandWorkQuota.Snapshot workProgress() {
    return workProgress;
  }

  AbstractPhaseScope<Solution_> globalScope() {
    return globalScope;
  }

  IslandTerminationBudget.Progress globalProgress() {
    return globalProgress;
  }

  IslandTerminationBudget.Node<Solution_> bindSearchTermination(
      PhaseTermination<Solution_> definition) {
    return quota.bindSearchTermination(definition);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> scope) {
    if (started) {
      return;
    }
    if (scope != solverScope) {
      throw new IllegalStateException(
          "An island sequence budget must remain bound to its original solver scope.");
    }
    started = true;
    if (member != null) {
      quota.start(scoresComparableToOwner ? solverScope.getBestScore() : null);
      publishWork();
    }
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    if (currentPhase == phaseScope) {
      return;
    }
    currentPhase = phaseScope;
    searchActive = isSearchPhase(phaseScope);
    if (phaseScope instanceof LocalSearchPhaseScope
        || phaseScope instanceof AlnsPhaseScope
        || phaseScope instanceof IteratedLocalSearchPhaseScope) {
      budget.searchStarted();
    }
    if (member != null) {
      if (scoresComparableToOwner) {
        quota.recordBest(solverScope.getBestScore());
      }
      publishWork();
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    publishWork();
    // Retain applicability until the runner checks the sequence guard for the next phase.
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (member != null) {
      quota.stepStarted(member, stepScope, isSearchPhase(stepScope.getPhaseScope()));
      publishWork();
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (member != null) {
      quota.stepEnded(
          member,
          stepScope,
          isSearchPhase(stepScope.getPhaseScope()),
          scoresComparableToOwner ? solverScope.getBestScore() : null);
      publishWork();
    }
  }

  @Override
  public void bestScoreImproved(AbstractStepScope<Solution_> stepScope) {
    // Only shared strict publications update global idle history. Local history belongs to the
    // original island and must survive nested starts and the retirement of descendant solvers.
    if (member != null && scoresComparableToOwner) {
      quota.bestScoreImproved(solverScope.getBestScore(), isSearchPhase(stepScope.getPhaseScope()));
    }
  }

  private static boolean isSearchPhase(AbstractPhaseScope<?> scope) {
    return !(scope instanceof ConstructionHeuristicPhaseScope)
        && !(scope instanceof CustomPhaseScope);
  }

  /** Publish only on this evaluator's owning solver thread, before its director is closed. */
  public void publishWork() {
    if (member != null) {
      workProgress =
          quota.publish(
              member,
              solverScope.getMoveEvaluationCount(),
              solverScope.getScoreDirector().getCalculationCount());
    } else {
      workProgress = quota.snapshot();
    }
  }

  private void refreshProgress() {
    publishWork();
    globalProgress = budget.progress();
    globalScope.update(globalProgress);
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> scope) {
    if (budget.hasSharedHistory()) {
      synchronized (budget) {
        return evaluateTermination();
      }
    }
    return evaluateTermination();
  }

  private boolean evaluateTermination() {
    synchronized (quota) {
      // Keep publishing after the latch: final in-flight work still belongs to enclosing quotas.
      refreshProgress();
      if (!terminated && root.applicable(searchActive) && root.isTerminated()) {
        // Only the complete expression latches; idle leaves within AND may become false again.
        terminated = true;
      }
      return terminated;
    }
  }

  @Override
  public boolean isPhaseTerminated(AbstractPhaseScope<Solution_> phaseScope) {
    return isSolverTerminated(solverScope);
  }

  @Override
  public double calculateSolverTimeGradient(SolverScope<Solution_> scope) {
    if (budget.hasSharedHistory()) {
      synchronized (budget) {
        return evaluateGradient();
      }
    }
    return evaluateGradient();
  }

  private double evaluateGradient() {
    synchronized (quota) {
      refreshProgress();
      var gradient = root.applicable(searchActive) ? root.gradient() : -1.0;
      return terminated && gradient >= 0.0 && gradient <= 1.0 ? 1.0 : gradient;
    }
  }

  @Override
  public double calculatePhaseTimeGradient(AbstractPhaseScope<Solution_> phaseScope) {
    return calculateSolverTimeGradient(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> scope) {
    if (ended) {
      return;
    }
    ended = true;
    publishWork();
    // Descendants may finish independently. Retain their work and the owner's shared history.
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> scope, ChildThreadType childThreadType) {
    if (scope == solverScope) {
      return this;
    }
    publishWork();
    // MOVE workers already credit consumed calculations to their owner. Register solver scopes
    // only; counting MOVE directors would count speculative or already credited work twice.
    return new IslandSequenceTermination<>(
        budget,
        scope,
        quota,
        childThreadType == ChildThreadType.PART_THREAD,
        scoresComparableToOwner);
  }

  /**
   * A partition solves only part of the owner's problem. Its work belongs to the inherited quota,
   * but only the parent's merged full-problem score may update the owner's search history.
   */
  IslandSequenceTermination<Solution_> createPartitionChildTermination(
      SolverScope<Solution_> scope) {
    if (scope == solverScope) {
      throw new IllegalArgumentException("A partition child must use its own solver scope.");
    }
    // A partition definition may be rebound by a descendant thread. Do not inspect the source
    // evaluator's mutable solver scope here; its own lifecycle callbacks publish its work.
    return new IslandSequenceTermination<>(budget, scope, quota, true, false);
  }

  boolean supportedForRepairAttempts() {
    return root.supportsRepairAttempts();
  }
}
