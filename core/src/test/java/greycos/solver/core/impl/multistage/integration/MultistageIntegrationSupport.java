package greycos.solver.core.impl.multistage.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

public final class MultistageIntegrationSupport {

  private MultistageIntegrationSupport() {}

  static SolverConfig basicConfig(String workers) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withConstraintProviderClass(NumberConstraints.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(7L);
  }

  static TestdataSolution basicProblem(int entityCount) {
    var problem = new TestdataSolution(UUID.randomUUID().toString());
    var values = new ArrayList<TestdataValue>();
    for (int i = 0; i < 5; i++) {
      values.add(new TestdataValue(Integer.toString(i)));
    }
    problem.setValueList(values);
    var entities = new ArrayList<TestdataEntity>();
    for (int i = 0; i < entityCount; i++) {
      entities.add(new TestdataEntity(Integer.toString(i), values.getFirst()));
    }
    problem.setEntityList(entities);
    return problem;
  }

  static List<String> assignments(TestdataSolution solution) {
    return solution.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList();
  }

  static void assertBasicScore(TestdataSolution solution) {
    long independentScore =
        solution.getEntityList().stream()
            .mapToLong(entity -> Long.parseLong(entity.getValue().getCode()))
            .sum();
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(independentScore));
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(solution.getValueList()).contains(entity.getValue()));
  }

  static <Solution_> DefaultSolver<Solution_> solver(SolverConfig config) {
    return (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
  }

  static List<BasicStep> recordBasicSteps(DefaultSolver<TestdataSolution> solver) {
    var steps = new ArrayList<BasicStep>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var solution = scope.getWorkingSolution();
            assertThat(scope.getScore().isFullyAssigned()).isTrue();
            var score = (SimpleScore) scope.getScore().raw();
            long independentScore =
                solution.getEntityList().stream()
                    .mapToLong(entity -> Long.parseLong(entity.getValue().getCode()))
                    .sum();
            assertThat(score).isEqualTo(SimpleScore.of(independentScore));
            steps.add(new BasicStep(assignments(solution), score));
          }
        });
    return steps;
  }

  record BasicStep(List<String> assignments, SimpleScore score) {}

  public static final class NumberConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .reward(SimpleScore.ONE, entity -> Long.parseLong(entity.getValue().getCode()))
            .asConstraint("Assigned numeric values")
      };
    }
  }

  static SolverConfig listConfig(String workers) {
    return new SolverConfig()
        .withSolutionClass(TestdataListSolution.class)
        .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
        .withConstraintProviderClass(ListConstraints.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(7L);
  }

  static TestdataListSolution listProblem() {
    var values =
        List.of(new TestdataListValue("0"), new TestdataListValue("1"), new TestdataListValue("2"));
    var problem = new TestdataListSolution();
    problem.setValueList(new ArrayList<>(values));
    problem.setEntityList(
        new ArrayList<>(
            List.of(
                TestdataListEntity.createWithValues(
                    "source", values.get(2), values.get(1), values.get(0)),
                TestdataListEntity.createWithValues("target"))));
    return problem;
  }

  static void assertListScoreAndShadows(TestdataListSolution solution) {
    long independentScore = 0;
    var assigned = new ArrayList<TestdataListValue>();
    for (var entity : solution.getEntityList()) {
      for (int index = 0; index < entity.getValueList().size(); index++) {
        var value = entity.getValueList().get(index);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        assigned.add(value);
        if (entity.getCode().equals("target")) {
          independentScore += Long.parseLong(value.getCode()) + 1;
        }
      }
    }
    assertThat(assigned).containsExactlyInAnyOrderElementsOf(solution.getValueList());
    assertThat(solution.getScore()).isEqualTo(SimpleScore.of(independentScore));
  }

  public static final class ListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataListValue.class)
            .filter(value -> value.getEntity().getCode().equals("target"))
            .reward(SimpleScore.ONE, value -> Long.parseLong(value.getCode()) + 1)
            .asConstraint("Values assigned to target")
      };
    }
  }
}
