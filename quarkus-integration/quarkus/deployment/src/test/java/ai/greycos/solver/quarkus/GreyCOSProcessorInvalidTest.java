package ai.greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.score.ScoreManager;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation.TestdataInvalidInverseRelationEntity;
import ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation.TestdataInvalidInverseRelationSolution;
import ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation.TestdataInvalidInverseRelationValue;
import ai.greycos.solver.quarkus.testcotwin.invalid.inverserelation.TestdataInvalidQuarkusConstraintProvider;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorInvalidTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataInvalidInverseRelationSolution.class,
                          TestdataInvalidInverseRelationEntity.class,
                          TestdataInvalidInverseRelationValue.class,
                          TestdataInvalidQuarkusConstraintProvider.class))
          .assertException(
              exception -> {
                assertEquals(IllegalStateException.class, exception.getClass());
                assertEquals(
                    """
                        The field (entityList) with a @%s annotation is \
                        in a class (%s) \
                        that does not have a @%s annotation.
                        Maybe add a @%s annotation on the class (%s)."""
                        .formatted(
                            InverseRelationShadowVariable.class.getSimpleName(),
                            TestdataInvalidInverseRelationValue.class.getName(),
                            PlanningEntity.class.getSimpleName(),
                            PlanningEntity.class.getSimpleName(),
                            TestdataInvalidInverseRelationValue.class.getName()),
                    exception.getMessage());
              });

  @Inject SolverFactory<TestdataInvalidInverseRelationSolution> solverFactory;
  @Inject SolverManager<TestdataInvalidInverseRelationSolution, Long> solverManager;
  @Inject ScoreManager<TestdataInvalidInverseRelationSolution, SimpleScore> scoreManager;
  @Inject SolutionManager<TestdataInvalidInverseRelationSolution, SimpleScore> solutionManager;

  @Test
  void solve() {
    fail("Build should fail");
  }
}
