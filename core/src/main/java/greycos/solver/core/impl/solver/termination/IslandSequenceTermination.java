package greycos.solver.core.impl.solver.termination;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.custom.scope.CustomPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.jspecify.annotations.Nullable;

/**
 * Thread-confined bridge from an island's inner phases to its enclosing sequence budget. The bound
 * scopes never change when another inner phase starts, and no public step event is synthesized.
 */
public final class IslandSequenceTermination<Solution_>
    extends AbstractUniversalTermination<Solution_>
    implements ChildThreadSupportingTermination<Solution_, SolverScope<Solution_>> {

  private final IslandTerminationBudget<Solution_> budget;
  private final SolverScope<Solution_> solverScope;
  private final SequenceScope workScope;
  private final SequenceScope searchScope;
  private final IslandTerminationBudget<Solution_>.ProgressScope globalScope;
  private final IslandTerminationBudget.Node<Solution_> root;
  private final List<PhaseTermination<Solution_>> statefulTerminations = new ArrayList<>();
  private IslandTerminationBudget.Progress globalProgress;
  private @Nullable AbstractPhaseScope<Solution_> currentPhase;
  private @Nullable AbstractStepScope<Solution_> lastStartedStep;
  private @Nullable AbstractStepScope<Solution_> lastEndedStep;
  private @Nullable InnerScore<?> localBestScore;
  private boolean started;
  private boolean ended;
  private boolean terminated;
  private boolean searchActive;

  IslandSequenceTermination(
      IslandTerminationBudget<Solution_> budget, SolverScope<Solution_> scope) {
    this.budget = budget;
    solverScope = scope;
    workScope = new SequenceScope();
    searchScope = new SequenceScope();
    globalScope = budget.newProgressScope();
    globalProgress = budget.progress();
    root = budget.bind(this);
  }

  AbstractPhaseScope<Solution_> workScope() {
    return workScope;
  }

  AbstractPhaseScope<Solution_> searchScope() {
    return searchScope;
  }

  AbstractPhaseScope<Solution_> globalScope() {
    return globalScope;
  }

  IslandTerminationBudget.Progress globalProgress() {
    return globalProgress;
  }

  void addStatefulTermination(PhaseTermination<Solution_> termination) {
    statefulTerminations.add(termination);
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
    workScope.reset();
    searchScope.reset();
    localBestScore = solverScope.getBestScore();
    for (var termination : statefulTerminations) {
      termination.phaseStarted(searchScope);
    }
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    if (currentPhase == phaseScope) {
      return;
    }
    currentPhase = phaseScope;
    searchActive = isSearchPhase(phaseScope);
    if (phaseScope instanceof LocalSearchPhaseScope || phaseScope instanceof AlnsPhaseScope) {
      budget.searchStarted();
    }
    // A construction phase may initialize the local best before the first search phase starts.
    recordLocalBest();
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    // Retain the last phase's applicability until the phase runner checks the sequence guard.
    // In particular, an exhausted search budget must prevent starting a later inner phase.
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (lastStartedStep == stepScope) {
      return;
    }
    lastStartedStep = stepScope;
    if (isSearchPhase(stepScope.getPhaseScope())) {
      var step = searchScope.nextStep();
      for (var termination : statefulTerminations) {
        termination.stepStarted(step);
      }
    }
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    if (lastEndedStep == stepScope) {
      return;
    }
    lastEndedStep = stepScope;
    workScope.completeStep();
    if (isSearchPhase(stepScope.getPhaseScope())) {
      var step = searchScope.completeStep();
      recordLocalBest();
      for (var termination : statefulTerminations) {
        termination.stepEnded(step);
      }
    } else {
      recordLocalBest();
    }
  }

  @Override
  public void bestScoreImproved(AbstractStepScope<Solution_> stepScope) {
    // Local adoption can improve an island without completing a search step. Global idle history
    // is updated exclusively by the shared publication callback, never by this notification.
    recordLocalBest();
    if (isSearchPhase(stepScope.getPhaseScope())) {
      for (var termination : statefulTerminations) {
        termination.bestScoreImproved(searchScope.getLastCompletedStepScope());
      }
    }
  }

  private void recordLocalBest() {
    var score = solverScope.getBestScore();
    if (score != null
        && (localBestScore == null || IslandTerminationBudget.compare(score, localBestScore) > 0)) {
      localBestScore = score;
      searchScope.setBestSolutionStepIndex(searchScope.getLastCompletedStepScope().getStepIndex());
    }
  }

  private static boolean isSearchPhase(AbstractPhaseScope<?> scope) {
    return !(scope instanceof ConstructionHeuristicPhaseScope)
        && !(scope instanceof CustomPhaseScope);
  }

  private void refreshGlobalProgress() {
    globalProgress = budget.progress();
    globalScope.update(globalProgress);
  }

  @Override
  public boolean isSolverTerminated(SolverScope<Solution_> scope) {
    if (budget.hasSharedHistory()) {
      // Keep history predicates and the immutable global snapshot on the same publication version
      // for the complete AND/OR expression. Ordinary elapsed/idle graphs need no monitor.
      synchronized (budget) {
        return evaluateTermination();
      }
    }
    return evaluateTermination();
  }

  private boolean evaluateTermination() {
    if (terminated) {
      return true;
    }
    refreshGlobalProgress();
    if (root.applicable(searchActive) && root.isTerminated()) {
      // Only the complete expression latches. An idle leaf within AND may become false again
      // after another island improves before this island has consumed its own work quota.
      terminated = true;
    }
    return terminated;
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
    if (terminated) {
      return 1.0;
    }
    refreshGlobalProgress();
    return root.applicable(searchActive) ? root.gradient() : 0.0;
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
    for (var termination : statefulTerminations) {
      termination.phaseEnded(searchScope);
    }
  }

  @Override
  public Termination<Solution_> createChildThreadTermination(
      SolverScope<Solution_> scope, ChildThreadType childThreadType) {
    if (scope == solverScope) {
      return this;
    }
    return budget.createIslandTermination(scope);
  }

  boolean supportedForRepairAttempts() {
    return root.supportsRepairAttempts();
  }

  private final class SequenceScope extends AbstractPhaseScope<Solution_> {
    private int completedSteps;
    private SequenceStep lastStep = new SequenceStep(this, -1);

    private SequenceScope() {
      super(IslandSequenceTermination.this.solverScope, 0);
      startingSystemTimeMillis = budget.phaseStartMillis();
    }

    SequenceStep nextStep() {
      var step = new SequenceStep(this, completedSteps);
      step.setScore(solverScope.getBestScore());
      return step;
    }

    SequenceStep completeStep() {
      lastStep = nextStep();
      if (completedSteps != Integer.MAX_VALUE) {
        completedSteps++;
      }
      return lastStep;
    }

    @Override
    public AbstractStepScope<Solution_> getLastCompletedStepScope() {
      return lastStep;
    }

    @Override
    public int getNextStepIndex() {
      return completedSteps;
    }
  }

  private final class SequenceStep extends AbstractStepScope<Solution_> {
    private final SequenceScope scope;

    private SequenceStep(SequenceScope scope, int index) {
      super(index);
      this.scope = scope;
    }

    @Override
    public AbstractPhaseScope<Solution_> getPhaseScope() {
      return scope;
    }
  }
}
