package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseEventProducerId;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ResumablePartitionEventTest {

  @Test
  void rootPartitionPublishesOneLogicalEventPerImprovementAcrossWorkerCounts() throws Exception {
    List<Publication> reference = null;
    for (int workers : new int[] {1, 2, 4}) {
      var workload = new BasicWorkload();
      var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 8);
      var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      config.withPhases(
          partition(sessions(search, 3, workers, 2, ResumableSearchCooperationPolicy.NONE)));
      var solver =
          (DefaultSolver<BasicSolution>) SolverFactory.<BasicSolution>create(config).buildSolver();
      var publications = new ArrayList<Publication>();
      var improvedSteps = new AtomicInteger();
      var observedRound = new AtomicInteger(-1);
      var coordinator = Thread.currentThread();
      var previousScore = new SimpleScore[] {workload.recompute(workload.createProblem(24))};
      solver.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void stepEnded(AbstractStepScope<BasicSolution> step) {
              if (step instanceof PartitionedSearchStepScope && step.getBestScoreImproved())
                improvedSteps.incrementAndGet();
            }
          });
      solver.addEventListener(
          event -> {
            if (event.getProducerId().phaseIndex().isEmpty()) return;
            assertThat(Thread.currentThread()).isSameAs(coordinator);
            assertThat(event.getProducerId()).isInstanceOf(ResumableSearchEventProducerId.class);
            var id = (ResumableSearchEventProducerId) event.getProducerId();
            assertThat(id.outerPhaseIndex()).isZero();
            assertThat(id.modelRevision()).isZero();
            assertThat(id.episode()).isZero();
            assertThat(id.round()).isEqualTo(observedRound.get() + 1L);
            assertThat(id.sessionPath()).matches("phase-00000000/part-0000000[01]");
            var snapshot = event.getNewBestSolution();
            assertThat(snapshot.getJobs()).hasSize(24);
            var independentlyScored = workload.recompute(snapshot);
            assertThat(event.getNewBestScore()).isEqualTo(independentlyScored);
            assertThat(independentlyScored.compareTo(previousScore[0])).isPositive();
            previousScore[0] = independentlyScored;
            publications.add(new Publication(id, independentlyScored, workload.state(snapshot)));
          });
      BasicSolution result;
      try (var ignored =
          ResumableSearchRuntime.observeRounds(
              round -> observedRound.set(Math.toIntExact(round.round())), false)) {
        result = solver.solve(workload.createProblem(24));
      }
      assertThat(result.getScore()).isEqualTo(workload.recompute(result));
      assertThat(publications).isNotEmpty().hasSize(improvedSteps.get());
      if (reference == null) reference = List.copyOf(publications);
      else assertThat(publications).isEqualTo(reference);
    }
  }

  @Test
  void nestedPartitionDoesNotExposePrivatePartSolutionsAsOuterEvents() {
    var workload = new BasicWorkload();
    var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 8);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    var root = sessions(search, 2, 2, 2, ResumableSearchCooperationPolicy.NONE);
    root.setPhaseConfigList(List.of(partition(search)));
    config.withPhases(root);
    var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
    var publications = new ArrayList<Publication>();
    var coordinator = Thread.currentThread();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().phaseIndex().isEmpty()) return;
          assertThat(Thread.currentThread()).isSameAs(coordinator);
          assertThat(event.getProducerId()).isInstanceOf(ResumableSearchEventProducerId.class);
          var id = (ResumableSearchEventProducerId) event.getProducerId();
          assertThat(id.sessionPath()).matches("phase-00000000/island-0000000[01]");
          assertThat(event.getNewBestSolution().getJobs()).hasSize(24);
          var independentScore = workload.recompute(event.getNewBestSolution());
          assertThat(event.getNewBestScore()).isEqualTo(independentScore);
          publications.add(
              new Publication(id, independentScore, workload.state(event.getNewBestSolution())));
        });

    var result = solver.solve(workload.createProblem(24));

    assertThat(publications).isNotEmpty();
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  @Test
  void partitionEventsAfterProblemChangeCarryTheNewModelRevision() throws Exception {
    var workload = new BasicWorkload();
    var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 8);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(partition(sessions(search, 3, 2, 2, ResumableSearchCooperationPolicy.NONE)));
    var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
    var changed = new AtomicBoolean();
    var requested = new AtomicBoolean();
    var revisions = new ArrayList<Long>();
    var lastScoreByRevision = new HashMap<Long, SimpleScore>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().phaseIndex().isEmpty()) return;
          assertThat(event.getProducerId()).isInstanceOf(ResumableSearchEventProducerId.class);
          var id = (ResumableSearchEventProducerId) event.getProducerId();
          assertThat(id.modelRevision()).isEqualTo(changed.get() ? 1L : 0L);
          revisions.add(id.modelRevision());
          var score = workload.recompute(event.getNewBestSolution());
          assertThat(event.getNewBestScore()).isEqualTo(score);
          var previous = lastScoreByRevision.put(id.modelRevision(), score);
          if (previous != null) assertThat(score.compareTo(previous)).isPositive();
        });
    BasicSolution result;
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              if (requested.compareAndSet(false, true)) {
                solver.addProblemChange(
                    (working, director) -> {
                      for (var job : working.getJobs())
                        director.changeVariable(
                            job,
                            "machine",
                            entity -> entity.setMachine(working.getMachines().getFirst()));
                      changed.set(true);
                    });
              }
            },
            false)) {
      result = solver.solve(workload.createProblem(24));
    }

    assertThat(revisions).contains(0L, 1L);
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  @Test
  void ordinaryPartitionSearchKeepsItsEstablishedEventIdentity() {
    var workload = new BasicWorkload();
    var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 8);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    config.withPhases(partition(search));
    var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
    var producers = new ArrayList<EventProducerId>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().phaseIndex().isPresent()) producers.add(event.getProducerId());
          assertThat(event.getNewBestScore())
              .isEqualTo(workload.recompute(event.getNewBestSolution()));
        });

    var result = solver.solve(workload.createProblem(24));

    assertThat(producers)
        .isNotEmpty()
        .allSatisfy(
            producer -> {
              assertThat(producer).isInstanceOf(PhaseEventProducerId.class);
              assertThat(producer.producerId()).isEqualTo("Partitioned Search (0)");
            });
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> child) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(ResumableSearchLifecycleTest.TwoPartitions.class)
        .withRunnablePartThreadLimit("1")
        .withPhaseConfigList(List.of(child));
  }

  private record Publication(
      ResumableSearchEventProducerId producer, SimpleScore score, String state) {}
}
