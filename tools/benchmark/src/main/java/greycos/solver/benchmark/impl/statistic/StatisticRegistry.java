package greycos.solver.benchmark.impl.statistic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.alns.AlnsStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverMetricUtil;
import greycos.solver.core.impl.solver.scope.SolverScope;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class StatisticRegistry<Solution_> extends SimpleMeterRegistry
    implements PhaseLifecycleListener<Solution_> {

  private static final String CONSTRAINT_ID_TAG = "constraint.id";

  List<Consumer<SolverScope<Solution_>>> solverMeterListenerList = new ArrayList<>();
  List<BiConsumer<Long, AbstractStepScope<Solution_>>> stepMeterListenerList = new ArrayList<>();
  private final Map<SolverMetric, List<BiConsumer<Long, AbstractStepScope<Solution_>>>>
      metricListeners = new EnumMap<>(SolverMetric.class);
  private SolverMetricSample currentSample;
  private final Consumer<SolverMetricSample> sampleListener = this::accept;
  private SolverScope<Solution_> attachedSolverScope;
  AbstractStepScope<Solution_> bestSolutionStepScope = null;
  long bestSolutionChangedTimestamp = Long.MIN_VALUE;
  boolean lastStepImprovedSolution = false;
  ScoreDefinition<?> scoreDefinition;
  final Function<Number, Number> scoreLevelNumberConverter;

  public StatisticRegistry(ScoreDefinition<?> scoreDefinition) {
    this.scoreDefinition = scoreDefinition;
    var zeroScoreLevel0 = scoreDefinition.getZeroScore().toLevelNumbers()[0];
    if (zeroScoreLevel0 instanceof BigDecimal) {
      scoreLevelNumberConverter = number -> BigDecimal.valueOf(number.doubleValue());
    } else if (zeroScoreLevel0 instanceof BigInteger) {
      scoreLevelNumberConverter = number -> BigInteger.valueOf(number.longValue());
    } else if (zeroScoreLevel0 instanceof Double) {
      scoreLevelNumberConverter = Number::doubleValue;
    } else if (zeroScoreLevel0 instanceof Float) {
      scoreLevelNumberConverter = Number::floatValue;
    } else if (zeroScoreLevel0 instanceof Long) {
      scoreLevelNumberConverter = Number::longValue;
    } else if (zeroScoreLevel0 instanceof Integer) {
      scoreLevelNumberConverter = Number::intValue;
    } else if (zeroScoreLevel0 instanceof Short) {
      scoreLevelNumberConverter = Number::shortValue;
    } else if (zeroScoreLevel0 instanceof Byte) {
      scoreLevelNumberConverter = Number::byteValue;
    } else {
      throw new IllegalStateException(
          "Cannot determine score level type for score definition ("
              + scoreDefinition.getClass().getName()
              + ").");
    }
  }

  public void attach(SolverScope<Solution_> solverScope) {
    if (attachedSolverScope != null) {
      throw new IllegalStateException("The statistic registry is already attached to a solver.");
    }
    attachedSolverScope = solverScope;
    solverScope.addMetricSampleListener(sampleListener);
  }

  public void detach() {
    if (attachedSolverScope != null) {
      attachedSolverScope.removeMetricSampleListener(sampleListener);
      attachedSolverScope = null;
    }
  }

  @Override
  public void close() {
    detach();
    super.close();
  }

  public void addListener(SolverMetric metric, Consumer<Long> listener) {
    addListener(metric, (timestamp, stepScope) -> listener.accept(timestamp));
  }

  public void addListener(
      SolverMetric metric, BiConsumer<Long, AbstractStepScope<Solution_>> listener) {
    metricListeners.computeIfAbsent(metric, ignored -> new ArrayList<>()).add(listener);
    if (!metric.isMetricBestSolutionBased()) {
      stepMeterListenerList.add(listener);
    }
  }

  public void addListener(Consumer<SolverScope<Solution_>> listener) {
    solverMeterListenerList.add(listener);
  }

  public Set<Meter.Id> getMeterIds(SolverMetric metric, Tags runId) {
    if (currentSample != null) {
      return currentSample.measurements().keySet().stream()
          .filter(id -> id.getName().startsWith(metric.getMeterId()))
          .collect(Collectors.toSet());
    }
    return Search.in(this)
        .name(name -> name.startsWith(metric.getMeterId()))
        .tags(runId)
        .meters()
        .stream()
        .map(Meter::getId)
        .collect(Collectors.toSet());
  }

  public void extractScoreFromMeters(
      SolverMetric metric, Tags runId, Consumer<InnerScore<?>> scoreConsumer) {
    if (currentSample != null
        && currentSample.stepScore() != null
        && (metric == SolverMetric.STEP_SCORE
            || (metric == SolverMetric.BEST_SCORE
                && currentSample.kind() == SolverMetricSample.Kind.BEST))) {
      scoreConsumer.accept(currentSample.stepScore());
      return;
    }
    var score =
        SolverMetricUtil.extractScore(
            metric,
            scoreDefinition,
            id -> {
              var value = getGaugeValue(id, runId);
              return value != null && Double.isFinite(value.doubleValue())
                  ? scoreLevelNumberConverter.apply(value)
                  : null;
            });
    if (score != null) {
      scoreConsumer.accept(score);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void extractConstraintSummariesFromMeters(
      SolverMetric metric,
      Tags runId,
      Consumer<ConstraintSummary<?>> constraintMatchTotalConsumer) {
    // Add the constraint ids from the meter ids
    getMeterIds(metric, runId).stream()
        .map(meterId -> ConstraintRef.of(meterId.getTag(CONSTRAINT_ID_TAG)))
        .distinct()
        .forEach(
            constraintRef -> {
              var constraintMatchTotalRunId = runId.and(CONSTRAINT_ID_TAG, constraintRef.id());
              // Get the score from the corresponding constraint ID meters
              extractScoreFromMeters(
                  metric,
                  constraintMatchTotalRunId,
                  // Get the count gauge (add constraint ID to the run tags)
                  score -> {
                    var count =
                        getGaugeValue(
                            SolverMetricUtil.getGaugeName(metric, "count"),
                            constraintMatchTotalRunId);
                    if (count != null) {
                      constraintMatchTotalConsumer.accept(
                          new ConstraintSummary(constraintRef, score.raw(), count.intValue()));
                    }
                  });
            });
  }

  public void extractMoveCountPerType(
      SolverScope<Solution_> solverScope, ObjLongConsumer<String> gaugeConsumer) {
    solverScope
        .getReportedMoveCountsByType()
        .forEach((type, count) -> gaugeConsumer.accept(type, count));
  }

  /** Returns values from the immutable island sample while that sample is being consumed. */
  public Number getGaugeValue(SolverMetric metric, Tags tags) {
    return getGaugeValue(metric.getMeterId(), tags);
  }

  public Number getGaugeValue(String meterName, Tags tags) {
    if (currentSample != null) {
      if (meterName.startsWith("jvm.memory.")) {
        return SolverMetricUtil.getGaugeValue(this, meterName, tags);
      }
      if (meterName.equals(SolverMetric.SCORE_CALCULATION_COUNT.getMeterId())) {
        return currentSample.work().scoreCalculationCount();
      }
      if (meterName.equals(SolverMetric.MOVE_EVALUATION_COUNT.getMeterId())) {
        return currentSample.work().moveEvaluationCount();
      }
      return currentSample.gaugeValue(meterName, tags);
    }
    return SolverMetricUtil.getGaugeValue(this, meterName, tags);
  }

  public String getSampleSource() {
    return currentSample == null || "root".equals(currentSample.source())
        ? null
        : currentSample.source();
  }

  public String getMoveType(AbstractStepScope<Solution_> stepScope) {
    if (currentSample != null) {
      return currentSample.moveType();
    }
    if (stepScope instanceof AlnsStepScope<Solution_> alnsStepScope) {
      return alnsStepScope.getOperatorPairId();
    }
    if (stepScope instanceof LocalSearchStepScope<Solution_> localSearchStepScope) {
      return localSearchStepScope.getStep().describe();
    }
    return null;
  }

  public boolean isFinalSample() {
    return currentSample != null
        && currentSample.kind() == SolverMetricSample.Kind.FINAL
        && "root".equals(currentSample.source());
  }

  /** Called on the coordinator thread; child scopes and live gauges never escape their owners. */
  public void accept(SolverMetricSample sample) {
    currentSample = sample;
    try {
      switch (sample.kind()) {
        case STEP -> {
          metricListeners.forEach(
              (metric, listeners) -> {
                boolean localBest =
                    metric == SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF
                        || metric == SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE;
                if (!metric.isMetricBestSolutionBased()
                    || (localBest && sample.bestScoreImproved())) {
                  listeners.forEach(listener -> listener.accept(sample.timeMillisSpent(), null));
                }
              });
        }
        case BEST -> {
          notifyMetric(SolverMetric.BEST_SCORE, sample.timeMillisSpent());
          notifyMetric(SolverMetric.BEST_SOLUTION_MUTATION, sample.timeMillisSpent());
        }
        case FINAL -> {
          notifyMetric(SolverMetric.SCORE_CALCULATION_COUNT, sample.timeMillisSpent());
          notifyMetric(SolverMetric.MOVE_EVALUATION_COUNT, sample.timeMillisSpent());
          notifyMetric(SolverMetric.MEMORY_USE, sample.timeMillisSpent());
        }
      }
    } finally {
      currentSample = null;
    }
  }

  private void notifyMetric(SolverMetric metric, long timestamp) {
    metricListeners
        .getOrDefault(metric, List.of())
        .forEach(listener -> listener.accept(timestamp, null));
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.MILLISECONDS;
  }

  @Override
  public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    var timestamp =
        System.currentTimeMillis()
            - stepScope.getPhaseScope().getSolverScope().getStartingSystemTimeMillis();
    stepMeterListenerList.forEach(listener -> listener.accept(timestamp, stepScope));
    if (stepScope.getBestScoreImproved()) {
      // Since best solution metrics are updated in a best solution listener, we need
      // to delay updating it until after the best solution listeners were processed
      bestSolutionStepScope = stepScope;
      bestSolutionChangedTimestamp = timestamp;
      lastStepImprovedSolution = true;
    }
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    // intentional empty
  }

  @Override
  public void stepStarted(AbstractStepScope<Solution_> stepScope) {
    if (lastStepImprovedSolution) {
      notifyDeferredBestListeners();
      lastStepImprovedSolution = false;
    }
  }

  private void notifyDeferredBestListeners() {
    metricListeners.forEach(
        (metric, listeners) -> {
          if (metric.isMetricBestSolutionBased()
              && (attachedSolverScope == null
                  || (metric != SolverMetric.BEST_SCORE
                      && metric != SolverMetric.BEST_SOLUTION_MUTATION))) {
            listeners.forEach(
                listener -> listener.accept(bestSolutionChangedTimestamp, bestSolutionStepScope));
          }
        });
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    // intentional empty
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    // intentional empty
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    if (lastStepImprovedSolution) {
      notifyDeferredBestListeners();
      lastStepImprovedSolution = false;
    }
    solverMeterListenerList.forEach(listener -> listener.accept(solverScope));
  }
}
