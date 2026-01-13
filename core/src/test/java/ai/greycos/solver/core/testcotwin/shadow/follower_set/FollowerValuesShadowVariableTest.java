package ai.greycos.solver.core.testcotwin.shadow.follower_set;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;
import ai.greycos.solver.core.impl.solver.MoveAsserter;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import ai.greycos.solver.core.preview.api.move.builtin.Moves;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.shadow.follower.TestdataFollowerConstraintProvider;
import ai.greycos.solver.core.testcotwin.shadow.follower.TestdataLeaderEntity;

import org.junit.jupiter.api.Test;

class FollowerValuesShadowVariableTest {
  @Test
  void testSolve() {
    var problem = TestdataFollowerSetSolution.generateSolution(3, 8, 2);

    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataFollowerSetSolution.class)
            .withEntityClasses(TestdataLeaderEntity.class, TestdataFollowerSetEntity.class)
            .withConstraintProviderClass(TestdataFollowerConstraintProvider.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1_000L));

    var solverFactory = SolverFactory.<TestdataFollowerSetSolution>create(solverConfig);
    var solver = solverFactory.buildSolver();
    var solution = solver.solve(problem);

    for (var follower : solution.getFollowers()) {
      assertThat(follower.getValue()).isEqualTo(follower.valueSupplier());
    }
  }

  @Test
  void testMove() {
    var leaderA = new TestdataLeaderEntity("A");
    var leaderB = new TestdataLeaderEntity("B");
    var leaderC = new TestdataLeaderEntity("C");

    var followerAB = new TestdataFollowerSetEntity("AB", List.of(leaderA, leaderB));
    var followerAC = new TestdataFollowerSetEntity("AC", List.of(leaderA, leaderC));
    var followerBC = new TestdataFollowerSetEntity("BC", List.of(leaderB, leaderC));

    var value1 = new TestdataValue("1");
    var value2 = new TestdataValue("2");

    var solution =
        new TestdataFollowerSetSolution(
            "Solution",
            List.of(leaderA, leaderB, leaderC),
            List.of(followerAB, followerAC, followerBC),
            List.of(value1, value2));

    var solutionDescriptor = TestdataFollowerSetSolution.getSolutionDescriptor();
    var variableMetamodel =
        (PlanningVariableMetaModel<
                TestdataFollowerSetSolution, ? super TestdataLeaderEntity, ? super TestdataValue>)
            solutionDescriptor.getMetaModel().entity(TestdataLeaderEntity.class).variable("value");
    var moveAsserter = MoveAsserter.create(solutionDescriptor);

    moveAsserter.assertMoveAndApply(
        solution,
        Moves.change(variableMetamodel, leaderA, value1),
        newSolution -> {
          assertThat(followerAB.getValue()).isEqualTo(value1);
          assertThat(followerAC.getValue()).isEqualTo(value1);
          assertThat(followerBC.getValue()).isEqualTo(null);
        });

    moveAsserter.assertMoveAndApply(
        solution,
        Moves.change(variableMetamodel, leaderB, value2),
        newSolution -> {
          assertThat(followerAB.getValue()).isEqualTo(value1);
          assertThat(followerAC.getValue()).isEqualTo(value1);
          assertThat(followerBC.getValue()).isEqualTo(value2);
        });

    moveAsserter.assertMoveAndApply(
        solution,
        Moves.change(variableMetamodel, leaderC, value1),
        newSolution -> {
          assertThat(followerAB.getValue()).isEqualTo(value1);
          assertThat(followerAC.getValue()).isEqualTo(value1);
          assertThat(followerBC.getValue()).isEqualTo(value1);
        });
  }
}
