package ai.greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.OptionalInt;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverConfigOverride;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.impl.solver.random.RandomFactory;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.invalid.noentity.TestdataNoEntitySolution;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class DefaultSolverFactoryTest {

  @Test
  void cachesScoreDirectorFactory() {
    SolverConfig solverConfig =
        SolverConfig.createFromXmlResource(
            "ai/greycos/solver/core/config/solver/testdataSolverConfig.xml");
    DefaultSolverFactory<TestdataSolution> defaultSolverFactory =
        new DefaultSolverFactory<>(solverConfig);

    SolutionDescriptor<TestdataSolution> solutionDescriptor1 =
        defaultSolverFactory.getSolutionDescriptor();
    ScoreDirectorFactory<TestdataSolution, SimpleScore> scoreDirectorFactory1 =
        defaultSolverFactory.getScoreDirectorFactory();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(solutionDescriptor1).isNotNull();
          softly.assertThat(scoreDirectorFactory1).isNotNull();
          softly
              .assertThat(scoreDirectorFactory1.getSolutionDescriptor())
              .isSameAs(solutionDescriptor1);
        });

    SolutionDescriptor<TestdataSolution> solutionDescriptor2 =
        defaultSolverFactory.getSolutionDescriptor();
    ScoreDirectorFactory<TestdataSolution, SimpleScore> scoreDirectorFactory2 =
        defaultSolverFactory.getScoreDirectorFactory();
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(solutionDescriptor2).isSameAs(solutionDescriptor1);
          softly.assertThat(scoreDirectorFactory2).isSameAs(scoreDirectorFactory1);
        });
  }

  @Test
  void testNoSolutionConfiguration() {
    SolverConfig solverConfig = new SolverConfig();
    assertThatCode(() -> new DefaultSolverFactory<>(solverConfig))
        .hasMessageContaining("The solver configuration must have a solutionClass")
        .hasMessageContaining(
            "If you're using the Quarkus extension or Spring Boot starter, it should have been filled in already.");
  }

  @Test
  void testNoEntityConfiguration() {
    SolverConfig solverConfig = new SolverConfig();
    solverConfig.setSolutionClass(TestdataNoEntitySolution.class);
    assertThatCode(() -> new DefaultSolverFactory<>(solverConfig))
        .hasMessageContaining("The solver configuration must have at least 1 entityClass")
        .hasMessageContaining(
            "If you're using the Quarkus extension or Spring Boot starter, it should have been filled in already.");
  }

  @Test
  void testInvalidRandomConfiguration() {
    SolverConfig solverConfig =
        SolverConfig.createFromXmlResource(
                "ai/greycos/solver/core/config/solver/testdataSolverConfig.xml")
            .withRandomFactoryClass(RandomFactory.class)
            .withRandomSeed(1000L);
    assertThatCode(
            () ->
                new DefaultSolverFactory<>(solverConfig).buildSolver(new SolverConfigOverride<>()))
        .hasMessageContaining("The solverConfig with randomFactoryClass ")
        .hasMessageContaining("has a non-null randomType (null) or a non-null randomSeed (1000).");
  }

  // ************************************************************************
  // MoveThreadCount resolution tests
  // ************************************************************************

  @Test
  void moveThreadCountAutoIsCorrectlyResolvedWhenCpuCountIsPositive() {
    assertThat(mockMoveThreadCountResolverAuto(1)).isEqualTo(OptionalInt.empty());
    assertThat(mockMoveThreadCountResolverAuto(2)).isEqualTo(OptionalInt.empty());
    assertThat(mockMoveThreadCountResolverAuto(4)).isEqualTo(OptionalInt.of(2));
    assertThat(mockMoveThreadCountResolverAuto(5)).isEqualTo(OptionalInt.of(3));
    assertThat(mockMoveThreadCountResolverAuto(6)).isEqualTo(OptionalInt.of(4));
    assertThat(mockMoveThreadCountResolverAuto(100)).isEqualTo(OptionalInt.of(4));
  }

  @Test
  void moveThreadCountAutoIsResolvedToEmptyWhenCpuCountIsNegative() {
    assertThat(mockMoveThreadCountResolverAuto(-1)).isEqualTo(OptionalInt.empty());
  }

  private OptionalInt mockMoveThreadCountResolverAuto(int mockCpuCount) {
    DefaultSolverFactory.MoveThreadCountResolver moveThreadCountResolverMock =
        new DefaultSolverFactory.MoveThreadCountResolver() {
          @Override
          protected int getAvailableProcessors() {
            return mockCpuCount;
          }
        };

    return moveThreadCountResolverMock.resolveMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_AUTO);
  }

  @Test
  void moveThreadCountIsCorrectlyResolvedWhenValueIsPositive() {
    assertThat(resolveMoveThreadCount("2")).isEqualTo(OptionalInt.of(2));
  }

  @Test
  void moveThreadCountThrowsExceptionWhenValueIsNegative() {
    assertThatIllegalArgumentException().isThrownBy(() -> resolveMoveThreadCount("-1"));
  }

  @Test
  void moveThreadCountIsResolvedToEmptyWhenValueIsNone() {
    assertThat(resolveMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE))
        .isEqualTo(OptionalInt.empty());
  }

  private OptionalInt resolveMoveThreadCount(String moveThreadCountString) {
    DefaultSolverFactory.MoveThreadCountResolver moveThreadCountResolver =
        new DefaultSolverFactory.MoveThreadCountResolver();
    return moveThreadCountResolver.resolveMoveThreadCount(moveThreadCountString);
  }
}
