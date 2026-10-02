package greycos.solver.core.impl.multistage.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class MultistageMixedIntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void separateSelectorsPreserveOtherVariablesFullPinsPrefixesAndMixedShadows(String workers) {
    var basic =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(MixedBasicStages.class)
            .withEntityClass(TestdataMixedEntity.class)
            .withVariableName("basicValue")
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var list =
        new ListMultistageMoveSelectorConfig()
            .withStageProviderClass(MixedListStages.class)
            .withEntityClass(TestdataMixedEntity.class)
            .withVariableName("valueList")
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataMixedSolution.class)
            .withEntityClasses(
                TestdataMixedEntity.class, TestdataMixedValue.class, TestdataMixedOtherValue.class)
            .withConstraintProviderClass(MixedConstraints.class)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withMoveThreadCount(workers)
            .withRandomSeed(7L)
            .withPhases(phase(basic), phase(list));
    var problem = problem();

    var result = MultistageIntegrationSupport.<TestdataMixedSolution>solver(config).solve(problem);

    assertThat(result.getScore()).isEqualTo(SimpleScore.of(15));
    assertThat(result.getEntityList())
        .extracting(entity -> entity.getBasicValue().getStrength())
        .containsExactly(2, 3, 3);
    assertThat(result.getEntityList())
        .allSatisfy(
            entity -> {
              assertThat(entity.getSecondBasicValue().getStrength()).isEqualTo(2);
              assertThat(entity.getDeclarativeShadowVariableValue())
                  .isEqualTo(entity.getBasicValue().getStrength());
            });
    assertThat(result.getEntityList().get(0).getValueList())
        .extracting(TestdataMixedValue::getCode)
        .containsExactly("0");
    assertThat(result.getEntityList().get(1).getValueList())
        .extracting(TestdataMixedValue::getCode)
        .containsExactly("1");
    assertThat(result.getEntityList().get(2).getValueList())
        .extracting(TestdataMixedValue::getCode)
        .containsExactly("2", "3");
    long independentScore =
        result.getEntityList().stream()
            .mapToLong(entity -> entity.getBasicValue().getStrength())
            .sum();
    var assigned = new ArrayList<TestdataMixedValue>();
    for (var entity : result.getEntityList()) {
      var values = entity.getValueList();
      for (int index = 0; index < values.size(); index++) {
        var value = values.get(index);
        assigned.add(value);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPreviousElement()).isSameAs(index == 0 ? null : values.get(index - 1));
        assertThat(value.getNextElement())
            .isSameAs(index == values.size() - 1 ? null : values.get(index + 1));
        assertThat(value.getCascadingShadowVariableValue()).isEqualTo(index + 1);
        assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(index + 2);
        if (entity.getCode().equals("target"))
          independentScore += Long.parseLong(value.getCode()) + 1;
      }
    }
    assertThat(assigned).containsExactlyInAnyOrderElementsOf(result.getValueList());
    for (var value : result.getOtherValueList()) {
      var owners =
          result.getEntityList().stream()
              .filter(entity -> entity.getBasicValue() == value)
              .toList();
      assertThat(value.getEntityList()).containsExactlyInAnyOrderElementsOf(owners);
      assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(owners.size() + 2);
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(independentScore));
  }

  private static LocalSearchPhaseConfig phase(MoveSelectorConfig<?> selector) {
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
        .withMoveSelectorConfig(selector)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
  }

  private static TestdataMixedSolution problem() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(3, 4, 2);
    solution.getOtherValueList().getFirst().setStrength(3);
    solution.getOtherValueList().getLast().setStrength(2);
    for (var entity : solution.getEntityList()) {
      entity.setBasicValue(solution.getOtherValueList().getLast());
      entity.setSecondBasicValue(solution.getOtherValueList().getLast());
    }
    for (int i = 0; i < solution.getValueList().size(); i++)
      solution.getValueList().get(i).setCode(Integer.toString(i));
    var pinned = solution.getEntityList().getFirst();
    pinned.setPinned(true);
    pinned.setValueList(new ArrayList<>(List.of(solution.getValueList().get(0))));
    var prefix = solution.getEntityList().get(1);
    prefix.setPinnedIndex(1);
    prefix.setValueList(
        new ArrayList<>(
            List.of(
                solution.getValueList().get(1),
                solution.getValueList().get(3),
                solution.getValueList().get(2))));
    solution.getEntityList().getLast().setCode("target");
    SolutionManager.updateShadowVariables(solution);
    return solution;
  }

  public static final class MixedBasicStages
      implements BasicVariableStageProvider<
          TestdataMixedSolution, TestdataMixedEntity, TestdataMixedOtherValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            BasicVariableCustomStage<
                TestdataMixedSolution, TestdataMixedEntity, TestdataMixedOtherValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var solution = evaluator.workingSolution();
            var entity = solution.getEntityList().get(1);
            assertThat(evaluator.legalValues(entity))
                .containsExactlyElementsOf(solution.getOtherValueList());
            return MultistageStageResult.apply(
                evaluator.assign(entity, solution.getOtherValueList().getFirst()));
          },
          evaluator -> {
            var solution = evaluator.workingSolution();
            var changed = solution.getEntityList().get(1);
            assertThat(changed.getDeclarativeShadowVariableValue()).isEqualTo(3);
            assertThat(changed.getBasicValue().getEntityList()).contains(changed);
            assertThat(changed.getSecondBasicValue().getStrength()).isEqualTo(2);
            return MultistageStageResult.apply(
                evaluator.assign(
                    solution.getEntityList().getLast(), evaluator.currentValue(changed)));
          });
    }
  }

  public static final class MixedListStages
      implements ListVariableStageProvider<
          TestdataMixedSolution, TestdataMixedEntity, TestdataMixedValue, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<
            ListVariableCustomStage<
                TestdataMixedSolution, TestdataMixedEntity, TestdataMixedValue, SimpleScore>>
        createStages(long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var solution = evaluator.workingSolution();
            var pinned = solution.getEntityList().getFirst();
            var prefix = solution.getEntityList().get(1);
            assertThat(evaluator.legalPositions(solution.getValueList().get(0))).isEmpty();
            assertThat(evaluator.legalPositions(solution.getValueList().get(1))).isEmpty();
            var value = solution.getValueList().get(2);
            assertThat(evaluator.legalPositions(value))
                .isNotEmpty()
                .allSatisfy(
                    position -> {
                      assertThat(position.entity()).isNotSameAs(pinned);
                      if (position.entity() == prefix)
                        assertThat(position.index()).isGreaterThanOrEqualTo(1);
                    });
            return MultistageStageResult.apply(
                evaluator.place(value, solution.getEntityList().getLast(), 0));
          },
          evaluator -> {
            var solution = evaluator.workingSolution();
            var target = solution.getEntityList().getLast();
            var moved = solution.getValueList().get(2);
            assertThat(moved.getEntity()).isSameAs(target);
            assertThat(moved.getCascadingShadowVariableValue()).isEqualTo(1);
            assertThat(moved.getDeclarativeShadowVariableValue()).isEqualTo(2);
            var value = solution.getEntityList().get(1).getValueList().getLast();
            return MultistageStageResult.apply(
                evaluator.place(value, target, target.getValueList().size()));
          });
    }
  }

  public static final class MixedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedEntity.class)
            .reward(SimpleScore.ONE, entity -> entity.getBasicValue().getStrength())
            .asConstraint("Basic strength"),
        factory
            .forEach(TestdataMixedValue.class)
            .filter(value -> value.getEntity().getCode().equals("target"))
            .reward(SimpleScore.ONE, value -> Long.parseLong(value.getCode()) + 1)
            .asConstraint("List target")
      };
    }
  }
}
