package greycos.solver.core.impl.multistage.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultistageRangeIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void dependentStagesUseEachEntityRangeAndFailedSwapProbePreservesTheirBaseline(String workers) {
    var zero = new TestdataValue("0");
    var one = new TestdataValue("1");
    var two = new TestdataValue("2");
    var problem = new TestdataEntityProvidingSolution("range problem");
    problem.setEntityList(
        List.of(
            new TestdataEntityProvidingEntity("first", List.of(zero, one), zero),
            new TestdataEntityProvidingEntity("second", List.of(zero, two), zero)));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataEntityProvidingSolution.class)
            .withEntityClasses(TestdataEntityProvidingEntity.class)
            .withConstraintProviderClass(RangeConstraints.class)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withMoveThreadCount(workers)
            .withRandomSeed(7L)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                    .withMoveSelectorConfig(
                        new MultistageMoveSelectorConfig()
                            .withStageProviderClass(RangeStages.class)
                            .withVariableName("value")
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));

    var result =
        MultistageIntegrationSupport.<TestdataEntityProvidingSolution>solver(config).solve(problem);

    assertThat(result.getEntityList())
        .extracting(entity -> entity.getValue().getCode())
        .containsExactly("1", "2");
    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValueRange()).contains(entity.getValue()));
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(3));
    assertThat(problem.getEntityList())
        .extracting(entity -> entity.getValue().getCode())
        .containsExactly("0", "0");
  }

  public static final class RangeStages
      implements BasicVariableStageProvider<
          TestdataEntityProvidingSolution,
          TestdataEntityProvidingEntity,
          TestdataValue,
          SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            BasicVariableCustomStage<
                TestdataEntityProvidingSolution,
                TestdataEntityProvidingEntity,
                TestdataValue,
                SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var first = evaluator.workingSolution().getEntityList().getFirst();
            assertThat(evaluator.legalValues(first))
                .extracting(TestdataValue::getCode)
                .containsExactly("0", "1");
            return evaluator.bestFit(
                evaluator.legalValues(first).stream()
                    .map(value -> evaluator.assign(first, value))
                    .toList());
          },
          evaluator -> {
            var solution = evaluator.workingSolution();
            var first = solution.getEntityList().getFirst();
            var second = solution.getEntityList().getLast();
            assertThat(first.getValue().getCode()).isEqualTo("1");
            assertThat(evaluator.legalValues(second))
                .extracting(TestdataValue::getCode)
                .containsExactly("0", "2");
            assertThat(evaluator.legalValues(second)).doesNotContain(first.getValue());
            var baselineScore = solution.getScore();
            assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluator.evaluate(evaluator.swap(first, second)))
                .withMessageContaining("range");
            assertThat(first.getValue().getCode()).isEqualTo("1");
            assertThat(second.getValue().getCode()).isEqualTo("0");
            assertThat(solution.getScore()).isEqualTo(baselineScore);
            return evaluator.bestFit(
                evaluator.legalValues(second).stream()
                    .map(value -> evaluator.assign(second, value))
                    .toList());
          });
    }
  }

  public static final class RangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntityProvidingEntity.class)
            .reward(SimpleScore.ONE, entity -> Long.parseLong(entity.getValue().getCode()))
            .asConstraint("Numeric value in entity range")
      };
    }
  }
}
