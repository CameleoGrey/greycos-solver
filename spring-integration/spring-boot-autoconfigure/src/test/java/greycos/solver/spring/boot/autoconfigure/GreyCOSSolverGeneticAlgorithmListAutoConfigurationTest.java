package greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.spring.boot.autoconfigure.normal.NormalSpringTestConfiguration;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@ResourceLock("yamlAndXml")
class GreyCOSSolverGeneticAlgorithmListAutoConfigurationTest {

  private static final String SOLVER_XML =
      "greycos/solver/spring/boot/autoconfigure/geneticAlgorithmMixedSolverConfig.xml";

  @Test
  @Timeout(30)
  @SuppressWarnings("unchecked")
  void xmlConfiguredMixedDomainSolvesThroughAutoConfiguredBeans() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
        .withUserConfiguration(NormalSpringTestConfiguration.class)
        .withPropertyValues("greycos.solver-config-xml=" + SOLVER_XML)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var config = context.getBean(SolverConfig.class);
              assertMixedConfiguration(config);
              SolverFactory<TestdataMixedSolution> factory = context.getBean(SolverFactory.class);
              var result = solveAndReplay(factory);
              SolutionManager<TestdataMixedSolution, SimpleScore> manager =
                  context.getBean(SolutionManager.class);
              assertThat(manager.update(result)).isEqualTo(replayScore(result));
            });
  }

  @Test
  @Timeout(30)
  void aotRestoredMixedConfigurationBuildsAndSolves() {
    var config = SolverConfig.createFromXmlResource(SOLVER_XML);
    var writer = new StringWriter();
    new SolverConfigIO().write(config, writer);

    var restored = new GreyCOSSolverAotFactory().solverConfigSupplier(writer.toString());

    assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    assertMixedConfiguration(restored);
    solveAndReplay(SolverFactory.create(restored));
  }

  private static void assertMixedConfiguration(SolverConfig config) {
    assertThat(config.getSolutionClass()).isEqualTo(TestdataMixedSolution.class);
    assertThat(config.getEntityClassList())
        .containsExactly(
            TestdataMixedEntity.class, TestdataMixedValue.class, TestdataMixedOtherValue.class);
    assertThat(config.getScoreDirectorFactoryConfig().getConstraintProviderClass())
        .isEqualTo(MixedConstraints.class);
    assertThat(config.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(GeneticAlgorithmPhaseConfig.class);
    var phase = (GeneticAlgorithmPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.getMoveThreadCount()).isEqualTo(SolverConfig.MOVE_THREAD_COUNT_NONE);
    assertThat(phase.getCrossoverProbability()).isEqualTo(1.0);
    assertThat(phase.resolve().getMutationOperatorConfigList())
        .extracting(GeneticAlgorithmMutationOperatorConfig::getType)
        .containsExactly(GeneticAlgorithmMutationType.values());
  }

  private static TestdataMixedSolution solveAndReplay(
      SolverFactory<TestdataMixedSolution> factory) {
    var problem = TestdataMixedSolution.generateUninitializedSolution(3, 9, 3);
    for (var entity : problem.getEntityList()) {
      entity.setBasicValue(problem.getOtherValueList().getFirst());
      entity.setSecondBasicValue(problem.getOtherValueList().getFirst());
    }
    problem.getEntityList().getFirst().getValueList().addAll(problem.getValueList());
    SolutionManager.updateShadowVariables(problem);
    var initialScore = replayScore(problem);
    var solver = (DefaultSolver<TestdataMixedSolution>) factory.buildSolver();

    var result = solver.solve(problem);

    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(16L);
    assertThat(result.getScore())
        .isEqualTo(replayScore(result))
        .isGreaterThanOrEqualTo(initialScore);
    assertThat(result.getEntityList().stream().flatMap(entity -> entity.getValueList().stream()))
        .containsExactlyInAnyOrderElementsOf(result.getValueList());
    for (var entity : result.getEntityList()) {
      assertThat(entity.getBasicValue()).isIn(result.getOtherValueList());
      assertThat(entity.getSecondBasicValue()).isIn(result.getOtherValueList());
      assertThat(entity.getDeclarativeShadowVariableValue())
          .isEqualTo(entity.getBasicValue().getStrength());
      var values = entity.getValueList();
      for (int index = 0; index < values.size(); index++) {
        var value = values.get(index);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(index);
        assertThat(value.getPreviousElement()).isSameAs(index == 0 ? null : values.get(index - 1));
        assertThat(value.getNextElement())
            .isSameAs(index == values.size() - 1 ? null : values.get(index + 1));
        assertThat(value.getCascadingShadowVariableValue()).isEqualTo(index + 1);
        assertThat(value.getDeclarativeShadowVariableValue()).isEqualTo(index + 2);
      }
    }
    for (var otherValue : result.getOtherValueList()) {
      var owners =
          result.getEntityList().stream()
              .filter(entity -> entity.getBasicValue() == otherValue)
              .toList();
      assertThat(otherValue.getEntityList()).containsExactlyInAnyOrderElementsOf(owners);
      assertThat(otherValue.getDeclarativeShadowVariableValue()).isEqualTo(owners.size() + 2);
    }
    // The caller's initialized input remains intact after the working solution was mutated.
    assertThat(problem.getEntityList().getFirst().getValueList())
        .containsExactlyElementsOf(problem.getValueList());
    assertThat(problem.getEntityList().subList(1, 3))
        .allSatisfy(entity -> assertThat(entity.getValueList()).isEmpty());
    assertThat(problem.getEntityList())
        .allSatisfy(
            entity -> {
              assertThat(entity.getBasicValue()).isSameAs(problem.getOtherValueList().getFirst());
              assertThat(entity.getSecondBasicValue())
                  .isSameAs(problem.getOtherValueList().getFirst());
            });
    return result;
  }

  private static SimpleScore replayScore(TestdataMixedSolution solution) {
    int penalty = 0;
    for (var entity : solution.getEntityList()) {
      penalty += entity.getValueList().size() * entity.getValueList().size();
      penalty += entity.getBasicValue().getStrength() + entity.getSecondBasicValue().getStrength();
      for (int index = 0; index < entity.getValueList().size(); index++) {
        penalty += index + 1;
      }
    }
    return SimpleScore.of(-penalty);
  }

  public static final class MixedConstraints implements ConstraintProvider {

    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMixedEntity.class)
            .penalize(
                SimpleScore.ONE,
                entity ->
                    entity.getValueList().size() * entity.getValueList().size()
                        + entity.getBasicValue().getStrength()
                        + entity.getSecondBasicValue().getStrength())
            .asConstraint("List load and basic assignments"),
        factory
            .forEach(TestdataMixedValue.class)
            .penalize(SimpleScore.ONE, value -> value.getIndex() + 1)
            .asConstraint("List index")
      };
    }
  }
}
