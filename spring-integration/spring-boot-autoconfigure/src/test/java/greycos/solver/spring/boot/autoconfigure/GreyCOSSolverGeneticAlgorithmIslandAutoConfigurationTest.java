package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.spring.boot.autoconfigure.normal.NormalSpringTestConfiguration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ResourceLock("yamlAndXml")
class GreyCOSSolverGeneticAlgorithmIslandAutoConfigurationTest {

  @Test
  @Timeout(30)
  void xmlConfiguredIslandGeneticAlgorithmWithMigrationSolvesThroughAutoConfiguredBeans() {
    solveThroughAutoConfiguredBeans(true);
  }

  @Test
  @Timeout(30)
  void xmlConfiguredIslandGeneticAlgorithmWithoutMigrationSolvesThroughAutoConfiguredBeans() {
    solveThroughAutoConfiguredBeans(false);
  }

  @Test
  @Timeout(30)
  void aotRestoredIslandGeneticAlgorithmWithMigrationBuildsAndSolves() {
    solveThroughAotRestoredConfiguration(true);
  }

  @Test
  @Timeout(30)
  void aotRestoredIslandGeneticAlgorithmWithoutMigrationBuildsAndSolves() {
    solveThroughAotRestoredConfiguration(false);
  }

  @SuppressWarnings("unchecked")
  private static void solveThroughAutoConfiguredBeans(boolean migration) {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
        .withUserConfiguration(NormalSpringTestConfiguration.class)
        .withPropertyValues("greycos.solver-config-xml=" + resource(migration))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertConfiguration(context.getBean(SolverConfig.class), migration);
              SolverFactory<TestdataSolution> factory = context.getBean(SolverFactory.class);
              var result = solveAndReplay(factory);
              var replayedScore = SimpleScore.of(-result.getEntityList().size());
              SolutionManager<TestdataSolution, SimpleScore> manager =
                  context.getBean(SolutionManager.class);
              assertThat(manager.update(result)).isEqualTo(replayedScore);
            });
  }

  private static void solveThroughAotRestoredConfiguration(boolean migration) {
    var config = SolverConfig.createFromXmlResource(resource(migration));
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);

    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());

    assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    assertConfiguration(restored, migration);
    solveAndReplay(SolverFactory.create(restored));
  }

  private static String resource(boolean migration) {
    return "greycos/solver/spring/boot/autoconfigure/geneticAlgorithmIsland"
        + (migration ? "Migration" : "NoMigration")
        + "SolverConfig.xml";
  }

  private static void assertConfiguration(SolverConfig config, boolean migration) {
    assertThat(config.getSolutionClass()).isEqualTo(TestdataSolution.class);
    assertThat(config.getEntityClassList()).containsExactly(TestdataEntity.class);
    assertThat(config.getScoreDirectorFactoryConfig().getConstraintProviderClass())
        .isEqualTo(TestdataConstraintProvider.class);
    assertThat(config.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(IslandModelPhaseConfig.class);
    var island = (IslandModelPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(island.getIslandCount()).isEqualTo(2);
    assertThat(island.getMigrationFrequency()).isEqualTo(1);
    assertThat(island.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
    var ga = (GeneticAlgorithmPhaseConfig) island.getPhaseConfigList().getFirst();
    assertThat(ga.getPopulationSize()).isEqualTo(4);
    assertThat(ga.getMigrationRate()).isEqualTo(migration ? 0.5 : 0.0);
    assertThat(ga.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
    assertThat(ga.resolve().getLocalImprovementMoveCountLimit()).isZero();
  }

  private static TestdataSolution solveAndReplay(SolverFactory<TestdataSolution> factory) {
    var problem = TestdataSolution.generateSolution(3, 6);
    var originalAssignments =
        problem.getEntityList().stream().map(entity -> entity.getValue().getCode()).toList();
    var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();

    var result = solver.solve(problem);

    assertThat(solver.getSolverScope().getReportedMoveEvaluationCount()).isEqualTo(22L);
    assertThat(result.getEntityList())
        .hasSize(6)
        .allSatisfy(entity -> assertThat(entity.getValue()).isIn(result.getValueList()));
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-result.getEntityList().size()));
    assertThat(problem.getEntityList().stream().map(entity -> entity.getValue().getCode()))
        .containsExactlyElementsOf(originalAssignments);
    return result;
  }
}
