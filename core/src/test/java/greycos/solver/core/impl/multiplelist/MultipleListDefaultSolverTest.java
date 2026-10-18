package greycos.solver.core.impl.multiplelist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.IdentityHashMap;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEasyScoreCalculator;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListDefaultSolverTest {

  @Test
  void defaultPhasesInitializeBothListsAndBasicVariables() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataMultipleListSolution.class)
            .withEntityClasses(
                TestdataMultipleListEntity.class,
                TestdataMultipleListFirstValue.class,
                TestdataMultipleListSecondValue.class)
            .withEasyScoreCalculatorClass(TestdataMultipleListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(0L)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(60));
    // Deliberately omit phases and selectors: this exercises automatic construction and search.
    var result =
        SolverFactory.<TestdataMultipleListSolution>create(config)
            .buildSolver()
            .solve(TestdataMultipleListSolution.generateUninitializedSolution(4, 5, 2));
    assertCompleteState(result);
    assertThat(result.getScore())
        .isEqualTo(new TestdataMultipleListEasyScoreCalculator().calculateScore(result));
  }

  @Test
  void independentNamedPrefixesSurviveDefaultSearch() {
    var problem = TestdataMultipleListSolution.generateInitializedSolution(4, 6, 2);
    problem.getEntityList().getFirst().setFirstPinnedIndex(1);
    problem.getEntityList().getFirst().setSecondPinnedIndex(2);
    var firstPrefix = problem.getEntityList().getFirst().getFirstValueList().getFirst().getCode();
    var secondPrefix =
        problem.getEntityList().getFirst().getSecondValueList().subList(0, 2).stream()
            .map(TestdataMultipleListSecondValue::getCode)
            .toList();
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataMultipleListSolution.class)
            .withEntityClasses(
                TestdataMultipleListEntity.class,
                TestdataMultipleListFirstValue.class,
                TestdataMultipleListSecondValue.class)
            .withEasyScoreCalculatorClass(TestdataMultipleListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(3L)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(30));
    var result =
        SolverFactory.<TestdataMultipleListSolution>create(config).buildSolver().solve(problem);
    assertCompleteState(result);
    assertThat(result.getEntityList().getFirst().getFirstValueList().getFirst().getCode())
        .isEqualTo(firstPrefix);
    assertThat(result.getEntityList().getFirst().getSecondValueList().subList(0, 2))
        .extracting(TestdataMultipleListSecondValue::getCode)
        .containsExactlyElementsOf(secondPrefix);
  }

  private static void assertCompleteState(TestdataMultipleListSolution result) {
    var seen = new IdentityHashMap<Object, Boolean>();
    for (var owner : result.getEntityList()) {
      assertThat(owner.getBasicValue()).isIn(result.getBasicValueRange());
      for (int i = 0; i < owner.getFirstValueList().size(); i++) {
        var value = owner.getFirstValueList().get(i);
        assertThat(seen.put(value, true)).isNull();
        assertThat(result.getFirstValueList()).contains(value);
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious())
            .isSameAs(i == 0 ? null : owner.getFirstValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getFirstValueList().size()
                    ? null
                    : owner.getFirstValueList().get(i + 1));
      }
      for (int i = 0; i < owner.getSecondValueList().size(); i++) {
        var value = owner.getSecondValueList().get(i);
        assertThat(seen.put(value, true)).isNull();
        assertThat(result.getSecondValueList()).contains(value);
        assertThat(value.getQuantity()).isIn(result.getBasicValueRange());
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious())
            .isSameAs(i == 0 ? null : owner.getSecondValueList().get(i - 1));
        assertThat(value.getNext())
            .isSameAs(
                i + 1 == owner.getSecondValueList().size()
                    ? null
                    : owner.getSecondValueList().get(i + 1));
      }
    }
    assertThat(seen)
        .hasSize(result.getFirstValueList().size() + result.getSecondValueList().size());
    assertThat(result.getScore()).isNotNull().isLessThanOrEqualTo(SimpleScore.ZERO);
  }
}
