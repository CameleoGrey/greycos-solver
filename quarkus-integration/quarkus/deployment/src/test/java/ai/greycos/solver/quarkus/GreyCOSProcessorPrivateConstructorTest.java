package ai.greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorConstraintProvider;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorEntity;
import ai.greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorPrivateConstructorTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey("quarkus.greycos.solver.termination.best-score-limit", "0")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          PrivateNoArgsConstructorConstraintProvider.class,
                          PrivateNoArgsConstructorSolution.class,
                          PrivateNoArgsConstructorEntity.class));

  @Inject SolverManager<PrivateNoArgsConstructorSolution> solverManager;

  @Test
  void canConstructBeansWithPrivateConstructors() throws ExecutionException, InterruptedException {
    PrivateNoArgsConstructorSolution problem =
        new PrivateNoArgsConstructorSolution(
            Arrays.asList(
                new PrivateNoArgsConstructorEntity("1"),
                new PrivateNoArgsConstructorEntity("2"),
                new PrivateNoArgsConstructorEntity("3")));
    PrivateNoArgsConstructorSolution solution =
        solverManager.solve(1L, problem).getFinalBestSolution();
    assertThat(solution.score.score()).isZero();
    assertThat(solution.someField).isEqualTo(2);
  }
}
