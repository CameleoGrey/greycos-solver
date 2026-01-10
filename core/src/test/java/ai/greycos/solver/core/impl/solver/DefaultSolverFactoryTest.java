package ai.greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThatCode;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.SolverConfigOverride;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.impl.solver.random.RandomFactory;
import ai.greycos.solver.core.testdomain.TestdataSolution;
import ai.greycos.solver.core.testdomain.invalid.noentity.TestdataNoEntitySolution;

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
}
