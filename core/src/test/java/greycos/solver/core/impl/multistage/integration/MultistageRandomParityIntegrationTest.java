package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.MultistageIntegrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.multistage.PreparedMultistageMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultistageRandomParityIntegrationTest {
  private static final int STEPS = 5;
  private static final int CANDIDATES_PER_STEP = 8;
  private static final Map<String, AtomicInteger> CALLBACKS = new ConcurrentHashMap<>();

  @ParameterizedTest
  @ValueSource(longs = {3L, 7L, 13L})
  void seededCandidateStreamsProduceIdenticalSelectedJournalsAndScoresAcrossWorkerCounts(
      long seed) {
    List<Step> expected = null;
    for (var workers : List.of("NONE", "1", "2", "4")) {
      var actual = solve(seed, workers);
      if (expected == null) expected = actual;
      else
        assertThat(actual)
            .as("Seed %s with %s workers", seed, workers)
            .containsExactlyElementsOf(expected);
    }
  }

  private static List<Step> solve(long seed, String workers) {
    var problem = basicProblem(2);
    for (int value = problem.getValueList().size(); value < 64; value++) {
      problem.getValueList().add(new TestdataValue(Integer.toString(value)));
    }
    var callbacks = new AtomicInteger();
    CALLBACKS.put(problem.getCode(), callbacks);
    try {
      var selector =
          new MultistageMoveSelectorConfig()
              .withStageProviderClass(RandomStages.class)
              .withEntityClass(TestdataEntity.class)
              .withVariableName("value")
              .withSelectionOrder(SelectionOrder.RANDOM)
              .withCandidateCountLimit(CANDIDATES_PER_STEP);
      var phase =
          new LocalSearchPhaseConfig()
              .withMoveSelectorConfig(selector)
              .withAcceptorConfig(
                  new LocalSearchAcceptorConfig()
                      .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
              .withForagerConfig(
                  new LocalSearchForagerConfig()
                      .withAcceptedCountLimit(100)
                      .withPickEarlyType(LocalSearchPickEarlyType.NEVER)
                      .withBreakTieRandomly(false))
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(STEPS));
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers).withRandomSeed(seed).withPhases(phase));
      var steps = new ArrayList<Step>();
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var stepScope = (LocalSearchStepScope<TestdataSolution>) scope;
              assertThat(stepScope.getSelectedMoveCount()).isEqualTo(CANDIDATES_PER_STEP);
              assertThat(stepScope.getAcceptedMoveCount()).isEqualTo(CANDIDATES_PER_STEP);
              assertThat(stepScope.getStep()).isInstanceOf(PreparedMultistageMove.class);
              assertThat(scope.getScore().isFullyAssigned()).isTrue();
              assertBasicScore(scope.getWorkingSolution());
              var score = (SimpleScore) scope.getScore().raw();
              if (!steps.isEmpty())
                assertThat(score.compareTo(steps.getLast().score())).isPositive();
              steps.add(
                  new Step(
                      assignments(scope.getWorkingSolution()),
                      score,
                      stepScope.getStep().toString()));
            }
          });

      var result = solver.solve(problem);

      assertThat(steps).hasSize(STEPS);
      assertBasicScore(result);
      assertThat(assignments(result)).isEqualTo(steps.getLast().assignments());
      // All eight finite candidates are consumed, so no candidate is canceled speculatively.
      // Assertions, coordinator commitment and worker replay must reuse the frozen journal.
      assertThat(callbacks).hasValue(2 * STEPS * CANDIDATES_PER_STEP);
      return steps;
    } finally {
      CALLBACKS.remove(problem.getCode());
    }
  }

  private record Step(List<String> assignments, SimpleScore score, String journal) {}

  public static final class RandomStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 4;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      int increase = Math.toIntExact(candidateIndex) + random.nextInt(1, 5);
      return List.of(
          evaluator -> {
            var solution = evaluator.workingSolution();
            CALLBACKS.get(solution.getCode()).incrementAndGet();
            var first = solution.getEntityList().getFirst();
            var originalValue = first.getValue();
            var originalScore = solution.getScore();
            int next = Integer.parseInt(originalValue.getCode()) + increase;
            var operation = evaluator.assign(first, solution.getValueList().get(next));
            assertThat(evaluator.evaluate(operation).isComplete()).isTrue();
            assertThat(first.getValue()).isSameAs(originalValue);
            assertThat(solution.getScore()).isEqualTo(originalScore);
            return MultistageStageResult.apply(operation);
          },
          evaluator -> {
            var solution = evaluator.workingSolution();
            CALLBACKS.get(solution.getCode()).incrementAndGet();
            var first = solution.getEntityList().getFirst();
            var second = solution.getEntityList().getLast();
            assertThat(
                    Integer.parseInt(first.getValue().getCode())
                        - Integer.parseInt(second.getValue().getCode()))
                .isEqualTo(increase);
            return MultistageStageResult.apply(
                evaluator.assign(second, evaluator.currentValue(first)));
          });
    }
  }
}
