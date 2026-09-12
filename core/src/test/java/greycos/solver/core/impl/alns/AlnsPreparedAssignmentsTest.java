package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationSource;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated("Changes internal ALNS diagnostic properties to compare independent execution paths")
@Timeout(120)
class AlnsPreparedAssignmentsTest {

  private static final String LAZY_REBASING = "greycos.solver.alns.lazyProbeRebasing";

  @Test
  void lazyBasicRebasingLooksUpOnlyCommonReferencesAndRequestedOrdinals() {
    String previous = System.getProperty(LAZY_REBASING);
    System.setProperty(LAZY_REBASING, "true");
    var workload = new BasicWorkload();
    try (var parent = director(workload, 240);
        var worker = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
      var model = new AlnsModel<>(parent, () -> {});
      var target = model.targets(false).getFirst();
      var prepared = model.prepareAssignments(target);
      var lookedUp = new ArrayList<Object>();
      var rebased = prepared.rebase(countingLookup(worker, lookedUp));
      assertThat(lookedUp).containsExactly(target.entity(), model.current(target).value());
      int requested = prepared.size() / 4;
      assertThat(requested).isPositive();
      for (int i = 0; i < requested; i++) {
        var expected = parent.executeTemporaryMove(prepared.move(i), true);
        assertThat(worker.executeTemporaryMove(rebased.move(i), true)).isEqualTo(expected);
      }
      assertThat(lookedUp).hasSize(2 + requested);
      for (int i = 0; i < requested; i++) {
        assertThat(lookedUp.get(i + 2)).isSameAs(prepared.assignment(i).value());
      }
      assertThat(lookedUp.stream().filter(object -> object == target.entity())).hasSize(1);
    } finally {
      restore(LAZY_REBASING, previous);
    }
  }

  @Test
  void lazyBatchRebasesATargetOnlyWhenThisWorkerUsesIt() throws Exception {
    String previous = System.getProperty(LAZY_REBASING);
    System.setProperty(LAZY_REBASING, "true");
    try (var parent = director(new BasicWorkload(), 80);
        var worker = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
      var model = new AlnsModel<>(parent, () -> {});
      var targets = model.targets(false).subList(0, 2);
      var first = model.prepareAssignments(targets.get(0));
      var second = model.prepareAssignments(targets.get(1));
      var batch = preparedBatch(List.of(first, second));
      var lookedUp = new ArrayList<Object>();
      var rebased = batch.rebase(countingLookup(worker, lookedUp));
      assertThat(lookedUp).isEmpty();
      rebased.move(0);
      rebased.move(1);
      assertThat(lookedUp.stream().filter(object -> object == targets.get(0).entity())).hasSize(1);
      assertThat(lookedUp).doesNotContain(targets.get(1).entity());
      rebased.move(first.size());
      assertThat(lookedUp.stream().filter(object -> object == targets.get(1).entity())).hasSize(1);
    } finally {
      restore(LAZY_REBASING, previous);
    }
  }

