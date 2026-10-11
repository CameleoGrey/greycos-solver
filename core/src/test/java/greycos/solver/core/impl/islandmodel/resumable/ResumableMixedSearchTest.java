package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
class ResumableMixedSearchTest {
  @Test
  void mixedAssignmentsPinsPrefixesAndBothShadowGraphsSurviveQuantaAndStableIdReconstruction()
      throws Exception {
    List<String> reference = null;
    for (int workers : new int[] {1, 2, 3}) {
      var phase =
          new LocalSearchPhaseConfig()
              .withMoveSelectorConfig(
                  new UnionMoveSelectorConfig()
                      .withMoveSelectors(
                          basic("basicValue"),
                          basic("secondBasicValue"),
                          new ListChangeMoveSelectorConfig(),
                          new ListSwapMoveSelectorConfig())
                      .withSelectedCountLimit(128L))
              .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(7))
              .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(8))
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
      var config =
          new SolverConfig()
              .withSolutionClass(TestdataMixedSolution.class)
              .withEntityClasses(
                  TestdataMixedEntity.class,
                  TestdataMixedValue.class,
                  TestdataMixedOtherValue.class)
              .withConstraintProviderClass(MixedConstraints.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withRandomSeed(37L)
              .withMoveThreadCount("NONE")
              .withPhases(sessions(phase, 3, workers, 3, ResumableSearchCooperationPolicy.NONE));
      var trace = new ArrayList<String>();
      TestdataMixedSolution result;
      try (var ignored =
          ResumableSearchRuntime.observeRounds(
              round -> {
                for (var session : round.sessions())
                  for (var step : session.steps()) {
                    var solution = (TestdataMixedSolution) step.solution();
                    var score = replay(solution);
                    assertThat(step.score().raw()).isEqualTo(score);
                    trace.add(
                        session.path()
                            + ":"
                            + session.episode()
                            + ":"
                            + step.stepIndex()
                            + ":"
                            + step.moveDescription()
                            + ":"
                            + score
                            + ":"
                            + state(solution));
                  }
              })) {
        result = SolverFactory.<TestdataMixedSolution>create(config).buildSolver().solve(problem());
      }
      assertThat(trace).hasSize(60);
      assertThat(result.getScore()).isEqualTo(replay(result));
      var rebuilt = reconstruct(result);
      assertThat(replay(rebuilt)).isEqualTo(result.getScore());
      assertThat(state(rebuilt)).isEqualTo(state(result));
      if (reference == null) reference = trace;
      else assertThat(trace).isEqualTo(reference);
    }
  }

  private static ChangeMoveSelectorConfig basic(String variable) {
    return new ChangeMoveSelectorConfig()
        .withEntitySelectorConfig(new EntitySelectorConfig(TestdataMixedEntity.class))
        .withValueSelectorConfig(new ValueSelectorConfig(variable));
  }

  private static TestdataMixedSolution problem() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(4, 12, 3);
    var baseline = solution.getOtherValueList().getLast();
    for (var entity : solution.getEntityList()) {
      entity.setBasicValue(baseline);
      entity.setSecondBasicValue(baseline);
    }
    solution.getEntityList().getFirst().setPinned(true);
    solution.getEntityList().getFirst().getValueList().add(solution.getValueList().getFirst());
    solution.getEntityList().get(1).setPinnedIndex(1);
    solution.getEntityList().get(1).getValueList().add(solution.getValueList().get(1));
    for (int i = 2; i < solution.getValueList().size(); i++)
      solution.getEntityList().get(1 + i % 3).getValueList().add(solution.getValueList().get(i));
    SolutionManager.updateShadowVariables(solution);
    solution.setScore(replay(solution));
    return solution;
  }

  private static SimpleScore replay(TestdataMixedSolution solution) {
    var all = new HashSet<TestdataMixedValue>();
    long penalty = 0;
    for (var entity : solution.getEntityList()) {
      assertThat(solution.getOtherValueList())
          .contains(entity.getBasicValue(), entity.getSecondBasicValue());
      assertThat(entity.getDeclarativeShadowVariableValue())
          .isEqualTo(entity.getBasicValue().getStrength());
      var values = entity.getValueList();
      penalty += (long) values.size() * values.size() * entity.getBasicValue().getStrength();
      penalty +=
          Math.abs((long) entity.getSecondBasicValue().getStrength() - entity.getDifficulty());
      for (int index = 0; index < values.size(); index++) {
        var value = values.get(index);
        assertThat(all.add(value)).isTrue();
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPreviousElement()).isSameAs(index == 0 ? null : values.get(index - 1));
        assertThat(value.getNextElement())
            .isSameAs(index + 1 == values.size() ? null : values.get(index + 1));
        assertThat(value.getCascadingShadowVariableValue()).isEqualTo(index + 1);
        assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(index + 2);
      }
    }
    assertThat(all).containsExactlyInAnyOrderElementsOf(solution.getValueList());
    var pinned = solution.getEntityList().getFirst();
    assertThat(pinned.getValueList()).containsExactly(solution.getValueList().getFirst());
    assertThat(pinned.getBasicValue()).isSameAs(solution.getOtherValueList().getLast());
    assertThat(pinned.getSecondBasicValue()).isSameAs(solution.getOtherValueList().getLast());
    assertThat(solution.getEntityList().get(1).getValueList().getFirst())
        .isSameAs(solution.getValueList().get(1));
    for (var value : solution.getOtherValueList()) {
      var expected =
          solution.getEntityList().stream()
              .filter(entity -> entity.getBasicValue() == value)
              .toList();
      assertThat(value.getEntityList()).containsExactlyInAnyOrderElementsOf(expected);
      assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(expected.size() + 2);
    }
    return SimpleScore.of(-penalty);
  }

  private static String state(TestdataMixedSolution solution) {
    var out = new StringBuilder();
    for (var entity : solution.getEntityList())
      out.append(entity.getCode())
          .append('=')
          .append(entity.getBasicValue().getCode())
          .append('/')
          .append(entity.getSecondBasicValue().getCode())
          .append(':')
          .append(entity.getValueList().stream().map(TestdataMixedValue::getCode).toList())
          .append(';');
    return out.toString();
  }

  private static TestdataMixedSolution reconstruct(TestdataMixedSolution source) {
    var result = problem();
    Map<String, TestdataMixedValue> values =
        result.getValueList().stream()
            .collect(Collectors.toMap(TestdataMixedValue::getCode, Function.identity()));
    Map<String, TestdataMixedOtherValue> basics =
        result.getOtherValueList().stream()
            .collect(Collectors.toMap(TestdataMixedOtherValue::getCode, Function.identity()));
    for (int i = 0; i < source.getEntityList().size(); i++) {
      var from = source.getEntityList().get(i);
      var to = result.getEntityList().get(i);
      to.setBasicValue(basics.get(from.getBasicValue().getCode()));
      to.setSecondBasicValue(basics.get(from.getSecondBasicValue().getCode()));
      to.setValueList(
          new ArrayList<>(
              from.getValueList().stream().map(value -> values.get(value.getCode())).toList()));
    }
    SolutionManager.updateShadowVariables(result);
    return result;
  }

  public static final class MixedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity ->
                    (long) entity.getValueList().size()
                            * entity.getValueList().size()
                            * entity.getBasicValue().getStrength()
                        + Math.abs(
                            (long) entity.getSecondBasicValue().getStrength()
                                - entity.getDifficulty()))
            .asConstraint("Coupled assignment and list load")
      };
    }
  }
}
