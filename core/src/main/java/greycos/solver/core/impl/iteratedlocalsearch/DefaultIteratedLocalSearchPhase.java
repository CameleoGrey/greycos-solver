package greycos.solver.core.impl.iteratedlocalsearch;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.IntFunction;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.impl.heuristic.selector.common.SelectionAttemptLedger;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.islandmodel.GlobalBestUpdater;
import greycos.solver.core.impl.islandmodel.SolutionSyncMove;
import greycos.solver.core.impl.localsearch.LocalSearchEpisodeRunner;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.move.PreparableMove;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.move.PreparedMoveFilters;
import greycos.solver.core.impl.move.SolutionAssignmentDiagnostics;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSamples;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

/**
 * Strict incumbent acceptance over bounded native local-search episodes. Scoring state and move
 * workers belong to this phase; the histories used to improve each starting state do not.
 */
public final class DefaultIteratedLocalSearchPhase<Solution_> extends AbstractPhase<Solution_>
    implements IteratedLocalSearchPhase<Solution_> {

  public record Diagnostics(
      long episodes,
      long initialEpisodes,
      long completedIterations,
      long acceptedIterations,
      long rejectedIterations,
      long failedPerturbations,
      long noChangeCount,
      long interruptedIterations,
      long migrantRestarts,
      long perturbationAttempts,
      long episodeAttempts,
      long speculativeSelectionAttempts,
      long primitiveSteps,
      long scoreCalculations,
      long consumedWorkerCalculations,
      long additionalWorkerCalculations,
      long workerStartups,
      int moveWorkers,
      long snapshotNanos,
      long restorationNanos,
      long resourceSetupNanos,
      long episodeSetupNanos,
      long fullAssignmentCaptures,
      long copiedAssignmentBindings,
      long copiedListElements,
      long assignmentCaptureNanos,
      long assignmentComparisons,
      long assignmentComparisonNanos,
      long assignmentValidations,
      long assignmentValidationNanos,
      long episodeBestUpdates,
      String completionReason) {}

  private record Incumbent<Solution_>(
      SolutionAssignments<Solution_> assignments, InnerScore<?> score) {}

  private enum ShakeOutcome {
    COMPLETE,
    FAILED,
    NO_CHANGE,
    INTERRUPTED,
    ADOPTION
  }

  private final IteratedLocalSearchPhaseConfig config;
  private final LocalSearchStepLoggingMode stepLoggingMode;
  private final LocalSearchEpisodeRunner<Solution_> episodes;
  private final MoveSelector<Solution_> perturbation;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final int moveWorkers;
  private final boolean timingDiagnostics =
      Boolean.getBoolean("greycos.solver.iteratedLocalSearchDiagnostics");
  private IteratedLocalSearchMetrics<Solution_> metrics;
  private Diagnostics diagnostics;

  private DefaultIteratedLocalSearchPhase(Builder<Solution_> builder) {
    super(builder);
    config = builder.config;
    var configuredLoggingMode = config.getLocalSearchConfig().getStepLoggingMode();
    stepLoggingMode =
        configuredLoggingMode == null ? LocalSearchStepLoggingMode.ALL : configuredLoggingMode;
    episodes = builder.episodes;
    perturbation = builder.perturbation;
    bestSolutionRecaller = builder.bestSolutionRecaller;
    moveWorkers = builder.moveWorkers == null ? 0 : builder.moveWorkers;
  }

  public Diagnostics getDiagnostics() {
    return diagnostics;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.ITERATED_LOCAL_SEARCH;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::iteratedLocalSearch;
  }

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    var scope = new IteratedLocalSearchPhaseScope<>(solverScope, phaseIndex);
    metrics = new IteratedLocalSearchMetrics<>();
    diagnostics = null;
    var contexts = new IdentityHashMap<Object, PreparableMove<Solution_>>();
    scope.assignmentDiagnostics = timingDiagnostics ? SolutionAssignmentDiagnostics.open() : null;
    boolean phaseActive = false;
    boolean activeIteration = false;
    Throwable failure = null;
    try {
      solverScope.getSolver().prepareForPhase(scope);
      phaseActive = true;
      phaseStarted(scope);
      var initialScore = scope.calculateScore();
      requireComplete(initialScore, "starting solution; configure a construction heuristic first");
      scope.getLastCompletedStepScope().setScore(initialScore);
      scope.initializeControllerRandom();
      metrics.phaseStarted(scope);
      resetStrength(scope);
      logger.info(
          "{}Iterated Local Search phase ({}) starts with strengths ({}), move workers ({}).",
          logIndentation,
          phaseIndex,
          config.getPerturbationStrengths(),
          moveWorkers);
      if (isTerminated(scope)) {
        scope.completionReason = "ENCLOSING_TERMINATION";
        return;
      }
      Incumbent<Solution_> incumbent = snapshot(scope, initialScore);
      incumbent.assignments().validateComplete(scope.getScoreDirector());
      long resourceStart = now();
      scope.evaluationResourcesStarted = true;
      episodes.open(scope, initialScore);
      scope.resourceSetupNanos += elapsed(resourceStart);
      boolean initialEpisodeRequired = true;
      int consecutiveFailedStrengths = 0;
      while (!isTerminated(scope)) {
        var adopted = adoptPending(scope);
        if (adopted != null) {
          incumbent = adopted;
          initialEpisodeRequired = true;
          consecutiveFailedStrengths = 0;
          resetStrength(scope);
          scope.migrantRestarts++;
          metrics.update(scope);
        }
        if (initialEpisodeRequired) {
          scope.initialEpisodes++;
          var result = improve(scope);
          if (result.outcome() == LocalSearchEpisodeRunner.Outcome.ENCLOSING_TERMINATED) break;
          if (result.outcome() == LocalSearchEpisodeRunner.Outcome.ADOPTION) {
            var migrant = adoptPending(scope);
            if (migrant != null) {
              incumbent = migrant;
              scope.migrantRestarts++;
              metrics.update(scope);
              consecutiveFailedStrengths = 0;
              resetStrength(scope);
            } else {
              restore(scope, incumbent);
            }
            continue;
          }
          if (compare(result.bestScore(), incumbent.score()) > 0) {
            incumbent = new Incumbent<>(result.bestAssignments(), result.bestScore());
          }
          restore(scope, incumbent);
          initialEpisodeRequired = false;
          continue;
        }
        if (config.getIterationCountLimit() != null
            && scope.completedIterations >= config.getIterationCountLimit()) {
          scope.completionReason = "ITERATION_LIMIT";
          break;
        }
        activeIteration = true;
        var shake = shake(scope, incumbent, contexts);
        if (shake == ShakeOutcome.INTERRUPTED) break;
        if (shake == ShakeOutcome.ADOPTION) {
          var migrant = adoptPending(scope);
          if (migrant != null) {
            incumbent = migrant;
            scope.migrantRestarts++;
            metrics.update(scope);
            initialEpisodeRequired = true;
            consecutiveFailedStrengths = 0;
            resetStrength(scope);
          } else {
            // A newer local best made the queued migrant stale. End this interrupted trial
            // without fabricating an acceptance outcome, and restart from the retained incumbent.
            scope.interruptedIterations++;
            restore(scope, incumbent);
          }
          activeIteration = false;
          continue;
        }
        if (shake == ShakeOutcome.FAILED || shake == ShakeOutcome.NO_CHANGE) {
          restore(scope, incumbent);
          scope.completedIterations++;
          scope.failedPerturbations++;
          if (shake == ShakeOutcome.NO_CHANGE) scope.noChangeCount++;
          activeIteration = false;
          consecutiveFailedStrengths++;
          logIteration(scope, "NO_PERTURBATION", null, incumbent.score());
          advanceStrength(scope);
          if (consecutiveFailedStrengths == config.getPerturbationStrengths().size()) {
            scope.completionReason = "NO_PROGRESS";
            break;
          }
          continue;
        }
        var result = improve(scope);
        if (result.outcome() == LocalSearchEpisodeRunner.Outcome.ENCLOSING_TERMINATED) break;
        if (result.outcome() == LocalSearchEpisodeRunner.Outcome.ADOPTION) {
          var migrant = adoptPending(scope);
          if (migrant != null) {
            incumbent = migrant;
            scope.migrantRestarts++;
            metrics.update(scope);
            initialEpisodeRequired = true;
            consecutiveFailedStrengths = 0;
            resetStrength(scope);
          } else {
            scope.interruptedIterations++;
            restore(scope, incumbent);
          }
          activeIteration = false;
          continue;
        }
        boolean accepted = compare(result.bestScore(), incumbent.score()) > 0;
        if (accepted) incumbent = new Incumbent<>(result.bestAssignments(), result.bestScore());
        restore(scope, incumbent);
        scope.completedIterations++;
        if (accepted) scope.acceptedIterations++;
        else scope.rejectedIterations++;
        logIteration(
            scope, accepted ? "ACCEPTED" : "REJECTED", result.bestScore(), incumbent.score());
        if (accepted) resetStrength(scope);
        else advanceStrength(scope);
        consecutiveFailedStrengths = 0;
        activeIteration = false;
      }
      if (activeIteration) scope.interruptedIterations++;
      if (scope.completionReason == null) scope.completionReason = "ENCLOSING_TERMINATION";
    } catch (CancellationException cancelled) {
      if (cancelled.getSuppressed().length != 0 || !isTerminated(scope)) {
        failure = cancelled;
        throw cancelled;
      }
      if (activeIteration) scope.interruptedIterations++;
      scope.completionReason = "ENCLOSING_TERMINATION";
    } catch (RuntimeException | Error thrown) {
      failure = thrown;
      scope.completionReason = "FAILURE";
      throw thrown;
    } finally {
      try {
        Throwable cleanupFailure = null;
        try {
          episodes.close();
        } catch (RuntimeException | Error thrown) {
          cleanupFailure = thrown;
        }
        // Closing resources joins workers before any context or provider state is disposed.
        boolean safeToDispose = episodes.isEvaluationStateSafeToDispose();
        if (!safeToDispose) deferCleanup(scope, contexts, phaseActive);
        if (safeToDispose) {
          for (var context : contexts.values()) {
            try {
              context.closeEvaluationContext(scope.getScoreDirector());
            } catch (RuntimeException | Error thrown) {
              cleanupFailure = append(cleanupFailure, thrown);
            }
          }
        }
        if (safeToDispose && scope.perturbationPhaseStarted) {
          try {
            perturbation.phaseEnded(scope);
          } catch (RuntimeException | Error thrown) {
            cleanupFailure = append(cleanupFailure, thrown);
          }
        }
        if (safeToDispose && scope.perturbationSolvingStarted) {
          try {
            perturbation.solvingEnded(solverScope);
          } catch (RuntimeException | Error thrown) {
            cleanupFailure = append(cleanupFailure, thrown);
          }
        }
        if (phaseActive) {
          if (cleanupFailure != null) scope.completionReason = "FAILURE";
          try {
            scope.endingNow();
            diagnostics = diagnostics(scope);
            logger.info(
                "{}Iterated Local Search phase ({}) ended: time spent ({}), environment mode ({}), best score ({}),"
                    + " move evaluation speed ({}/sec), step total ({}), completion reason ({}), iterations ({}), episodes ({}).",
                logIndentation,
                phaseIndex,
                scope.calculateSolverTimeMillisSpentUpToNow(),
                environmentMode.name(),
                logScore(scope.getBestScore()),
                scope.getPhaseMoveEvaluationSpeed(),
                scope.getNextStepIndex(),
                scope.completionReason,
                scope.completedIterations,
                scope.episodes);
          } catch (RuntimeException | Error thrown) {
            cleanupFailure = append(cleanupFailure, thrown);
          }
          if (safeToDispose) {
            try {
              metrics.phaseEnded(scope);
            } catch (RuntimeException | Error thrown) {
              cleanupFailure = append(cleanupFailure, thrown);
            }
            try {
              phaseEnded(scope);
            } catch (RuntimeException | Error thrown) {
              cleanupFailure = append(cleanupFailure, thrown);
            }
          }
        }
        if (cleanupFailure != null) {
          if (failure != null) {
            if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
          } else if (cleanupFailure instanceof RuntimeException runtime) throw runtime;
          else throw (Error) cleanupFailure;
        }
      } finally {
        if (scope.assignmentDiagnostics != null) scope.assignmentDiagnostics.close();
      }
    }
  }

  private void deferCleanup(
      IteratedLocalSearchPhaseScope<Solution_> scope,
      Map<Object, PreparableMove<Solution_>> contexts,
      boolean phaseActive) {
    var retainedMetrics = metrics;
    boolean[] phasePending = {phaseActive};
    scope
        .getSolverScope()
        .getWorkerRegistry()
        .deferCleanup(
            this,
            () -> {
              // The registry runs this on the next owner cleanup, before resetting counters or
              // replacing
              // the old coordinator graph, and only after all registered workers have actually
              // exited.
              episodes.close();
              if (!episodes.isEvaluationStateSafeToDispose()) {
                throw new IllegalStateException(
                    "Iterated Local Search workers still own the retained phase state.");
              }
              Throwable failure = null;
              var iterator = contexts.values().iterator();
              while (iterator.hasNext()) {
                var context = iterator.next();
                iterator.remove();
                try {
                  context.closeEvaluationContext(scope.getScoreDirector());
                } catch (RuntimeException | Error thrown) {
                  failure = append(failure, thrown);
                }
              }
              if (scope.perturbationPhaseStarted) {
                scope.perturbationPhaseStarted = false;
                try {
                  perturbation.phaseEnded(scope);
                } catch (RuntimeException | Error thrown) {
                  failure = append(failure, thrown);
                }
              }
              if (scope.perturbationSolvingStarted) {
                scope.perturbationSolvingStarted = false;
                try {
                  perturbation.solvingEnded(scope.getSolverScope());
                } catch (RuntimeException | Error thrown) {
                  failure = append(failure, thrown);
                }
              }
              if (phasePending[0]) {
                phasePending[0] = false;
                try {
                  retainedMetrics.phaseEnded(scope);
                } catch (RuntimeException | Error thrown) {
                  failure = append(failure, thrown);
                }
                try {
                  phaseEnded(scope);
                } catch (RuntimeException | Error thrown) {
                  failure = append(failure, thrown);
                }
              }
              diagnostics = diagnostics(scope);
              if (failure instanceof RuntimeException runtime) throw runtime;
              if (failure instanceof Error error) throw error;
            });
  }

  private LocalSearchEpisodeRunner.EpisodeResult<Solution_> improve(
      IteratedLocalSearchPhaseScope<Solution_> scope) {
    checkTermination(scope);
    scope.episodes++;
    metrics.update(scope);
    var startingScore = scope.getLastCompletedStepScope().getScore();
    logger.debug(
        "{}  ILS episode ({}) starts at score ({}), strength ({}).",
        logIndentation,
        scope.episodes,
        startingScore.raw(),
        scope.strength);
    long consumedBefore = episodes.getConsumedSelectionCount();
    long discardedBefore = episodes.getDiscardedSelectionCount();
    LocalSearchEpisodeRunner.EpisodeResult<Solution_> result;
    Throwable failure = null;
    try {
      result =
          episodes.runEpisode(
              startingScore,
              new LocalSearchEpisodeRunner.EpisodeCallbacks<>() {
                private IteratedLocalSearchStepScope<Solution_> outerStep;

                @Override
                public boolean isEnclosingTerminated() {
                  return isTerminated(scope);
                }

                @Override
                public boolean isAdoptionPending() {
                  return scope.getSolverScope().hasPendingMove();
                }

                @Override
                public void decisionStarted(LocalSearchStepScope<Solution_> inner) {
                  outerStep =
                      new IteratedLocalSearchStepScope<>(
                          scope, IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
                  try {
                    stepStarted(outerStep);
                  } catch (RuntimeException | Error failure) {
                    scope.getSolverScope().getSolver().stepAborted(outerStep);
                    outerStep = null;
                    throw failure;
                  }
                }

                @Override
                public void beforeMoveCommitted(LocalSearchStepScope<Solution_> inner) {
                  captureStepString(outerStep, inner.getStep(), inner.getScore());
                }

                @Override
                public void moveCommitted(LocalSearchStepScope<Solution_> inner) {
                  outerStep.setMove(inner.getStep());
                  outerStep.setScore(inner.getScore());
                  outerStep.setSelectedMoveCount(inner.getSelectedMoveCount());
                  outerStep.setAcceptedMoveCount(inner.getAcceptedMoveCount());
                  finishStep(outerStep);
                  outerStep = null;
                }

                @Override
                public void decisionAborted(LocalSearchStepScope<Solution_> inner) {
                  if (outerStep != null) {
                    scope.getSolverScope().getSolver().stepAborted(outerStep);
                    outerStep = null;
                  }
                }
              });
    } catch (RuntimeException | Error thrown) {
      failure = thrown;
      throw thrown;
    } finally {
      scope.episodeAttempts += episodes.getConsumedSelectionCount() - consumedBefore;
      scope.speculativeSelectionAttempts += episodes.getDiscardedSelectionCount() - discardedBefore;
      try {
        metrics.update(scope);
      } catch (RuntimeException | Error thrown) {
        if (failure == null) throw thrown;
        if (failure != thrown) failure.addSuppressed(thrown);
      }
    }
    logger.debug(
        "{}  ILS episode ({}) ended ({}/{}), best ({}), attempts ({}), committed moves ({}), elapsed ({} ms).",
        logIndentation,
        scope.episodes,
        result.outcome(),
        result.completionReason(),
        result.bestScore().raw(),
        result.attempts(),
        result.committedSteps(),
        result.elapsedMillis());
    return result;
  }

  private ShakeOutcome shake(
      IteratedLocalSearchPhaseScope<Solution_> scope,
      Incumbent<Solution_> incumbent,
      Map<Object, PreparableMove<Solution_>> contexts) {
    var ledger =
        new SelectionAttemptLedger(
            config.getPerturbationAttemptLimit(),
            () -> isTerminated(scope) || scope.getSolverScope().hasPendingMove());
    int successful = 0;
    Throwable failure = null;
    try {
      while (successful < scope.strength && !ledger.isExhausted()) {
        if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
        if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
        var step =
            new IteratedLocalSearchStepScope<>(
                scope, IteratedLocalSearchStepScope.Origin.PERTURBATION);
        boolean committed = false;
        boolean[] selectorStepEntered = {false};
        boolean discardSetup = false;
        Throwable stepFailure = null;
        try {
          stepStarted(step);
          if (!ledger.runSetup(
              () -> {
                if (!scope.perturbationSolvingStarted) {
                  scope.perturbationSolvingStarted = true;
                  perturbation.solvingStarted(scope.getSolverScope());
                }
                if (!scope.perturbationPhaseStarted) {
                  scope.perturbationPhaseStarted = true;
                  perturbation.phaseStarted(scope);
                }
                selectorStepEntered[0] = true;
                perturbation.stepStarted(step);
              })) {
            discardSetup = true;
            if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
            if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
            return ShakeOutcome.FAILED;
          }
          try (var cursor = ledger.openCursor(perturbation::iterator)) {
            for (var attempt = cursor.next(); attempt != null; attempt = cursor.next()) {
              if (attempt.isMarker()) {
                ledger.consume(attempt);
                continue;
              }
              boolean selected;
              try {
                selected = evaluatePerturbation(step, attempt.selection(), contexts);
              } catch (CancellationException cancelled) {
                if (cancelled.getSuppressed().length != 0) throw cancelled;
                if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
                if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
                throw cancelled;
              }
              ledger.consume(attempt);
              if (selected) {
                if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
                if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
                if (!episodes.cancelAndQuiesce()) return ShakeOutcome.INTERRUPTED;
                captureStepString(step, step.getMove(), step.getScore());
                scope.getScoreDirector().executeMove(step.getMove());
                finishStep(step);
                committed = true;
                perturbation.stepEnded(step);
                episodes.replayWorkingState(step.getMove(), step.getScore());
                successful++;
                break;
              }
              if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
              if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
            }
          }
        } catch (RuntimeException | Error thrown) {
          stepFailure = thrown;
          throw thrown;
        } finally {
          if (!committed) {
            Throwable abortFailure = null;
            if (selectorStepEntered[0]) {
              try {
                perturbation.stepAborted(step);
              } catch (RuntimeException | Error thrown) {
                abortFailure = thrown;
              }
            }
            if (discardSetup) {
              try {
                discardPerturbationSetup(scope);
              } catch (RuntimeException | Error thrown) {
                abortFailure = append(abortFailure, thrown);
              }
            }
            try {
              scope.getSolverScope().getSolver().stepAborted(step);
            } catch (RuntimeException | Error thrown) {
              abortFailure = append(abortFailure, thrown);
            }
            if (abortFailure != null) {
              if (stepFailure != null) {
                if (stepFailure != abortFailure) stepFailure.addSuppressed(abortFailure);
              } else if (abortFailure instanceof RuntimeException runtime) throw runtime;
              else throw (Error) abortFailure;
            }
          }
        }
        if (!committed) break;
      }
      if (isTerminated(scope)) return ShakeOutcome.INTERRUPTED;
      if (scope.getSolverScope().hasPendingMove()) return ShakeOutcome.ADOPTION;
      if (successful < scope.strength) return ShakeOutcome.FAILED;
      return incumbent.assignments().matchesCurrent(scope.getScoreDirector())
          ? ShakeOutcome.NO_CHANGE
          : ShakeOutcome.COMPLETE;
    } catch (RuntimeException | Error thrown) {
      failure = thrown;
      throw thrown;
    } finally {
      Throwable cleanupFailure = null;
      try {
        scope.perturbationAttempts += ledger.getConsumedCount();
        scope.speculativeSelectionAttempts += ledger.getDiscardedCount();
        metrics.update(scope);
      } catch (RuntimeException | Error thrown) {
        cleanupFailure = thrown;
      }
      try {
        closePerturbationContexts(scope, contexts);
      } catch (RuntimeException | Error thrown) {
        cleanupFailure = append(cleanupFailure, thrown);
      }
      if (cleanupFailure != null) {
        if (failure != null) {
          if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
        } else if (cleanupFailure instanceof RuntimeException runtime) throw runtime;
        else throw (Error) cleanupFailure;
      }
    }
  }

  private void closePerturbationContexts(
      IteratedLocalSearchPhaseScope<Solution_> scope,
      Map<Object, PreparableMove<Solution_>> contexts) {
    if (contexts.isEmpty()) return;
    Throwable failure = null;
    try {
      episodes.cancelAndQuiesce();
    } catch (RuntimeException | Error thrown) {
      failure = thrown;
    }
    if (episodes.isEvaluationStateSafeToDispose()) {
      var iterator = contexts.values().iterator();
      while (iterator.hasNext()) {
        var context = iterator.next();
        iterator.remove();
        try {
          context.closeEvaluationContext(scope.getScoreDirector());
        } catch (RuntimeException | Error thrown) {
          failure = append(failure, thrown);
        }
      }
    }
    // An unsuccessful join leaves these entries owned by the outer deferred cleanup.
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  private void discardPerturbationSetup(IteratedLocalSearchPhaseScope<Solution_> scope) {
    Throwable failure = null;
    if (scope.perturbationPhaseStarted) {
      scope.perturbationPhaseStarted = false;
      try {
        perturbation.phaseEnded(scope);
      } catch (RuntimeException | Error thrown) {
        failure = thrown;
      }
    }
    if (scope.perturbationSolvingStarted) {
      scope.perturbationSolvingStarted = false;
      try {
        perturbation.solvingEnded(scope.getSolverScope());
      } catch (RuntimeException | Error thrown) {
        failure = append(failure, thrown);
      }
    }
    if (failure instanceof RuntimeException runtime) throw runtime;
    if (failure instanceof Error error) throw error;
  }

  private <Score_ extends Score<Score_>> boolean evaluatePerturbation(
      IteratedLocalSearchStepScope<Solution_> step,
      Move<Solution_> proposal,
      Map<Object, PreparableMove<Solution_>> contexts) {
    var scope = step.getPhaseScope();
    var director = scope.<Score_>getScoreDirector();
    Move<Solution_> realized = proposal;
    InnerScore<Score_> score;
    @SuppressWarnings("unchecked")
    SolutionAssignments<Solution_>[] target = new SolutionAssignments[1];
    if (proposal instanceof PreparableMove<Solution_> preparable) {
      contexts.putIfAbsent(preparable.cleanupKey(), preparable);
      var prepared =
          preparable.prepare(
              director,
              () -> {
                checkTermination(scope);
                if (scope.getSolverScope().hasPendingMove())
                  throw new CancellationException("Perturbation superseded by migration.");
              },
              environmentMode.isFullyAsserted(),
              (view, move) -> target[0] = capture(scope));
      if (prepared.status() != PreparedMoveEvaluation.Status.EVALUATED) return false;
      realized = PreparedMoveFilters.filter(prepared.move(), director);
      if (realized == null) return false;
      score = prepared.score();
    } else {
      if (proposal instanceof AbstractSelectorBasedMove<Solution_> selectorMove
          && !selectorMove.isMoveDoable(director)) return false;
      score =
          director.executeTemporaryMove(
              proposal, view -> target[0] = capture(scope), environmentMode.isFullyAsserted());
    }
    scope.addMoveEvaluationCount(realized, 1L);
    step.setSelectedMoveCount(step.getSelectedMoveCount() + 1);
    if (!score.isFullyAssigned() || score.isStructurallyFlawed()) return false;
    if (target[0] == null) {
      throw new IllegalStateException(
          "The prepared perturbation move ("
              + proposal
              + ") did not materialize its final assignment snapshot.");
    }
    if (target[0].matchesCurrent(director)) return false;
    try {
      target[0].validateComplete(director);
    } catch (IllegalStateException | IllegalArgumentException invalidProposal) {
      logger.trace(
          "{}  Invalid perturbation proposal ({}): {}",
          logIndentation,
          proposal,
          invalidProposal.getMessage());
      return false;
    }
    step.setMove(realized);
    step.setScore(score);
    step.setAcceptedMoveCount(1L);
    return true;
  }

  private void finishStep(IteratedLocalSearchStepScope<Solution_> step) {
    requireComplete(step.getScore(), step.getMove());
    predictWorkingStepScore(step, step.getMove());
    bestSolutionRecaller.processWorkingSolutionDuringStep(step);
    stepEnded(step);
    step.getPhaseScope().setLastCompletedStepScope(step);
    metrics.record(step);
    SolverMetricSamples.publishIslandStep(
        step.getPhaseScope().getSolverScope(), step, episodes.getUncreditedCalculationCount());
    logStep(step);
  }

  private void captureStepString(
      IteratedLocalSearchStepScope<Solution_> step, Move<Solution_> move, InnerScore<?> score) {
    if (logger.isDebugEnabled()
        && (stepLoggingMode == LocalSearchStepLoggingMode.ALL
            || compare(score, step.getPhaseScope().getBestScore()) > 0)) {
      // Move descriptions can read planning values; capture them before execution.
      // The recaller confirms strict solver-best improvement before the step is logged.
      step.setStepString(move.toString());
    }
  }

  private void logStep(IteratedLocalSearchStepScope<Solution_> step) {
    if (!logger.isDebugEnabled()
        || (stepLoggingMode == LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED
            && !step.getBestScoreImproved())) {
      return;
    }
    var scope = step.getPhaseScope();
    var islandSuffix = "";
    for (var tag : scope.getSolverScope().getMonitoringTags()) {
      if (tag.getKey().equals("island.id") && !tag.getValue().equals("root")) {
        islandSuffix = ", island (" + tag.getValue() + ")";
        break;
      }
    }
    logger.debug(
        "{}    ILS step ({}), time spent ({}), score ({}), {} best score ({}),"
            + " accepted/selected move count ({}/{}), picked move ({}), phase ({}), origin ({}), strength ({}){}.",
        logIndentation,
        step.getStepIndex(),
        scope.calculateSolverTimeMillisSpentUpToNow(),
        step.getScore().raw(),
        step.getBestScoreImproved() ? "new" : "   ",
        scope.getBestScore().raw(),
        step.getAcceptedMoveCount(),
        step.getSelectedMoveCount(),
        step.getStepString(),
        phaseIndex,
        step.getOrigin(),
        scope.strength,
        islandSuffix);
  }

  private static Object logScore(InnerScore<?> score) {
    return score == null ? "unavailable" : score.raw();
  }

  private void restore(
      IteratedLocalSearchPhaseScope<Solution_> scope, Incumbent<Solution_> incumbent) {
    checkTermination(scope);
    if (incumbent.assignments().matchesCurrent(scope.getScoreDirector())) {
      refreshWorkingScore(scope, incumbent.score());
      return;
    }
    long start = now();
    if (!episodes.cancelAndQuiesce())
      throw new CancellationException("Incumbent restoration interrupted before quiescence.");
    checkTermination(scope);
    var move = new SolutionAssignmentMove<>(incumbent.assignments());
    scope.getScoreDirector().executeMove(move);
    var actual = scope.calculateScore();
    requireComplete(actual, "restored incumbent");
    if (environmentMode.isAsserted() && !actual.equals(incumbent.score())) {
      throw new IllegalStateException(
          "The restored incumbent score ("
              + actual
              + ") differs from its retained score ("
              + incumbent.score()
              + ").");
    }
    episodes.replayWorkingState(move, actual);
    refreshWorkingScore(scope, actual);
    scope.restorationNanos += elapsed(start);
  }

  private Incumbent<Solution_> adoptPending(IteratedLocalSearchPhaseScope<Solution_> scope) {
    var pending = scope.getSolverScope().consumePendingMove();
    if (pending == null
        || (pending.score() != null && compare(pending.score(), scope.getBestScore()) <= 0))
      return null;
    checkTermination(scope);
    if (!episodes.cancelAndQuiesce())
      throw new CancellationException("Island adoption interrupted before quiescence.");
    if (pending.score() != null && compare(pending.score(), scope.getBestScore()) <= 0) return null;
    var director = scope.getScoreDirector();
    Move<Solution_> move;
    try {
      move =
          pending.move() instanceof SolutionSyncMove<Solution_> legacy
              ? legacy.toStrictMove(director)
              : pending.move();
      if (move instanceof SolutionAssignmentMove<Solution_> assignmentMove
          && !assignmentMove.isMoveDoable(director)) return null;
    } catch (IllegalStateException | IllegalArgumentException staleMigrant) {
      logger.debug(
          "{}  Rejected incompatible island assignments: {}",
          logIndentation,
          staleMigrant.getMessage());
      return null;
    }
    // Validate by temporary execution before installing an external state. Its fresh native score
    // is authoritative; cached migrant scores are only an eligibility hint.
    var candidate = director.executeTemporaryMove(move, environmentMode.isFullyAsserted());
    requireComplete(candidate, "incoming island assignments");
    if (compare(candidate, scope.getBestScore()) <= 0) return null;
    checkTermination(scope);
    director.executeMove(move);
    var actual = scope.calculateScore();
    requireComplete(actual, "adopted island assignments");
    var adoption =
        new IteratedLocalSearchStepScope<>(
            scope,
            scope.getLastCompletedStepScope().getStepIndex(),
            IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH);
    adoption.setScore(actual);
    bestSolutionRecaller.processWorkingSolutionDuringStep(adoption);
    GlobalBestUpdater.publishCurrentBestToGlobal(scope.getSolverScope());
    if (adoption.getBestScoreImproved()) phaseTermination.bestScoreImproved(adoption);
    refreshWorkingScore(scope, actual);
    metrics.incumbentChanged(scope, adoption.getBestScoreImproved());
    episodes.replayWorkingState(move, actual);
    return snapshot(scope, actual);
  }

  private void refreshWorkingScore(
      IteratedLocalSearchPhaseScope<Solution_> scope, InnerScore<?> score) {
    var last = scope.getLastCompletedStepScope();
    var refreshed =
        new IteratedLocalSearchStepScope<>(scope, last.getStepIndex(), last.getOrigin());
    refreshed.setScore(score);
    scope.setLastCompletedStepScope(refreshed);
  }

  private Incumbent<Solution_> snapshot(
      IteratedLocalSearchPhaseScope<Solution_> scope, InnerScore<?> score) {
    return new Incumbent<>(capture(scope), score);
  }

  private SolutionAssignments<Solution_> capture(IteratedLocalSearchPhaseScope<Solution_> scope) {
    long start = now();
    var snapshot =
        SolutionAssignments.captureComplete(
            scope.getSolutionDescriptor(), scope.getWorkingSolution());
    scope.snapshotNanos += elapsed(start);
    return snapshot;
  }

  private boolean isTerminated(IteratedLocalSearchPhaseScope<Solution_> scope) {
    scope.getSolverScope().checkYielding();
    return Thread.currentThread().isInterrupted() || phaseTermination.isPhaseTerminated(scope);
  }

  private void checkTermination(IteratedLocalSearchPhaseScope<Solution_> scope) {
    if (isTerminated(scope))
      throw new CancellationException("Iterated Local Search enclosing termination reached.");
  }

  private void resetStrength(IteratedLocalSearchPhaseScope<Solution_> scope) {
    scope.strengthIndex = 0;
    scope.strength = config.getPerturbationStrengths().getFirst();
  }

  private void advanceStrength(IteratedLocalSearchPhaseScope<Solution_> scope) {
    scope.strengthIndex = (scope.strengthIndex + 1) % config.getPerturbationStrengths().size();
    scope.strength = config.getPerturbationStrengths().get(scope.strengthIndex);
  }

  private void logIteration(
      IteratedLocalSearchPhaseScope<Solution_> scope,
      String outcome,
      InnerScore<?> candidate,
      InnerScore<?> incumbent) {
    metrics.update(scope);
    logger.debug(
        "{}  ILS iteration ({}) {}: strength ({}), candidate ({}), incumbent ({}), solver best ({}), attempts ({}/{}), elapsed ({} ms).",
        logIndentation,
        scope.completedIterations,
        outcome,
        scope.strength,
        logScore(candidate),
        incumbent.raw(),
        scope.getBestScore().raw(),
        scope.perturbationAttempts,
        scope.episodeAttempts,
        scope.calculatePhaseTimeMillisSpentUpToNow());
  }

  private Diagnostics diagnostics(IteratedLocalSearchPhaseScope<Solution_> scope) {
    var assignments = scope.assignmentDiagnostics;
    return new Diagnostics(
        scope.episodes,
        scope.initialEpisodes,
        scope.completedIterations,
        scope.acceptedIterations,
        scope.rejectedIterations,
        scope.failedPerturbations,
        scope.noChangeCount,
        scope.interruptedIterations,
        scope.migrantRestarts,
        scope.perturbationAttempts,
        scope.episodeAttempts,
        scope.speculativeSelectionAttempts,
        scope.getNextStepIndex(),
        scope.getPhaseScoreCalculationCount(),
        scope.evaluationResourcesStarted ? episodes.getConsumedWorkerCalculationCount() : 0L,
        scope.evaluationResourcesStarted ? episodes.getAdditionalWorkerCalculationCount() : 0L,
        scope.evaluationResourcesStarted ? episodes.getWorkerStartupCount() : 0L,
        moveWorkers,
        scope.snapshotNanos + (scope.evaluationResourcesStarted ? episodes.getSnapshotNanos() : 0L),
        scope.restorationNanos,
        scope.resourceSetupNanos,
        scope.evaluationResourcesStarted ? episodes.getEpisodeSetupNanos() : 0L,
        assignments == null ? 0L : assignments.getCaptureCount(),
        assignments == null
            ? 0L
            : assignments.getCapturedBindingCount() + assignments.getAccumulatorBindingCount(),
        assignments == null
            ? 0L
            : assignments.getCopiedListElementCount()
                + assignments.getAccumulatorListElementCount(),
        assignments == null ? 0L : assignments.getCaptureNanos(),
        assignments == null ? 0L : assignments.getComparisonCount(),
        assignments == null ? 0L : assignments.getComparisonNanos(),
        assignments == null ? 0L : assignments.getValidationCount(),
        assignments == null ? 0L : assignments.getValidationNanos(),
        assignments == null ? 0L : assignments.getAccumulatorUpdateCount(),
        scope.completionReason);
  }

  private long now() {
    return timingDiagnostics ? System.nanoTime() : 0L;
  }

  private long elapsed(long start) {
    return timingDiagnostics ? System.nanoTime() - start : 0L;
  }

  private static void requireComplete(InnerScore<?> score, Object context) {
    if (!score.isFullyAssigned() || score.isStructurallyFlawed()) {
      throw new IllegalStateException(
          "Iterated Local Search requires an initialized, structurally valid "
              + context
              + "; score ("
              + score
              + ").");
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int compare(InnerScore<?> left, InnerScore<?> right) {
    return ((InnerScore) left).compareTo((InnerScore) right);
  }

  private static Throwable append(Throwable first, Throwable addition) {
    if (first == null) return addition;
    if (first != addition) first.addSuppressed(addition);
    return first;
  }

  public static final class Builder<Solution_>
      extends AbstractPhaseBuilder<Solution_, DefaultIteratedLocalSearchPhase<Solution_>> {
    private final IteratedLocalSearchPhaseConfig config;
    private final LocalSearchEpisodeRunner<Solution_> episodes;
    private final MoveSelector<Solution_> perturbation;
    private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private final Integer moveWorkers;

    public Builder(
        int phaseIndex,
        EnvironmentMode environmentMode,
        String logIndentation,
        PhaseTermination<Solution_> termination,
        IteratedLocalSearchPhaseConfig config,
        LocalSearchEpisodeRunner<Solution_> episodes,
        MoveSelector<Solution_> perturbation,
        BestSolutionRecaller<Solution_> bestSolutionRecaller,
        Integer moveWorkers) {
      super(phaseIndex, environmentMode, logIndentation, termination);
      this.config = config;
      this.episodes = episodes;
      this.perturbation = perturbation;
      this.bestSolutionRecaller = bestSolutionRecaller;
      this.moveWorkers = moveWorkers;
    }

    @Override
    public DefaultIteratedLocalSearchPhase<Solution_> build() {
      return new DefaultIteratedLocalSearchPhase<>(this);
    }
  }
}
