package greycos.solver.core.impl.multistage.integration;

import static greycos.solver.core.impl.multistage.integration.MultistageIntegrationSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.multistage.PreparedMultistageMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30)
class MultistageFilterIntegrationTest {
  private static final Map<String, Observations> OBSERVATIONS = new ConcurrentHashMap<>();

  static Stream<Arguments> filters() {
    return Stream.of("NONE", "1", "2", "4")
        .flatMap(
            workers ->
                Stream.of(false, true)
                    .flatMap(
                        union ->
                            Stream.of(false, true)
                                .map(rejectAll -> Arguments.of(workers, union, rejectAll))));
  }

  @ParameterizedTest
  @MethodSource("filters")
  void filtersInspectPreparedCoordinatorObjectsAndRejectedCandidatesExhaustTheFiniteSelector(
      String workers, boolean union, boolean rejectAll) {
    var problem = basicProblem(2);
    var observations = new Observations(Thread.currentThread(), rejectAll);
    OBSERVATIONS.put(problem.getCode(), observations);
    try {
      MoveSelectorConfig<?> moves =
          union
              ? new UnionMoveSelectorConfig()
                  .withMoveSelectors(selector(), selector())
                  .withSelectionOrder(SelectionOrder.ORIGINAL)
                  .withFilterClass(PreparedFilter.class)
              : selector().withFilterClass(PreparedFilter.class);
      var solver =
          MultistageIntegrationSupport.<TestdataSolution>solver(
              basicConfig(workers)
                  .withPhases(
                      new LocalSearchPhaseConfig()
                          .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                          .withMoveSelectorConfig(moves)
                          .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
      var trace = recordBasicSteps(solver);

      var result = solver.solve(problem);

      int candidateCount = rejectAll && union ? 4 : 2;
      // Workers may start the second union child before acceptance cancels that speculative work.
      // Cancellation can stop its callback before its first probe has completed. Every candidate
      // actually passed to the filter must have completed its probe.
      assertThat(observations.callbacks.get()).isBetween(candidateCount, union ? 4 : 2);
      assertThat(observations.probes.get()).isBetween(candidateCount, observations.callbacks.get());
      assertThat(observations.filters).hasValue(candidateCount);
      if (rejectAll) {
        assertThat(trace).isEmpty();
        assertThat(assignments(result)).containsExactly("0", "0");
      } else {
        assertThat(trace).containsExactly(new BasicStep(List.of("2", "2"), SimpleScore.of(4)));
        assertThat(assignments(result)).containsExactly("2", "2");
      }
      assertBasicScore(result);
    } finally {
      OBSERVATIONS.remove(problem.getCode());
    }
  }

  private static MultistageMoveSelectorConfig selector() {
    return new MultistageMoveSelectorConfig()
        .withStageProviderClass(FilteredStages.class)
        .withEntityClass(TestdataEntity.class)
        .withVariableName("value")
        .withSelectionOrder(SelectionOrder.ORIGINAL)
        .withCandidateCountLimit(2);
  }

  private static final class Observations {
    final Thread coordinator;
    final boolean rejectAll;
    final AtomicInteger callbacks = new AtomicInteger();
    final AtomicInteger probes = new AtomicInteger();
    final AtomicInteger filters = new AtomicInteger();

    Observations(Thread coordinator, boolean rejectAll) {
      this.coordinator = coordinator;
      this.rejectAll = rejectAll;
    }
  }

  public static final class FilteredStages
      implements BasicVariableStageProvider<
          TestdataSolution, TestdataEntity, TestdataValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 2;
    }

    @Override
    public List<
            BasicVariableCustomStage<TestdataSolution, TestdataEntity, TestdataValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      var invoked = new AtomicBoolean();
      return List.of(
          evaluator -> {
            assertThat(invoked.compareAndSet(false, true))
                .as("A prepared candidate must replay without invoking its stage again")
                .isTrue();
            var solution = evaluator.workingSolution();
            var observations = OBSERVATIONS.get(solution.getCode());
            observations.callbacks.incrementAndGet();
            var selected = solution.getValueList().get(Math.toIntExact(candidateIndex) + 1);
            var operation =
                evaluator.sequence(
                    List.of(
                        evaluator.assign(solution.getEntityList().getFirst(), selected),
                        evaluator.assign(solution.getEntityList().getLast(), selected)));
            assertThat(evaluator.evaluate(operation).score())
                .isEqualTo(SimpleScore.of(2L * (candidateIndex + 1)));
            observations.probes.incrementAndGet();
            assertThat(assignments(solution)).containsExactly("0", "0");
            return MultistageStageResult.apply(operation);
          });
    }
  }

  public static final class PreparedFilter
      implements SelectionFilter<TestdataSolution, Move<TestdataSolution>> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataSolution> scoreDirector, Move<TestdataSolution> selection) {
      var solution = scoreDirector.getWorkingSolution();
      var observations = OBSERVATIONS.get(solution.getCode());
      observations.filters.incrementAndGet();
      assertThat(Thread.currentThread()).isSameAs(observations.coordinator);
      assertThat(selection).isInstanceOf(PreparedMultistageMove.class);
      assertThat(selection.getPlanningEntities())
          .hasSize(2)
          .allSatisfy(
              entity ->
                  assertThat(
                          solution.getEntityList().stream()
                              .anyMatch(workingEntity -> workingEntity == entity))
                      .isTrue());
      assertThat(assignments(solution)).containsExactly("0", "0");
      assertThat(selection.getPlanningValues()).hasSize(1);
      var selectedValue = (TestdataValue) selection.getPlanningValues().getFirst();
      assertThat(solution.getValueList().stream().anyMatch(value -> value == selectedValue))
          .isTrue();
      return !observations.rejectAll && selectedValue.getCode().equals("2");
    }
  }
}
