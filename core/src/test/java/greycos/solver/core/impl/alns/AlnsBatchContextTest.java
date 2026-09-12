package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicConstraints;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Machine;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlnsBatchContextTest {
  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void duplicateProbesRemainIndependentAndInvalidEntriesLeaveTheContextUsable(String threads) {
    try (var fixture = new Fixture(threads)) {
      var context = fixture.context;
      context.beginTrial();
      var target = context.targets().getFirst();
      var assignment = context.assignments(target).getLast();
      var expected = context.evaluate(assignment);
      assertThat(context.evaluateAssignments(List.of(assignment, assignment)))
          .containsExactly(expected, expected);
      fixture.assertOriginal();
      var removals = context.evaluateRemovals(List.of(target, target));
      assertThat(removals).hasSize(2);
      assertThat(removals.getFirst()).isEqualTo(removals.getLast());
      fixture.assertOriginal();

      assertThatThrownBy(() -> context.evaluateAssignments(null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> context.evaluateRemovals(null))
          .isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> context.evaluateAssignments(Arrays.asList(assignment, null)))
          .isInstanceOf(NullPointerException.class);
      fixture.assertOriginal();
      assertThatThrownBy(() -> context.evaluateRemovals(Arrays.asList(target, null)))
          .isInstanceOf(NullPointerException.class);
      fixture.assertOriginal();

      var foreignJob = fixture.workload.createProblem(20).getJobs().getFirst();
      var foreign =
          new AlnsTarget<BasicSolution>(target.variable(), foreignJob, foreignJob.getMachine());
      assertThatThrownBy(() -> context.evaluateRemovals(List.of(target, foreign)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("different working solution");
      assertThatThrownBy(
              () ->
                  context.evaluateAssignments(
                      List.of(
                          assignment,
                          new AlnsAssignment<>(foreign, foreignJob, assignment.value(), -1))))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  context.evaluateAssignments(
                      List.of(
                          assignment,
                          new AlnsAssignment<>(target, target.entity(), new Machine(999), -1))))
          .isInstanceOf(IllegalArgumentException.class);
      fixture.assertOriginal();

      assertThat(context.evaluateAssignments(List.of(assignment, assignment)))
          .containsExactly(expected, expected);
      assertThat(context.evaluateAssignments(List.of())).isEmpty();
      assertThat(context.evaluateRemovals(List.of())).isEmpty();
      fixture.assertOriginal();
      context.rollback();
    }
  }

  private static final class Fixture implements AutoCloseable {
    final BasicWorkload workload = new BasicWorkload();
    final BasicSolution solution = workload.createProblem(20);
    final String original = workload.state(solution);
    final SimpleScore originalScore = solution.getScore();
    final BavetConstraintStreamScoreDirector<BasicSolution, SimpleScore> director;
    final DefaultAlnsContext<BasicSolution, SimpleScore> context;

    Fixture(String threads) {
      var descriptor = SolutionDescriptor.buildSolutionDescriptor(BasicSolution.class, Job.class);
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<BasicSolution, SimpleScore>(
              descriptor, new BasicConstraints(), EnvironmentMode.NO_ASSERT, false);
      director = factory.createScoreDirectorBuilder().build();
      director.setWorkingSolution(solution);
      director.calculateScore();
      context = new DefaultAlnsContext<>(director, new Random(0), () -> false);
      context.configureMoveThreads(
          threads.equals("NONE") ? null : Integer.valueOf(threads),
          2,
          runnable -> new Thread(runnable, "alns-batch-validation"),
          0,
          EnvironmentMode.NO_ASSERT);
    }

    void assertOriginal() {
      assertThat(workload.state(solution)).isEqualTo(original);
      assertThat(director.calculateScore().raw()).isEqualTo(originalScore);
      assertThat(context.isChanged()).isFalse();
    }

    @Override
    public void close() {
      context.close();
      director.close();
    }
  }
}
