package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import jakarta.inject.Inject;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.impl.cotwin.solution.cloner.gizmo.GizmoSolutionCloner;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorConstraintProvider;
import greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorEntity;
import greycos.solver.quarkus.testcotwin.gizmo.PrivateNoArgsConstructorSolution;

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

  @Inject SolverFactory<PrivateNoArgsConstructorSolution> solverFactory;

  @Test
  void clonePrivateAndFinalFields() {
    var cloner =
        ((DefaultSolverFactory<PrivateNoArgsConstructorSolution>) solverFactory)
            .getSolutionDescriptor()
            .getSolutionCloner();
    assertThat(cloner).isInstanceOf(GizmoSolutionCloner.class);

    var first = new PrivateNoArgsConstructorEntity("1");
    first.setValue("1");
    var second = new PrivateNoArgsConstructorEntity("2");
    second.setValue("2");
    var original = new PrivateNoArgsConstructorSolution(new ArrayList<>(List.of(first, second)));
    original.setScore(SimpleScore.of(7));

    var clone = cloner.cloneSolution(original);

    assertThat(clone).isNotSameAs(original);
    assertThat(clone.getScore()).isEqualTo(original.getScore());
    assertThat(clone.getSomeField()).isEqualTo(2);
    assertThat(clone.getPlanningEntityList())
        .isNotSameAs(original.getPlanningEntityList())
        .hasSize(2);
    var firstClone = clone.getPlanningEntityList().get(0);
    var secondClone = clone.getPlanningEntityList().get(1);
    assertThat(firstClone).isNotSameAs(first);
    assertThat(secondClone).isNotSameAs(second);
    assertThat(firstClone.getId()).isEqualTo("1");
    assertThat(secondClone.getId()).isEqualTo("2");
    assertThat(firstClone.getValue()).isEqualTo("1");
    assertThat(secondClone.getValue()).isEqualTo("2");
    assertThat(firstClone.testReadRawState()).isEqualTo("Raw state (1)");
    assertThat(secondClone.testReadRawState()).isEqualTo("Raw state (2)");

    first.setValue("3");
    assertThat(firstClone.getValue()).isEqualTo("1");
    secondClone.setValue("3");
    assertThat(second.getValue()).isEqualTo("2");
    original.getPlanningEntityList().remove(second);
    assertThat(clone.getPlanningEntityList()).hasSize(2);
  }

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
