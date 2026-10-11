package greycos.solver.core.impl.constructionheuristic;

import java.util.function.IntFunction;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.placer.EntityPlacer;
import greycos.solver.core.impl.constructionheuristic.placer.PooledEntityPlacer;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.neighborhood.PlacerBasedMoveRepository;
import greycos.solver.core.impl.phase.AbstractPossiblyInitializingPhase;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.phase.ResumablePhaseExecution;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/**
 * Default implementation of {@link ConstructionHeuristicPhase}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public class DefaultConstructionHeuristicPhase<Solution_>
    extends AbstractPossiblyInitializingPhase<Solution_>
    implements ConstructionHeuristicPhase<Solution_> {
  protected final ConstructionHeuristicDecider<Solution_> decider;
  protected final PlacerBasedMoveRepository<Solution_> moveRepository;
  private TerminationStatus terminationStatus = TerminationStatus.NOT_TERMINATED;
  private @Nullable ConstructionExecution activeExecution;

  protected DefaultConstructionHeuristicPhase(
      DefaultConstructionHeuristicPhaseBuilder<Solution_> builder) {
    super(builder);
    this.decider = builder.decider;
    this.moveRepository = new PlacerBasedMoveRepository<>(builder.getEntityPlacer());
  }

  public EntityPlacer<Solution_> getEntityPlacer() {
    return moveRepository.getPlacer();
  }

  @Override
  public TerminationStatus getTerminationStatus() {
    return terminationStatus;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.CONSTRUCTION_HEURISTIC;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::constructionHeuristic;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************
  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    var execution = startExecution(solverScope);
    while (!execution.isComplete()) {
      execution.advance(Long.MAX_VALUE);
    }
  }

  /**
   * Starts one resident execution, retaining its placement iterator until real phase completion.
   */
  public ResumablePhaseExecution<Solution_> startExecution(SolverScope<Solution_> solverScope) {
    if (activeExecution != null) {
      throw new IllegalStateException(
          "The construction heuristic phase already has an active execution.");
    }
    var execution = new ConstructionExecution(solverScope);
    activeExecution = execution;
    try {
      execution.start();
      return execution;
    } catch (RuntimeException | Error failure) {
      execution.fail();
      throw failure;
    }
  }

  private final class ConstructionExecution implements ResumablePhaseExecution<Solution_> {
    private final Thread ownerThread = Thread.currentThread();
    private final ConstructionHeuristicPhaseScope<Solution_> phaseScope;
    private boolean hasListVariable;
    private int maxStepCount = -1;
    private @Nullable TerminationStatus earlyTerminationStatus;
    private boolean complete;
    private boolean failed;

    private ConstructionExecution(SolverScope<Solution_> solverScope) {
      phaseScope = buildPhaseScope(solverScope, phaseIndex);
    }

    private void start() {
      phaseStarted(phaseScope);
      hasListVariable = moveRepository.hasListVariable();
      if (hasListVariable) {
        // In case of list variable with support for unassigned values, the placer will iterate
        // indefinitely.
        // (When it exhausts all values, it will start over from the beginning.)
        // To prevent that, we need to limit the number of steps to the number of unassigned values.
        // The use of ValueRangeManager is safe
        // because it comes from the same score director as the working solution,
        // and therefore is guaranteed to match.
        // We compute fresh initialization statistics as opposed to possibly relying on a cached
        // one,
        // as otherwise Ruin&Recreate doesn't work correctly with its nested CH phase.
        var scoreDirector = phaseScope.getScoreDirector();
        var valueRangeManager = scoreDirector.getValueRangeManager();
        maxStepCount =
            valueRangeManager
                .computeInitializationStatistics(scoreDirector.getWorkingSolution(), null)
                .notInAnyListValueCount();
      }
      if (maxStepCount == 0) {
        earlyTerminationStatus = TerminationStatus.regular(phaseScope.getNextStepIndex());
        completePhase();
      }
    }

    @Override
    public long advance(long completedStepGrant) {
      checkActiveOwner();
      if (completedStepGrant <= 0L) {
        throw new IllegalArgumentException(
            "The completedStepGrant (%d) must be positive.".formatted(completedStepGrant));
      }
      long completedSteps = 0L;
      try {
        while (!complete && completedSteps < completedStepGrant) {
          if (!moveRepository.hasNext()) {
            completePhase();
            break;
          }
          var stepScope = new ConstructionHeuristicStepScope<>(phaseScope);
          stepStarted(stepScope);
          decider.decideNextStep(stepScope, moveRepository.iterator());
          if (stepScope.getStep() == null) {
            if (phaseTermination.isPhaseTerminated(phaseScope)) {
              var logLevel = Level.TRACE;
              if (decider.isLoggingEnabled() && logger.isEnabledForLevel(logLevel)) {
                logger
                    .atLevel(logLevel)
                    .log(
                        "{}    Step index ({}), time spent ({}) terminated without picking a nextStep.",
                        logIndentation,
                        stepScope.getStepIndex(),
                        stepScope.getPhaseScope().calculateSolverTimeMillisSpentUpToNow());
              }
            } else if (stepScope.getSelectedMoveCount() == 0L) {
              var logLevel = Level.WARN;
              if (decider.isLoggingEnabled() && logger.isEnabledForLevel(logLevel)) {
                logger
                    .atLevel(logLevel)
                    .log(
                        "{}    No doable selected move at step index ({}), time spent ({}). Terminating"
                            + " phase early.",
                        logIndentation,
                        stepScope.getStepIndex(),
                        stepScope.getPhaseScope().calculateSolverTimeMillisSpentUpToNow());
              }
            } else {
              throw new IllegalStateException(
                  "The step index (%d) has selected move count (%d) but failed to pick a nextStep (%s)."
                      .formatted(
                          stepScope.getStepIndex(),
                          stepScope.getSelectedMoveCount(),
                          stepScope.getStep()));
            }
            // A terminal decision is not an executed step and receives no stepEnded callback.
            earlyTerminationStatus = TerminationStatus.early(phaseScope.getNextStepIndex());
            completePhase();
            break;
          }
          doStep(stepScope);
          stepEnded(stepScope);
          phaseScope.setLastCompletedStepScope(stepScope);
          completedSteps++;
          if (moveRepository.getPlacer() instanceof PooledEntityPlacer<?>
              && stepScope.getScore().isFullyAssigned()
              && isOnlyUnassignment(stepScope.getStep())) {
            // Nullable variables left null are still reinitializable. Rebuilding their pool after
            // choosing no assignment would repeat the same decision indefinitely.
            earlyTerminationStatus = TerminationStatus.regular(phaseScope.getNextStepIndex());
            completePhase();
          } else if (hasListVariable && (long) stepScope.getStepIndex() + 1 >= maxStepCount) {
            earlyTerminationStatus = TerminationStatus.regular(phaseScope.getNextStepIndex());
            completePhase();
          } else if (phaseTermination.isPhaseTerminated(phaseScope)) {
            earlyTerminationStatus = TerminationStatus.early(phaseScope.getNextStepIndex());
            completePhase();
          }
        }
        return completedSteps;
      } catch (RuntimeException | Error failure) {
        fail();
        throw failure;
      }
    }

    @Override
    public boolean isComplete() {
      checkOwner();
      return complete;
    }

    @Override
    public void finish() {
      checkActiveOwner();
      if (complete) return;
      earlyTerminationStatus = TerminationStatus.early(phaseScope.getNextStepIndex());
      try {
        completePhase();
      } catch (RuntimeException | Error failure) {
        fail();
        throw failure;
      }
    }

    private void completePhase() {
      // Publish termination metadata only at real phase completion, never on a yield.
      terminationStatus =
          translateEarlyTermination(
              phaseScope,
              earlyTerminationStatus,
              earlyTerminationStatus != null && !earlyTerminationStatus.early()
                  ? false
                  : moveRepository.hasNext());
      complete = true;
      try {
        phaseEnded(phaseScope);
      } finally {
        activeExecution = null;
      }
    }

    @Override
    public ConstructionHeuristicPhaseScope<Solution_> getPhaseScope() {
      checkOwner();
      return phaseScope;
    }

    private void checkActiveOwner() {
      checkOwner();
      if (failed) {
        throw new IllegalStateException(
            "The construction heuristic execution has failed and cannot resume.");
      }
    }

    private void checkOwner() {
      if (Thread.currentThread() != ownerThread) {
        throw new IllegalStateException(
            "The construction heuristic execution belongs to thread (%s), not thread (%s)."
                .formatted(ownerThread, Thread.currentThread()));
      }
    }

    private void fail() {
      failed = true;
      activeExecution = null;
    }
  }

  private static boolean isOnlyUnassignment(Move<?> move) {
    if (move instanceof SelectorBasedNoChangeMove<?>) {
      return true;
    }
    if (move instanceof ChangeMove<?> change) {
      return change.getToPlanningValue() == null;
    }
    if (move instanceof SelectorBasedCompositeMove<?> composite) {
      for (var child : composite.getMoves()) {
        if (!isOnlyUnassignment(child)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  protected ConstructionHeuristicPhaseScope<Solution_> buildPhaseScope(
      SolverScope<Solution_> solverScope, int phaseIndex) {
    return new ConstructionHeuristicPhaseScope<>(solverScope, phaseIndex);
  }

  protected void doStep(ConstructionHeuristicStepScope<Solution_> stepScope) {
    var step = stepScope.getStep();
    stepScope.getScoreDirector().executeMove(step);
    predictWorkingStepScore(stepScope, step);
    if (!isNested()) {
      processWorkingSolutionDuringStep(stepScope);
    }
  }

  private void processWorkingSolutionDuringStep(
      ConstructionHeuristicStepScope<Solution_> stepScope) {
    var solver = stepScope.getPhaseScope().getSolverScope().getSolver();
    solver
        .getBestSolutionRecaller()
        .processWorkingSolutionDuringConstructionHeuristicsStep(stepScope);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    super.solvingStarted(solverScope);
    moveRepository.solvingStarted(solverScope);
    decider.solvingStarted(solverScope);
  }

  public void phaseStarted(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    terminationStatus = TerminationStatus.NOT_TERMINATED;
    moveRepository.phaseStarted(phaseScope);
    decider.phaseStarted(phaseScope);
  }

  public void stepStarted(ConstructionHeuristicStepScope<Solution_> stepScope) {
    super.stepStarted(stepScope);
    moveRepository.stepStarted(stepScope);
    decider.stepStarted(stepScope);
  }

  public void stepEnded(ConstructionHeuristicStepScope<Solution_> stepScope) {
    super.stepEnded(stepScope);
    moveRepository.stepEnded(stepScope);
    decider.stepEnded(stepScope);
    if (decider.isLoggingEnabled() && logger.isDebugEnabled()) {
      var timeMillisSpent = stepScope.getPhaseScope().calculateSolverTimeMillisSpentUpToNow();
      logger.debug(
          "{}    CH step ({}), time spent ({}), score ({}), selected move count ({}), picked move"
              + " ({}).",
          logIndentation,
          stepScope.getStepIndex(),
          timeMillisSpent,
          stepScope.getScore().raw(),
          stepScope.getSelectedMoveCount(),
          stepScope.getStepString());
    }
  }

  public void phaseEnded(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    ensureCorrectTermination(phaseScope, logger);
    updateBestSolutionAndFire(phaseScope);
    moveRepository.phaseEnded(phaseScope);
    decider.phaseEnded(phaseScope);
    phaseScope.endingNow();
    if (decider.isLoggingEnabled() && logger.isInfoEnabled()) {
      logger.info(
          """
          {}Construction Heuristic phase ({}) ended: time spent ({}), environment mode ({}), best score ({}), \
          {}move evaluation speed ({}/sec), step total ({}).\
          """,
          logIndentation,
          phaseIndex,
          phaseScope.calculateSolverTimeMillisSpentUpToNow(),
          environmentMode.name(),
          phaseScope.getBestScore().raw(),
          // Multithreaded solving uses "effective" move evaluation speed, since not all evaluated
          // moves
          // are foraged
          (decider.getClass().equals(ConstructionHeuristicDecider.class)) ? "" : "effective ",
          phaseScope.getPhaseMoveEvaluationSpeed(),
          phaseScope.getNextStepIndex());
    }
  }

  private void updateBestSolutionAndFire(ConstructionHeuristicPhaseScope<Solution_> phaseScope) {
    if (!isNested() && !phaseScope.getStartingScore().equals(phaseScope.getBestScore())) {
      // Only update the best solution if the CH made any change; nested phases don't update the
      // best solution.
      var solver = phaseScope.getSolverScope().getSolver();
      solver
          .getBestSolutionRecaller()
          .updateBestSolutionAndFire(phaseScope.getSolverScope(), phaseScope);
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    super.solvingEnded(solverScope);
    moveRepository.solvingEnded(solverScope);
    decider.solvingEnded(solverScope);
  }

  @Override
  public void solvingError(SolverScope<Solution_> solverScope, Throwable exception) {
    if (activeExecution != null) activeExecution.fail();
    super.solvingError(solverScope, exception);
    decider.solvingError(solverScope, exception);
  }

  public static class DefaultConstructionHeuristicPhaseBuilder<Solution_>
      extends AbstractPossiblyInitializingPhaseBuilder<
          Solution_, DefaultConstructionHeuristicPhase<Solution_>> {

    private final EntityPlacer<Solution_> entityPlacer;
    private final ConstructionHeuristicDecider<Solution_> decider;

    public DefaultConstructionHeuristicPhaseBuilder(
        int phaseIndex,
        boolean lastInitializingPhase,
        EnvironmentMode environmentMode,
        String logIndentation,
        PhaseTermination<Solution_> phaseTermination,
        EntityPlacer<Solution_> entityPlacer,
        ConstructionHeuristicDecider<Solution_> decider) {
      super(phaseIndex, lastInitializingPhase, environmentMode, logIndentation, phaseTermination);
      this.entityPlacer = entityPlacer;
      this.decider = decider;
    }

    public EntityPlacer<Solution_> getEntityPlacer() {
      return entityPlacer;
    }

    @Override
    public DefaultConstructionHeuristicPhase<Solution_> build() {
      return new DefaultConstructionHeuristicPhase<>(this);
    }
  }
}
