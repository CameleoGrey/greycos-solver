package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.quarkus.testcotwin.multiple.constraintprovider.TestdataMultipleConstraintSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorMultipleConstraintProviderTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addPackages(
                          true,
                          "ai.greycos.solver.quarkus.testcotwin.multiple.constraintprovider"));

  @Inject SolverFactory<TestdataMultipleConstraintSolution> solverFactory;

  @Test
  void readOnlyConcreteProviderClass() {
    var problem = TestdataMultipleConstraintSolution.generateSolution(3, 2);
    assertThatCode(() -> solverFactory.buildSolver().solve(problem)).doesNotThrowAnyException();
  }
}
