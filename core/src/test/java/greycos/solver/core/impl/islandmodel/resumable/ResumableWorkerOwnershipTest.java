package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForagerFactory;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated("Records the actual owning thread of each independently created session forager.")
@Timeout(60)
class ResumableWorkerOwnershipTest {

  @AfterEach
  void clearObservations() {
    OwnershipForager.INSTANCES.clear();
  }

  @ParameterizedTest
  @CsvSource({"16,16", "32,32", "64,64", "37,16", "65,32", "97,64"})
  void activeSessionsUseEveryWorkerWithBalancedFixedOwnership(int width, int workers)
      throws Exception {
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 3)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    search.getForagerConfig().setForagerClass(OwnershipForager.class);
    config.withPhases(
        sessions(search, width, workers, 1, ResumableSearchCooperationPolicy.NONE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(width * 3)));
    var ownersByPath = new HashMap<String, Thread>();
    var rounds = new AtomicInteger();
    try (var ignored =
        ResumableSearchRuntime.observeRounds(
            round -> {
              int completedRounds = rounds.incrementAndGet();
              assertThat(round.sessions()).hasSize(width);
              for (var session : round.sessions()) {
                var forager =
                    OwnershipForager.INSTANCES.get(
                        session.path()
                            + "/revision-"
                            + round.modelRevision()
                            + "/episode-"
                            + session.episode());
                assertThat(forager).isNotNull();
                ownersByPath.putIfAbsent(session.path(), forager.owner);
                assertThat(forager.owner).isSameAs(ownersByPath.get(session.path()));
                assertThat(forager.stepOwners)
                    .hasSize(completedRounds)
                    .allSatisfy(owner -> assertThat(owner).isSameAs(forager.owner));
              }
              var sessionsPerOwner =
                  ownersByPath.values().stream()
                      .collect(Collectors.groupingBy(owner -> owner, Collectors.counting()));
              assertThat(sessionsPerOwner).hasSize(workers);
              assertThat(sessionsPerOwner.values())
                  .allSatisfy(
                      count ->
                          assertThat(count)
                              .isBetween(
                                  (long) width / workers, (width + (long) workers - 1) / workers));
            },
            false)) {
      var result =
          SolverFactory.<BasicSolution>create(config)
              .buildSolver()
              .solve(workload.createProblem(8));
      assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    }
    assertThat(rounds).hasValue(3);
    assertThat(OwnershipForager.INSTANCES).hasSize(width);
    assertClosedOwners();
  }

  @Test
  void replacementEpisodesRetainTheirPredecessorsWorker() {
    var workload = new BasicWorkload();
    var config =
        ResumableSearchRuntimeTest.config(workload, "la", 37, 1000)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT);
    var search = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    search.getForagerConfig().setForagerClass(OwnershipForager.class);
    var phase =
        sessions(search, 4, 4, 4, ResumableSearchCooperationPolicy.RESTART_WEAKEST)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(256));
    phase.getResumableSearchConfig().setStagnationRounds(1);
    config.withPhases(phase);
    var result =
        SolverFactory.<BasicSolution>create(config).buildSolver().solve(workload.createProblem(24));
    assertThat(result.getScore()).isEqualTo(workload.recompute(result));
    assertThat(OwnershipForager.INSTANCES.size()).isGreaterThan(4);
    var episodesByPath =
        OwnershipForager.INSTANCES.entrySet().stream()
            .collect(
                Collectors.groupingBy(
                    entry -> entry.getKey().substring(0, entry.getKey().indexOf("/revision-")),
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    assertThat(episodesByPath).hasSize(4);
    for (var episodes : episodesByPath.values()) {
      var owner = episodes.getFirst().owner;
      assertThat(episodes).allSatisfy(episode -> assertThat(episode.owner).isSameAs(owner));
    }
    assertClosedOwners();
  }

  private static void assertClosedOwners() {
    assertThat(OwnershipForager.INSTANCES.values())
        .allSatisfy(
            forager -> {
              assertThat(forager.phasesStarted).isOne();
              assertThat(forager.phasesEnded).isOne();
              assertThat(forager.solvesEnded).isOne();
              assertThat(forager.owner.isAlive()).isFalse();
            });
  }

  /** Observes ownership without changing ordinary acceptance or foraging decisions. */
  public static final class OwnershipForager extends AbstractLocalSearchForager<Object> {
    static final Map<String, OwnershipForager> INSTANCES = new ConcurrentHashMap<>();
    private final LocalSearchForager<Object> delegate;
    private final List<Thread> stepOwners = new ArrayList<>();
    private Thread owner;
    private int phasesStarted;
    private int phasesEnded;
    private int solvesEnded;

    public OwnershipForager(LocalSearchForagerConfig config) {
      var delegateConfig = config.copyConfig();
      delegateConfig.setForagerClass(null);
      delegate = LocalSearchForagerFactory.<Object>create(delegateConfig).buildForager();
    }

    private void assertOwner() {
      assertThat(Thread.currentThread()).isSameAs(owner);
    }

    @Override
    public void solvingStarted(SolverScope<Object> scope) {
      owner = Thread.currentThread();
      assertThat(INSTANCES.putIfAbsent(scope.getMetricSource(), this)).isNull();
      delegate.solvingStarted(scope);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Object> scope) {
      assertOwner();
      phasesStarted++;
      delegate.phaseStarted(scope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<Object> scope) {
      assertOwner();
      delegate.stepStarted(scope);
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return delegate.supportsNeverEndingMoveSelector();
    }

    @Override
    public void addMove(LocalSearchMoveScope<Object> scope) {
      assertOwner();
      delegate.addMove(scope);
    }

    @Override
    public boolean isQuitEarly() {
      assertOwner();
      return delegate.isQuitEarly();
    }

    @Override
    public LocalSearchMoveScope<Object> pickMove(LocalSearchStepScope<Object> scope) {
      assertOwner();
      return delegate.pickMove(scope);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Object> scope) {
      assertOwner();
      stepOwners.add(Thread.currentThread());
      delegate.stepEnded(scope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Object> scope) {
      assertOwner();
      phasesEnded++;
      delegate.phaseEnded(scope);
    }

    @Override
    public void solvingEnded(SolverScope<Object> scope) {
      assertOwner();
      solvesEnded++;
      delegate.solvingEnded(scope);
    }
  }
}
