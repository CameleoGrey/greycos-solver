package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.function.IntFunction;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.api.solver.alns.AlnsVariable;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.phase.AbstractPhase;
import greycos.solver.core.impl.phase.PhaseType;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;

/** One candidate construction and one acceptance decision per completed ALNS iteration. */
public final class DefaultAlnsPhase<Solution_> extends AbstractPhase<Solution_>
    implements AlnsPhase<Solution_> {
  private final AlnsPhaseConfig config;
  private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final Integer moveThreadCount;
  private final int moveThreadBufferSize;
  private final ThreadFactory threadFactory;
  private final EnvironmentMode environmentMode;
  private AlnsMetrics<Solution_> metrics;
  private MoveEvaluationPipeline.Diagnostics moveEvaluationDiagnostics;

  private DefaultAlnsPhase(Builder<Solution_> builder) {
    super(builder);
    config = builder.config;
    bestSolutionRecaller = builder.bestSolutionRecaller;
    moveThreadCount = builder.moveThreadCount;
    moveThreadBufferSize = builder.moveThreadBufferSize;
    threadFactory = builder.threadFactory;
    environmentMode = builder.environmentMode;
  }

  public MoveEvaluationPipeline.Diagnostics getMoveEvaluationDiagnostics() {
    return moveEvaluationDiagnostics;
  }

  @Override
  public PhaseType getPhaseType() {
    return PhaseType.ALNS;
  }

  @Override
  public IntFunction<EventProducerId> getEventProducerIdSupplier() {
    return EventProducerId::alns;
  }

  @Override
  public void solve(SolverScope<Solution_> solverScope) {
    solveTyped(solverScope);
  }

  private <Score_ extends Score<Score_>> void solveTyped(SolverScope<Solution_> solverScope) {
    validate();
    var scope = new AlnsPhaseScope<>(solverScope, phaseIndex);
    metrics = new AlnsMetrics<>();
    InnerScoreDirector<Solution_, Score_> director = solverScope.getScoreDirector();
    var random = solverScope.getWorkingRandom().moveIteratorUsage();
    var budget = new TrialBudget(director);
    phaseStarted(scope);
    Throwable phaseFailure = null;
    DefaultAlnsContext<Solution_, Score_> context = null;
    moveEvaluationDiagnostics = null;
    try (var resources = new ResourceScope();
        var ownedContext =
            context =
                new DefaultAlnsContext<Solution_, Score_>(
                    director,
                    random,
                    () -> isPhaseTerminatedAfterYielding(scope) || budget.exhausted())) {
      context.configureMoveThreads(
          moveThreadCount, moveThreadBufferSize, threadFactory, phaseIndex, environmentMode);
      var initial = director.calculateScore();
      if (!initial.isFullyAssigned()) {
        throw new IllegalStateException(
            "ALNS requires an initialized solution; configure a construction heuristic first.");
      }
      scope.getLastCompletedStepScope().setScore(initial);
      metrics.phaseStarted(scope);
      var destroys = buildDestroys(context, resources);
      var repairs = this.<Score_>buildRepairs(context, resources);
      validatePairScopes(context, destroys, repairs);
      AlnsSelectionPolicy<Score_> selection =
          resources.own(buildSelection(destroys, repairs, resources));
      AlnsAcceptancePolicy<Score_> acceptance = resources.own(buildAcceptance(director, resources));
      acceptance.initialize(initial.raw());
      while (!isPhaseTerminatedAfterYielding(scope)) {
        adoptPending(scope, acceptance, context);
        var eligible = eligiblePairs(context, destroys, repairs);
        if (eligible.isEmpty()) {
          break;
        }
        var pair = selection.select(eligible, random);
        if (!eligible.contains(pair)) {
          throw new IllegalArgumentException(
              "ALNS selection policy returned an ineligible pair: " + pair);
        }
        var destroy = destroys.get(pair.destroyId());
        var repair = repairs.get(pair.repairId());
        var step = new AlnsStepScope<>(scope);
        stepStarted(step);
        Score_ before = director.calculateScore().raw();
        Score_ bestBefore = solverScope.<Score_>getBestScore().raw();
        Score_ candidate = null;
        var outcome = AlnsOutcome.REPAIR_FAILED;
        int destroyedCount = 0;
        int recoveryCount = 0;
        budget.start();
        context.beginTrial();
        try {
          var pool = scopedTargets(context.targets(), destroy.config());
          int requested = destructionSize(destroy.config(), pool, random);
          var proposed = destroy.operator().select(context, requested);
          if (context.isChanged()) {
            throw new IllegalStateException(
                "ALNS destroy selection must leave the incumbent unchanged; the framework applies destruction.");
          }
          var proposedSet = new LinkedHashSet<>(proposed);
          if (proposedSet.size() != proposed.size()
              || proposedSet.size() > requested
              || !pool.containsAll(proposedSet)) {
            throw new IllegalArgumentException(
                "ALNS destroy operator returned duplicate, ineligible or too many targets.");
          }
          // Canonical identity order belongs to the framework, repair ordering to its operator.
          var destroyed = pool.stream().filter(proposedSet::contains).toList();
          destroyedCount = destroyed.size();
          var recovery =
              recoveryTargets(context, destroy.config(), repair.config(), destroyedCount, random);
          recoveryCount = recovery.size();
          var pending = new ArrayList<>(destroyed);
          pending.addAll(recovery);
          context.setPendingTargets(pending);
          context.destroy(destroyed);
          boolean repaired = repair.operator().repair(context, List.copyOf(pending));
          if (repaired) {
            var evaluation = context.score();
            candidate = evaluation.score();
            if (evaluation.isComplete()) {
              if (!context.isChanged()) {
                outcome = AlnsOutcome.NO_CHANGE;
              } else if (acceptance.isAccepted(
                  before, candidate, solverScope.getWorkingRandom().acceptorUsage())) {
                outcome =
                    candidate.compareTo(bestBefore) > 0
                        ? AlnsOutcome.NEW_BEST
                        : candidate.compareTo(before) > 0
                            ? AlnsOutcome.IMPROVED
                            : AlnsOutcome.ACCEPTED;
              } else {
                outcome = AlnsOutcome.REJECTED;
              }
            }
          }
          context.checkTermination();
          if (accepted(outcome)) {
            context.commit();
          } else {
            context.rollback();
          }
        } catch (AlnsTerminationException interrupted) {
          context.rollback();
          outcome =
              phaseTermination.isPhaseTerminated(scope) || Thread.currentThread().isInterrupted()
                  ? AlnsOutcome.CANCELLED
                  : AlnsOutcome.REPAIR_FAILED;
        } catch (RuntimeException | Error failure) {
          try {
            context.rollback();
          } catch (RuntimeException | Error rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
          }
          throw failure;
        } finally {
          budget.stop();
        }
        Score_ after = accepted(outcome) ? candidate : before;
        step.setScore(InnerScore.fullyAssigned(after));
        predictWorkingStepScore(step, "ALNS " + pair);
        if (accepted(outcome)) {
          bestSolutionRecaller.processWorkingSolutionDuringStep(step);
        }
        var result =
            new AlnsTrialResult<>(
                step.getStepIndex(),
                pair.destroyId(),
                pair.repairId(),
                outcome,
                before,
                candidate,
                after,
                bestBefore,
                solverScope.<Score_>getBestScore().raw(),
                destroyedCount,
                recoveryCount,
                context.probeCount(),
                budget.elapsedNanos());
        step.setTrialResult(result);
        metrics.record(step);
        logTrial(step, result);
        if (outcome == AlnsOutcome.CANCELLED) {
          break;
        }
        // Count failed/no-change attempts as well, so a move-count-only limit always progresses.
        solverScope.addMoveEvaluationCount(1);
        if (solverScope.isMetricEnabled(SolverMetric.MOVE_COUNT_PER_TYPE)) {
          solverScope.addMoveEvaluationCountPerType(step.getOperatorPairId(), 1);
        }
        selection.update(result);
        acceptance.stepEnded(after);
        metrics.recordWeights(solverScope, selection.weights());
        stepEnded(step);
        scope.setLastCompletedStepScope(step);
      }
    } catch (AlnsTerminationException cancelled) {
      if (!phaseTermination.isPhaseTerminated(scope) && !Thread.currentThread().isInterrupted()) {
        phaseFailure = cancelled;
        throw cancelled;
      }
    } catch (RuntimeException | Error failure) {
      phaseFailure = failure;
      throw failure;
    } finally {
      if (context != null) {
        scope.addChildThreadsScoreCalculationCount(context.additionalCalculationCount());
        moveEvaluationDiagnostics = context.moveEvaluationDiagnostics();
        if (moveEvaluationDiagnostics != null) {
          logger.debug(
              "{}ALNS move evaluation diagnostics: {}", logIndentation, moveEvaluationDiagnostics);
        }
      }
      finishPhase(scope, phaseFailure);
    }
    logger.info(
        "{}ALNS phase ({}) ended: time spent ({}), best score ({}), trials ({}).",
        logIndentation,
        phaseIndex,
        solverScope.calculateTimeMillisSpentUpToNow(),
        solverScope.getBestScore(),
        scope.getNextStepIndex());
  }

  private <Score_ extends Score<Score_>> void logTrial(
      AlnsStepScope<Solution_> step, AlnsTrialResult<Score_> result) {
    if (!logger.isDebugEnabled()) {
      return;
    }
    var scope = step.getPhaseScope();
    var islandSuffix = "";
    for (var tag : scope.getSolverScope().getMonitoringTags()) {
      if (tag.getKey().equals("island.id")) {
        islandSuffix = ", island (" + tag.getValue() + ")";
        break;
      }
    }
    // Use the settled trial result, including the restored incumbent after rejection/cancellation.
    // Insertion probes do not log or advance the trial index independently.
    logger.debug(
        "{}    ALNS trial ({}), phase ({}), time spent ({}), score ({}), {} best score ({}),"
            + " candidate score ({}), outcome ({}), operator pair ({}),"
            + " destroyed/recovery count ({}/{}), probes ({}), trial time ({} ms){}.",
        logIndentation,
        result.trialIndex(),
        phaseIndex,
        scope.calculateSolverTimeMillisSpentUpToNow(),
        result.afterScore(),
        result.bestAfterScore().compareTo(result.bestBeforeScore()) > 0 ? "new" : "   ",
        result.bestAfterScore(),
        result.candidateScore() == null ? "unavailable" : result.candidateScore(),
        result.outcome(),
        step.getOperatorPairId(),
        result.destroyedCount(),
        result.recoveryCount(),
        result.probeCount(),
        result.elapsedNanos() / 1_000_000.0,
        islandSuffix);
  }

  private void finishPhase(AlnsPhaseScope<Solution_> scope, Throwable originalFailure) {
    Throwable cleanupFailure = null;
    for (Runnable action :
        List.<Runnable>of(
            scope::endingNow, () -> metrics.phaseEnded(scope), () -> phaseEnded(scope))) {
      try {
        action.run();
      } catch (RuntimeException | Error failure) {
        if (cleanupFailure == null) {
          cleanupFailure = failure;
        } else if (cleanupFailure != failure) {
          cleanupFailure.addSuppressed(failure);
        }
      }
    }
    if (cleanupFailure != null) {
      if (originalFailure != null) {
        if (originalFailure != cleanupFailure) {
          originalFailure.addSuppressed(cleanupFailure);
        }
      } else if (cleanupFailure instanceof RuntimeException runtime) {
        throw runtime;
      } else {
        throw (Error) cleanupFailure;
      }
    }
  }

  private static boolean accepted(AlnsOutcome outcome) {
    return outcome == AlnsOutcome.NEW_BEST
        || outcome == AlnsOutcome.IMPROVED
        || outcome == AlnsOutcome.ACCEPTED;
  }

  private boolean isPhaseTerminatedAfterYielding(AlnsPhaseScope<Solution_> scope) {
    scope.getSolverScope().checkYielding();
    return phaseTermination.isPhaseTerminated(scope);
  }

  private <Score_ extends Score<Score_>> void adoptPending(
      AlnsPhaseScope<Solution_> scope,
      AlnsAcceptancePolicy<Score_> acceptance,
      DefaultAlnsContext<Solution_, Score_> context) {
    var pending = scope.getSolverScope().consumePendingMove();
    if (pending == null) {
      return;
    }
    scope.getScoreDirector().getMoveDirector().execute(pending.move());
    InnerScore<Score_> score = scope.calculateScore();
    context.incumbentChanged(pending.move(), score);
    var adoption = new AlnsStepScope<>(scope);
    adoption.setScore(score);
    bestSolutionRecaller.processWorkingSolutionDuringStep(adoption);
    if (adoption.getBestScoreImproved()) {
      phaseTermination.bestScoreImproved(adoption);
    }
    scope.getLastCompletedStepScope().setScore(score);
    acceptance.incumbentChanged(score.raw());
    metrics.incumbentChanged(scope, adoption.getBestScoreImproved());
  }

  private <Score_ extends Score<Score_>> Map<String, DestroyEntry<Solution_, Score_>> buildDestroys(
      AlnsContext<Solution_, Score_> context, ResourceScope resources) {
    var configs = config.getDestroyOperatorConfigList();
    if (configs == null) {
      configs = new ArrayList<>();
      for (var variable : context.variables()) {
        var prefix = variable.entityClass().getName() + "." + variable.variableName();
        configs.add(
            new AlnsDestroyOperatorConfig()
                .withId(prefix + ".random")
                .withType(AlnsDestroyOperatorType.RANDOM)
                .withEntityClass(variable.entityClass())
                .withVariableName(variable.variableName()));
        if (variable.isList()) {
          configs.add(
              new AlnsDestroyOperatorConfig()
                  .withId(prefix + ".block")
                  .withType(AlnsDestroyOperatorType.LIST_BLOCK)
                  .withEntityClass(variable.entityClass())
                  .withVariableName(variable.variableName()));
        }
      }
    }
    var result = new LinkedHashMap<String, DestroyEntry<Solution_, Score_>>();
    for (int i = 0; i < configs.size(); i++) {
      var operatorConfig = configs.get(i).copyConfig();
      var id = Objects.requireNonNullElse(operatorConfig.getId(), "destroy-" + i);
      operatorConfig.setId(id);
      validateBounds(operatorConfig);
      if (result.containsKey(id) || id.isBlank()) {
        throw new IllegalArgumentException("ALNS destroy IDs must be nonblank and unique: " + id);
      }
      if (context.variables().stream()
          .noneMatch(
              variable ->
                  matches(
                          variable,
                          operatorConfig.getEntityClass(),
                          operatorConfig.getVariableName())
                      && (operatorConfig.getType() != AlnsDestroyOperatorType.LIST_BLOCK
                          || variable.isList()))) {
        throw new IllegalArgumentException("ALNS destroy scope matches no genuine variable: " + id);
      }
      result.put(
          id,
          new DestroyEntry<>(
              operatorConfig,
              resources.own(BuiltinAlnsOperators.<Solution_, Score_>destroy(operatorConfig))));
    }
    return result;
  }

  private <Score_ extends Score<Score_>> Map<String, RepairEntry<Solution_, Score_>> buildRepairs(
      AlnsContext<Solution_, Score_> context, ResourceScope resources) {
    var configs = config.getRepairOperatorConfigList();
    if (configs == null) {
      configs =
          List.of(
              new AlnsRepairOperatorConfig()
                  .withId("greedy")
                  .withType(AlnsRepairOperatorType.GREEDY),
              new AlnsRepairOperatorConfig()
                  .withId("regret-2")
                  .withType(AlnsRepairOperatorType.REGRET_2));
    }
    var result = new LinkedHashMap<String, RepairEntry<Solution_, Score_>>();
    for (int i = 0; i < configs.size(); i++) {
      var operatorConfig = configs.get(i).copyConfig();
      var id = Objects.requireNonNullElse(operatorConfig.getId(), "repair-" + i);
      operatorConfig.setId(id);
      if (result.containsKey(id) || id.isBlank()) {
        throw new IllegalArgumentException("ALNS repair IDs must be nonblank and unique: " + id);
      }
      if (context.variables().stream()
          .noneMatch(
              variable ->
                  matches(
                      variable,
                      operatorConfig.getEntityClass(),
                      operatorConfig.getVariableName()))) {
        throw new IllegalArgumentException("ALNS repair scope matches no genuine variable: " + id);
      }
      result.put(
          id,
          new RepairEntry<>(
              operatorConfig,
              resources.own(BuiltinAlnsOperators.<Solution_, Score_>repair(operatorConfig))));
    }
    return result;
  }

  private <Score_ extends Score<Score_>> AlnsSelectionPolicy<Score_> buildSelection(
      Map<String, DestroyEntry<Solution_, Score_>> destroys,
      Map<String, RepairEntry<Solution_, Score_>> repairs,
      ResourceScope resources) {
    if (config.getSelectionPolicyClass() != null) {
      AlnsSelectionPolicy<Score_> policy =
          resources.own(
              ConfigUtils.newInstance(
                  config, "selectionPolicyClass", config.getSelectionPolicyClass()));
      ConfigUtils.applyCustomProperties(
          policy,
          "selectionPolicyClass",
          config.getSelectionPolicyCustomProperties(),
          "selectionPolicyCustomProperties");
      return policy;
    }
    var destroyWeights = new LinkedHashMap<String, Double>();
    destroys.forEach(
        (id, entry) ->
            destroyWeights.put(
                id, Objects.requireNonNullElse(entry.config().getInitialWeight(), 1.0)));
    var repairWeights = new LinkedHashMap<String, Double>();
    repairs.forEach(
        (id, entry) ->
            repairWeights.put(
                id, Objects.requireNonNullElse(entry.config().getInitialWeight(), 1.0)));
    return new DefaultAlnsSelection<>(
        Objects.requireNonNullElse(
            config.getSelectionPolicyType(), AlnsSelectionPolicyType.SEGMENTED_ROULETTE),
        destroyWeights,
        repairWeights,
        Objects.requireNonNullElse(config.getSegmentLength(), 100),
        Objects.requireNonNullElse(config.getReactionFactor(), .2),
        Objects.requireNonNullElse(config.getMinimumWeight(), .01),
        Objects.requireNonNullElse(config.getUcbExploration(), Math.sqrt(2)),
        Objects.requireNonNullElse(config.getNewBestReward(), 5.0),
        Objects.requireNonNullElse(config.getImprovedReward(), 3.0),
        Objects.requireNonNullElse(config.getAcceptedReward(), 1.0));
  }

  private <Score_ extends Score<Score_>> AlnsAcceptancePolicy<Score_> buildAcceptance(
      InnerScoreDirector<Solution_, Score_> director, ResourceScope resources) {
    if (config.getAcceptancePolicyClass() != null) {
      AlnsAcceptancePolicy<Score_> policy =
          resources.own(
              ConfigUtils.newInstance(
                  config, "acceptancePolicyClass", config.getAcceptancePolicyClass()));
      ConfigUtils.applyCustomProperties(
          policy,
          "acceptancePolicyClass",
          config.getAcceptancePolicyCustomProperties(),
          "acceptancePolicyCustomProperties");
      return policy;
    }
    Score_ temperature =
        config.getStartingTemperature() == null
            ? null
            : director.getScoreDefinition().parseScore(config.getStartingTemperature());
    return new DefaultAlnsAcceptance<>(
        Objects.requireNonNullElse(config.getAcceptanceType(), AlnsAcceptanceType.LATE_ACCEPTANCE),
        Objects.requireNonNullElse(config.getLateAcceptanceSize(), 128),
        temperature,
        Objects.requireNonNullElse(config.getCoolingRate(), .999));
  }

  private <Score_ extends Score<Score_>> List<AlnsOperatorPair> eligiblePairs(
      AlnsContext<Solution_, Score_> context,
      Map<String, DestroyEntry<Solution_, Score_>> destroys,
      Map<String, RepairEntry<Solution_, Score_>> repairs) {
    var result = new ArrayList<AlnsOperatorPair>();
    var targets = new ArrayList<>(context.targets());
    if (!Objects.equals(config.getRecoveryCount(), 0)) {
      targets.addAll(context.unassignedTargets());
    }
    for (var destroy : destroys.values()) {
      var scoped = scopedTargets(targets, destroy.config());
      if (destroy.config().getType() == AlnsDestroyOperatorType.LIST_BLOCK) {
        scoped = scoped.stream().filter(AlnsTarget::isList).toList();
      }
      if (scoped.isEmpty()) {
        continue;
      }
      for (var repair : repairs.values()) {
        if (scoped.stream()
            .allMatch(
                target ->
                    matchesTarget(
                        target,
                        repair.config().getEntityClass(),
                        repair.config().getVariableName()))) {
          result.add(new AlnsOperatorPair(destroy.config().getId(), repair.config().getId()));
        }
      }
    }
    return result;
  }

  private <Score_ extends Score<Score_>> void validatePairScopes(
      AlnsContext<Solution_, Score_> context,
      Map<String, DestroyEntry<Solution_, Score_>> destroys,
      Map<String, RepairEntry<Solution_, Score_>> repairs) {
    for (var destroy : destroys.values()) {
      var variables =
          context.variables().stream()
              .filter(
                  variable ->
                      matches(
                          variable,
                          destroy.config().getEntityClass(),
                          destroy.config().getVariableName()))
              .filter(
                  variable ->
                      destroy.config().getType() != AlnsDestroyOperatorType.LIST_BLOCK
                          || variable.isList())
              .toList();
      if (repairs.values().stream()
          .noneMatch(
              repair ->
                  variables.stream()
                      .allMatch(
                          variable ->
                              matches(
                                  variable,
                                  repair.config().getEntityClass(),
                                  repair.config().getVariableName())))) {
        throw new IllegalArgumentException(
            "ALNS destroy operator has no compatible repair: " + destroy.config().getId());
      }
    }
  }

  private static <S> List<AlnsTarget<S>> scopedTargets(
      List<AlnsTarget<S>> targets, AlnsDestroyOperatorConfig config) {
    return targets.stream()
        .filter(target -> matchesTarget(target, config.getEntityClass(), config.getVariableName()))
        .filter(target -> config.getType() != AlnsDestroyOperatorType.LIST_BLOCK || target.isList())
        .toList();
  }

  private static boolean matches(
      AlnsVariable<?> variable, Class<?> entityClass, String variableName) {
    return (entityClass == null
            || entityClass.isAssignableFrom(variable.entityClass())
            || variable.entityClass().isAssignableFrom(entityClass))
        && (variableName == null || variableName.equals(variable.variableName()));
  }

  private static boolean matchesTarget(
      AlnsTarget<?> target, Class<?> entityClass, String variableName) {
    return matches(target.variable(), entityClass, variableName)
        && (entityClass == null
            || target.entity() == null
            || entityClass.isInstance(target.entity()));
  }

  private <Score_ extends Score<Score_>> List<AlnsTarget<Solution_>> recoveryTargets(
      AlnsContext<Solution_, Score_> context,
      AlnsDestroyOperatorConfig destroy,
      AlnsRepairOperatorConfig repair,
      int destroyedCount,
      RandomGenerator random) {
    var targets =
        new ArrayList<>(
            scopedTargets(context.unassignedTargets(), destroy).stream()
                .filter(
                    target ->
                        matchesTarget(target, repair.getEntityClass(), repair.getVariableName()))
                .toList());
    int limit =
        Math.min(
            targets.size(),
            Objects.requireNonNullElse(config.getRecoveryCount(), Math.max(1, destroyedCount)));
    for (int i = 0; i < limit; i++) {
      Collections.swap(targets, i, i + random.nextInt(targets.size() - i));
    }
    return List.copyOf(targets.subList(0, limit));
  }

  private static int destructionSize(
      AlnsDestroyOperatorConfig config,
      List<? extends AlnsTarget<?>> targets,
      RandomGenerator random) {
    int n = targets.size();
    if (n == 0) {
      return 0;
    }
    int minimum =
        config.getMinimumDestroyedCount() != null
            ? config.getMinimumDestroyedCount()
            : config.getMinimumDestroyedPercentage() != null
                ? (int) Math.floor(config.getMinimumDestroyedPercentage() * n)
                : 5;
    int maximum =
        config.getMaximumDestroyedCount() != null
            ? config.getMaximumDestroyedCount()
            : config.getMaximumDestroyedPercentage() != null
                ? (int) Math.floor(config.getMaximumDestroyedPercentage() * n)
                : targets.stream().anyMatch(AlnsTarget::isList) ? 40 : 20;
    minimum = Math.min(n, Math.max(1, minimum));
    maximum = Math.min(n, Math.max(1, maximum));
    if (minimum > maximum) {
      if (config.getMinimumDestroyedCount() == null
          && config.getMinimumDestroyedPercentage() == null) {
        minimum = maximum;
      } else if (config.getMaximumDestroyedCount() == null
          && config.getMaximumDestroyedPercentage() == null) {
        maximum = minimum;
      } else {
        throw new IllegalArgumentException(
            "ALNS destroy operator (%s) has incompatible destruction bounds (%d..%d) for %d eligible targets."
                .formatted(config.getId(), minimum, maximum, n));
      }
    }
    return (int) random.nextLong(minimum, (long) maximum + 1);
  }

  private void validate() {
    if (config.getRecoveryCount() != null && config.getRecoveryCount() < 0
        || config.getRepairScoreCalculationLimit() != null
            && config.getRepairScoreCalculationLimit() < 1
        || config.getRepairSpentLimit() != null
            && (config.getRepairSpentLimit().isNegative()
                || config.getRepairSpentLimit().isZero())) {
      throw new IllegalArgumentException(
          "ALNS recovery must be nonnegative and repair limits positive.");
    }
    if (config.getDestroyOperatorConfigList() != null
            && config.getDestroyOperatorConfigList().isEmpty()
        || config.getRepairOperatorConfigList() != null
            && config.getRepairOperatorConfigList().isEmpty()) {
      throw new IllegalArgumentException("Explicit ALNS operator portfolios must not be empty.");
    }
    if (config.getSelectionPolicyClass() != null && config.getSelectionPolicyType() != null
        || config.getAcceptancePolicyClass() != null && config.getAcceptanceType() != null) {
      throw new IllegalArgumentException(
          "Configure either an ALNS policy class or its built-in type.");
    }
    if (config.getSelectionPolicyClass() == null
            && config.getSelectionPolicyCustomProperties() != null
        || config.getAcceptancePolicyClass() == null
            && config.getAcceptancePolicyCustomProperties() != null) {
      throw new IllegalArgumentException("ALNS custom policy properties require a policy class.");
    }
  }

  private static void validateBounds(AlnsDestroyOperatorConfig config) {
    Integer min = config.getMinimumDestroyedCount();
    Integer max = config.getMaximumDestroyedCount();
    Double minPercentage = config.getMinimumDestroyedPercentage();
    Double maxPercentage = config.getMaximumDestroyedPercentage();
    if (min != null && (min < 1 || minPercentage != null)
        || max != null && (max < 1 || maxPercentage != null)
        || min != null && max != null && min > max) {
      throw new IllegalArgumentException("Invalid ALNS destruction count bounds.");
    }
    for (var percentage : new Double[] {minPercentage, maxPercentage}) {
      if (percentage != null
          && (!Double.isFinite(percentage) || percentage <= 0 || percentage > 1)) {
        throw new IllegalArgumentException("ALNS destruction percentages must be in (0, 1].");
      }
    }
    if (minPercentage != null && maxPercentage != null && minPercentage > maxPercentage) {
      throw new IllegalArgumentException(
          "ALNS minimum destruction percentage exceeds its maximum.");
    }
  }

  private static final class ResourceScope implements AutoCloseable {
    private final List<AutoCloseable> resources = new ArrayList<>();

    <T> T own(T resource) {
      if (resource instanceof AutoCloseable closeable
          && resources.stream().noneMatch(item -> item == closeable)) {
        resources.add(closeable);
      }
      return resource;
    }

    @Override
    public void close() {
      RuntimeException failure = null;
      for (var closeable : resources.reversed()) {
        try {
          closeable.close();
        } catch (Exception exception) {
          if (failure == null) {
            failure =
                new IllegalStateException("Failed to close an ALNS operator or policy.", exception);
          } else {
            failure.addSuppressed(exception);
          }
        }
      }
      if (failure != null) {
        throw failure;
      }
    }
  }

  private record DestroyEntry<S, Score_ extends Score<Score_>>(
      AlnsDestroyOperatorConfig config, AlnsDestroyOperator<S, Score_> operator) {}

  private record RepairEntry<S, Score_ extends Score<Score_>>(
      AlnsRepairOperatorConfig config, AlnsRepairOperator<S, Score_> operator) {}

  private final class TrialBudget {
    private final InnerScoreDirector<Solution_, ?> director;
    private long initialProbes;
    private long initialTime;
    private long finalElapsed;
    private boolean active;

    TrialBudget(InnerScoreDirector<Solution_, ?> director) {
      this.director = director;
    }

    void start() {
      initialProbes = director.getCalculationCount();
      initialTime = System.nanoTime();
      active = true;
    }

    boolean exhausted() {
      return active
          && (config.getRepairScoreCalculationLimit() != null
                  && director.getCalculationCount() - initialProbes
                      >= config.getRepairScoreCalculationLimit()
              || config.getRepairSpentLimit() != null
                  && System.nanoTime() - initialTime >= config.getRepairSpentLimit().toNanos());
    }

    void stop() {
      finalElapsed = System.nanoTime() - initialTime;
      active = false;
    }

    long elapsedNanos() {
      return finalElapsed;
    }
  }

  public static final class Builder<Solution_> extends AbstractPhaseBuilder<Solution_> {
    private final AlnsPhaseConfig config;
    private final BestSolutionRecaller<Solution_> bestSolutionRecaller;
    private Integer moveThreadCount;
    private int moveThreadBufferSize = 10;
    private ThreadFactory threadFactory;
    private EnvironmentMode environmentMode = EnvironmentMode.PHASE_ASSERT;

    public Builder(
        int phaseIndex,
        String logIndentation,
        PhaseTermination<Solution_> termination,
        AlnsPhaseConfig config,
        BestSolutionRecaller<Solution_> recaller) {
      super(phaseIndex, logIndentation, termination);
      this.config = config;
      bestSolutionRecaller = recaller;
    }

    @Override
    public Builder<Solution_> enableAssertions(EnvironmentMode mode) {
      super.enableAssertions(mode);
      environmentMode = mode;
      return this;
    }

    public Builder<Solution_> withMoveThreadCount(Integer moveThreadCount) {
      this.moveThreadCount = moveThreadCount;
      return this;
    }

    public Builder<Solution_> withMoveThreadBufferSize(int moveThreadBufferSize) {
      this.moveThreadBufferSize = moveThreadBufferSize;
      return this;
    }

    public Builder<Solution_> withThreadFactory(ThreadFactory threadFactory) {
      this.threadFactory = threadFactory;
      return this;
    }

    @Override
    public DefaultAlnsPhase<Solution_> build() {
      return new DefaultAlnsPhase<>(this);
    }
  }
}
