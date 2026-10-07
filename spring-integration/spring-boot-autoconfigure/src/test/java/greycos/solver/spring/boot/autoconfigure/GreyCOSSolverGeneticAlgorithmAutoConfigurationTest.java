package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.spring.boot.autoconfigure.normal.NormalSpringTestConfiguration;
import greycos.solver.spring.boot.autoconfigure.normal.constraints.TestdataSpringConstraintProvider;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringEntity;
import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ResourceLock("yamlAndXml")
class GreyCOSSolverGeneticAlgorithmAutoConfigurationTest {

  @Test
  @Timeout(30)
  @SuppressWarnings("unchecked")
  void geneticAlgorithmXmlUsesDiscoveredDomainAndConstraintProvider() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
        .withUserConfiguration(NormalSpringTestConfiguration.class)
        .withPropertyValues(
            "greycos.solver-config-xml=greycos/solver/spring/boot/autoconfigure/geneticAlgorithmSolverConfig.xml")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var config = context.getBean(SolverConfig.class);
              assertThat(config.getSolutionClass()).isEqualTo(TestdataSpringSolution.class);
              assertThat(config.getEntityClassList()).containsExactly(TestdataSpringEntity.class);
              assertThat(config.getScoreDirectorFactoryConfig().getConstraintProviderClass())
                  .isEqualTo(TestdataSpringConstraintProvider.class);
              assertThat(config.getPhaseConfigList())
                  .singleElement()
                  .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
              var phase = (GeneticAlgorithmPhaseConfig) config.getPhaseConfigList().getFirst();
              assertThat(phase.getPopulationSize()).isEqualTo(4);
              assertThat(phase.getMoveThreadCount()).isEqualTo("NONE");
              assertThat(phase.getMutationOperatorConfigList().getFirst().getType())
                  .isEqualTo(GeneticAlgorithmMutationType.CHANGE);
              SolverFactory<TestdataSpringSolution> factory = context.getBean(SolverFactory.class);
              var solver = (DefaultSolver<TestdataSpringSolution>) factory.buildSolver();
              var problem = new TestdataSpringSolution();
              problem.setValueList(List.of("a", "b", "c"));
              var entities =
                  List.of(
                      new TestdataSpringEntity(),
                      new TestdataSpringEntity(),
                      new TestdataSpringEntity(),
                      new TestdataSpringEntity());
              entities.forEach(entity -> entity.setValue("a"));
              problem.setEntityList(entities);
              var result = solver.solve(problem);
              assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(8L);
              assertThat(result.getEntityList())
                  .allSatisfy(entity -> assertThat(entity.getValue()).isIn(problem.getValueList()));
              int penalty = 0;
              for (var first : result.getEntityList()) {
                for (var second : result.getEntityList()) {
                  if (first != second && first.getValue().equals(second.getValue())) {
                    penalty++;
                  }
                }
              }
              var replayedScore = SimpleScore.of(-penalty);
              assertThat(result.getScore()).isEqualTo(replayedScore);
              SolutionManager<TestdataSpringSolution, SimpleScore> manager =
                  context.getBean(SolutionManager.class);
              assertThat(manager.update(result)).isEqualTo(replayedScore);
            });
  }
}