  @Test
  void eagerDiagnosticRebasingStillResolvesUntouchedTargetsAndSourceCapturesItsSetting()
      throws Exception {
    String previous = System.getProperty(LAZY_REBASING);
    System.setProperty(LAZY_REBASING, "false");
    try (var parent = director(new BasicWorkload(), 80);
        var worker = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
      var model = new AlnsModel<>(parent, () -> {});
      var targets = model.targets(false).subList(0, 2);
      var sources = targets.stream().map(model::prepareAssignments).toList();
      System.setProperty(LAZY_REBASING, "true");
      var lookedUp = new ArrayList<Object>();
      preparedBatch(sources).rebase(countingLookup(worker, lookedUp));
      assertThat(lookedUp).contains(targets.get(0).entity(), targets.get(1).entity());
      assertThat(lookedUp.size())
          .isGreaterThan(sources.stream().mapToInt(AlnsPreparedAssignments::size).sum());
    } finally {
      restore(LAZY_REBASING, previous);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void eagerAndLazyRebasingPreserveAllOrdinalScoresAndTheOriginalSource(String shape) {
    verifyRebaseModes(AlnsMoveThreadingWorkload.named(shape));
  }

  private static <S> void verifyRebaseModes(Workload<S> workload) {
    String previous = System.getProperty(LAZY_REBASING);
    try (var parent = director(workload, 24);
        var worker = parent.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD)) {
      var model = new AlnsModel<>(parent, () -> {});
      String original = workload.state(parent.getWorkingSolution());
      for (var target : model.targets(false)) {
        var assignments = model.assignments(target);
        System.setProperty(LAZY_REBASING, "false");
        var eager = model.prepareAssignments(target).rebase(worker.getMoveDirector());
        System.setProperty(LAZY_REBASING, "true");
        var source = model.prepareAssignments(target);
        var lazy = source.rebase(worker.getMoveDirector());
        assertThat(lazy.size()).isEqualTo(eager.size());
        for (int ordinal = 0; ordinal < source.size(); ordinal++) {
          assertThat(worker.executeTemporaryMove(lazy.move(ordinal), true))
              .isEqualTo(worker.executeTemporaryMove(eager.move(ordinal), true));
          assertThat(source.assignment(ordinal)).isEqualTo(assignments.get(ordinal));
        }
      }
      assertThat(workload.state(parent.getWorkingSolution())).isEqualTo(original);
      assertThat(workload.state(worker.getWorkingSolution())).isEqualTo(original);
    } finally {
      restore(LAZY_REBASING, previous);
    }
  }

  private static Lookup countingLookup(InnerScoreDirector<?, ?> worker, List<Object> lookedUp) {
    return new Lookup() {
      @Override
      public <T> T lookUpWorkingObject(T object) {
        lookedUp.add(object);
        return worker.getMoveDirector().lookUpWorkingObject(object);
      }
    };
  }

  @SuppressWarnings("unchecked")
  private static <S> MoveEvaluationSource<S> preparedBatch(List<AlnsPreparedAssignments<S>> sources)
      throws Exception {
    var constructor =
        Class.forName(DefaultAlnsContext.class.getName() + "$PreparedBatch")
            .getDeclaredConstructor(List.class);
    constructor.setAccessible(true);
    return (MoveEvaluationSource<S>) constructor.newInstance(sources);
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void preparedSpansPreservePublicAssignmentOrderAndProbeScores(String shape) {
    verifyPreparation(AlnsMoveThreadingWorkload.named(shape));
  }

  private static <S> void verifyPreparation(Workload<S> workload) {
    try (var director = director(workload, 24);
        var context =
            new DefaultAlnsContext<S, SimpleScore>(director, new Random(0), () -> false)) {
      var model = new AlnsModel<>(director, context::checkTermination);
      context.beginTrial();
      var targets = context.targets();
      for (var target : targets.subList(0, Math.min(5, targets.size()))) {
        for (boolean destroy : List.of(false, true)) {
          if (destroy) context.destroy(target);
          var prepared = model.prepareAssignments(target);
          var publicAssignments = context.assignments(target);
          assertThat(prepared.size()).isEqualTo(publicAssignments.size());
          var expected = context.evaluateAssignments(publicAssignments);
          var original = workload.state(director.getWorkingSolution());
          for (int i = 0; i < prepared.size(); i++) {
            assertThat(prepared.assignment(i)).isEqualTo(publicAssignments.get(i));
            var actual = director.executeTemporaryMove(prepared.move(i), true);
            assertThat(actual.raw()).isEqualTo(expected.get(i).score());
            assertThat(actual.unassignedCount()).isEqualTo(expected.get(i).unassignedCount());
          }
          assertThat(workload.state(director.getWorkingSolution())).isEqualTo(original);
        }
      }
      context.rollback();
      assertThat(director.calculateScore().raw())
          .isEqualTo(workload.recompute(director.getWorkingSolution()));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void evaluatedAssignmentsRejectInterveningMutationAndTrialRollback(String shape) {
    verifyRevision(AlnsMoveThreadingWorkload.named(shape));
  }

  private static <S> void verifyRevision(Workload<S> workload) {
    try (var director = director(workload, 24);
        var context =
            new DefaultAlnsContext<S, SimpleScore>(director, new Random(0), () -> false)) {
      context.beginTrial();
      var targets = context.targets().subList(0, 2);
      context.setPendingTargets(targets);
      context.destroy(targets);
      var choices = context.bestAssignments(targets, 3);
      var stale = choices.get(1).getFirst();
      context.assignEvaluated(choices.getFirst().getFirst());
      assertThatThrownBy(() -> context.assignEvaluated(stale))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("stale");
      var fresh = context.bestAssignments(List.of(targets.get(1)), 3).getFirst().getFirst();
      context.assignEvaluated(fresh);
      assertThat(director.calculateScore().raw())
          .isEqualTo(workload.recompute(director.getWorkingSolution()));
      context.rollback();
      context.beginTrial();
      assertThatThrownBy(() -> context.assignEvaluated(fresh))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("stale");
      context.rollback();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void streamingAndChunkSizesMatchLegacyBatchesAcrossLongTargets(String shape) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    String oldPrepared = System.getProperty("greycos.solver.alns.preparedProbes");
    String oldChunk = System.getProperty("greycos.solver.alns.probeChunkSize");
    try {
      for (var repair : AlnsRepairOperatorType.values()) {
        System.setProperty("greycos.solver.alns.preparedProbes", "false");
        var baseline = run(workload, "NONE", repair);
        System.setProperty("greycos.solver.alns.preparedProbes", "true");
        for (int chunk : new int[] {1, 4, 8}) {
          System.setProperty("greycos.solver.alns.probeChunkSize", Integer.toString(chunk));
          for (var workers : List.of("NONE", "2", "8")) {
            var result = run(workload, workers, repair);
            assertThat(result.traceFingerprint())
                .as("%s %s chunk=%d workers=%s", shape, repair, chunk, workers)
                .isEqualTo(baseline.traceFingerprint());
            assertThat(result.probes()).isEqualTo(baseline.probes());
          }
        }
      }
    } finally {
      restore("greycos.solver.alns.preparedProbes", oldPrepared);
      restore("greycos.solver.alns.probeChunkSize", oldChunk);
    }
  }

  private static <S> AlnsMoveThreadingBenchmark.Result run(
      Workload<S> workload, String workers, AlnsRepairOperatorType repair) {
    return AlnsMoveThreadingBenchmark.run(
        workload, workers, 17L, 270, 3, repair, 1000, 3, true, EnvironmentMode.NO_ASSERT);
  }

  private static void restore(String name, String value) {
    if (value == null) System.clearProperty(name);
    else System.setProperty(name, value);
  }

  @SuppressWarnings("unchecked")
  private static <S> InnerScoreDirector<S, SimpleScore> director(Workload<S> workload, int size) {
    var config =
        workload.solverConfig("NONE", 0L, 1, new TerminationConfig(), EnvironmentMode.NO_ASSERT);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            (Class<S>) config.getSolutionClass(), config.getEntityClassList());
    var scoreConfig = config.getScoreDirectorFactoryConfig();
    var provider =
        ConfigUtils.newInstance(
            scoreConfig, "constraintProviderClass", scoreConfig.getConstraintProviderClass());
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<S, SimpleScore>(
            descriptor, provider, EnvironmentMode.NO_ASSERT, false);
    var director = factory.createScoreDirectorBuilder().build();
    director.setWorkingSolution(workload.createProblem(size));
    director.calculateScore();
    return director;
  }
}
