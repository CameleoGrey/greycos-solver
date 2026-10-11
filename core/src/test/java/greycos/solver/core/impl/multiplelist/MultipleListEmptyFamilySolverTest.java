package greycos.solver.core.impl.multiplelist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(30)
class MultipleListEmptyFamilySolverTest {

  @ParameterizedTest
  @CsvSource({"0, 0", "0, 2", "2, 0"})
  void defaultSearchSkipsAbsentListFamiliesAndSolvesPresentVariables(
      int firstOwnerCount, int secondOwnerCount) {
    var problem = new EmptyFamilySolution();
    for (int i = 0; i < firstOwnerCount; i++) {
      problem.firstOwners.add(new FirstOwner());
    }
    for (int i = 0; i < secondOwnerCount; i++) {
      problem.secondOwners.add(new SecondOwner());
    }
    problem.firstValues = firstOwnerCount == 0 ? List.of() : List.of("a", "b", "c");
    problem.secondValues = secondOwnerCount == 0 ? List.of() : List.of(1, 2, 3);
    problem.basicEntities = List.of(new BasicEntity(), new BasicEntity(), new BasicEntity());
    var config =
        new SolverConfig()
            .withSolutionClass(EmptyFamilySolution.class)
            .withEntityClasses(FirstOwner.class, SecondOwner.class, BasicEntity.class)
            .withEasyScoreCalculatorClass(EmptyFamilyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withRandomSeed(0L)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(30));

    var result = SolverFactory.<EmptyFamilySolution>create(config).buildSolver().solve(problem);

    assertThat(result.firstOwners).hasSize(firstOwnerCount);
    assertThat(result.secondOwners).hasSize(secondOwnerCount);
    assertThat(result.firstOwners.stream().flatMap(owner -> owner.values.stream()).toList())
        .containsExactlyInAnyOrderElementsOf(problem.firstValues);
    assertThat(result.secondOwners.stream().flatMap(owner -> owner.values.stream()).toList())
        .containsExactlyInAnyOrderElementsOf(problem.secondValues);
    assertThat(result.basicEntities).allSatisfy(entity -> assertThat(entity.value).isEqualTo(1L));
    assertThat(result.score).isEqualTo(SimpleScore.of(3));
    assertThat(result.score).isEqualTo(new EmptyFamilyScoreCalculator().calculateScore(result));
  }

  public static class EmptyFamilyScoreCalculator
      implements EasyScoreCalculator<EmptyFamilySolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(EmptyFamilySolution solution) {
      return SimpleScore.of(
          solution.basicEntities.stream()
              .filter(entity -> entity.value != null)
              .mapToInt(entity -> entity.value.intValue())
              .sum());
    }
  }

  @PlanningEntity
  public static class FirstOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<String> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class SecondOwner {
    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Integer> values = new ArrayList<>();
  }

  @PlanningEntity
  public static class BasicEntity {
    @PlanningVariable(valueRangeProviderRefs = "basicRange")
    public Long value;
  }

  @PlanningSolution
  public static class EmptyFamilySolution {
    @PlanningEntityCollectionProperty public List<FirstOwner> firstOwners = new ArrayList<>();
    @PlanningEntityCollectionProperty public List<SecondOwner> secondOwners = new ArrayList<>();
    @PlanningEntityCollectionProperty public List<BasicEntity> basicEntities = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "firstRange")
    public List<String> firstValues = List.of();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "secondRange")
    public List<Integer> secondValues = List.of();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "basicRange")
    public List<Long> basicValues = List.of(0L, 1L);

    @PlanningScore public SimpleScore score;
  }
}
