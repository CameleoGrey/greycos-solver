package greycos.solver.core.impl.islandmodel.resumable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListWorkload;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForagerFactory;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

/** Shared configuration and independent observation, kept entirely outside production code. */
public final class ResumableSearchTestSupport {
  private ResumableSearchTestSupport() {}

  public static IslandModelPhaseConfig sessions(
      LocalSearchPhaseConfig search,
      int lanes,
      int workers,
      int quantum,
      ResumableSearchCooperationPolicy policy) {
    return new IslandModelPhaseConfig()
        .withIslandCount(lanes)
        .withMoveThreadCount("NONE")
        .withResumableSearchConfig(
            new ResumableSearchConfig()
                .withWorkerCount(workers)
                .withStepQuantum(quantum)
                .withCooperationPolicy(policy)
                .withStagnationRounds(4))
        .withPhaseConfigList(List.of(search));
  }

  public static void controller(SolverConfig config, LocalSearchPhaseConfig phase, String name) {
    phase.getMoveSelectorConfig().setSelectedCountLimit(512L);
    switch (name) {
      case "la" -> {
        phase.setAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(400));
        phase.setForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
      }
      case "dla" -> {
        config.withPreviewFeature(PreviewFeature.DIVERSIFIED_LATE_ACCEPTANCE);
        phase.setAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE))
                .withLateAcceptanceSize(64));
        phase.setForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(16));
      }
      case "gls" -> {
        phase.setLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH);
        phase.setAcceptorConfig(null);
        phase.setForagerConfig(null);
        phase.setGuidedLocalSearchConfig(
            new GuidedLocalSearchConfig()
                .withSearchMode(GuidedLocalSearchSearchMode.SAMPLED)
                .withSampleSize(16)
                .withMaxPenaltyUpdatesPerStep(4)
                .withMaxUnproductiveRounds(4));
      }
      default -> throw new IllegalArgumentException("Unknown controller (" + name + ").");
    }
  }

  public static String state(Object solution) {
    if (solution instanceof BasicSolution basic) {
      new BasicWorkload().recompute(basic);
      return new BasicWorkload().state(basic);
    }
    if (solution instanceof ListSolution list) {
      new ListWorkload().recompute(list);
      return new ListWorkload().state(list);
    }
    if (solution instanceof ResumableSearchWorkloads.Timetable timetable) {
      new ResumableSearchWorkloads.TimetableWorkload().recompute(timetable);
      return new ResumableSearchWorkloads.TimetableWorkload().state(timetable);
    }
    throw new IllegalArgumentException("No replay for solution (" + solution.getClass() + ").");
  }

  public static String move(Move<?> move) {
    return move == null
        ? "none"
        : move.describe() + ":" + move.getPlanningEntities() + ":" + move.getPlanningValues();
  }

  public static final class BasicEasyScoreCalculator
      implements EasyScoreCalculator<BasicSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(BasicSolution solution) {
      return new BasicWorkload().recompute(solution);
    }
  }

  public static final class ListEasyScoreCalculator
      implements EasyScoreCalculator<ListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(ListSolution solution) {
      return new ListWorkload().recompute(solution);
    }
  }

  /** Delegation retains ordinary forager behavior while recording every consumed candidate. */
  public static final class RecordingForager extends AbstractLocalSearchForager<Object> {
    static final ConcurrentLinkedQueue<RecordingForager> INSTANCES = new ConcurrentLinkedQueue<>();
    final List<String> events = new ArrayList<>();
    private final LocalSearchForager<Object> delegate;
    private Thread owner;
    String source;
    int phasesStarted;
    int phasesEnded;
    int stepsEnded;

    public RecordingForager(LocalSearchForagerConfig config) {
      var delegateConfig = config.copyConfig();
      delegateConfig.setForagerClass(null);
      delegate = LocalSearchForagerFactory.<Object>create(delegateConfig).buildForager();
      INSTANCES.add(this);
    }

    private void owned() {
      if (owner == null) owner = Thread.currentThread();
      else if (owner != Thread.currentThread())
        throw new IllegalStateException("A live forager changed its owning thread.");
    }

    @Override
    public void solvingStarted(SolverScope<Object> scope) {
      owned();
      source = scope.getMetricSource();
      events.add("solvingStarted");
      delegate.solvingStarted(scope);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Object> scope) {
      owned();
      phasesStarted++;
      events.add("phaseStarted:" + scope.getPhaseIndex());
      delegate.phaseStarted(scope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<Object> scope) {
      owned();
      events.add("stepStarted:" + scope.getStepIndex());
      delegate.stepStarted(scope);
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return delegate.supportsNeverEndingMoveSelector();
    }

    @Override
    public void addMove(LocalSearchMoveScope<Object> scope) {
      owned();
      events.add(
          "candidate:"
              + scope.getStepScope().getStepIndex()
              + ":"
              + scope.getMoveIndex()
              + ":"
              + move(scope.getMove())
              + ":"
              + scope.getScore()
              + ":"
              + scope.getAccepted());
      delegate.addMove(scope);
    }

    @Override
    public boolean isQuitEarly() {
      return delegate.isQuitEarly();
    }

    @Override
    public LocalSearchMoveScope<Object> pickMove(LocalSearchStepScope<Object> scope) {
      var selected = delegate.pickMove(scope);
      events.add("pick:" + move(selected == null ? null : selected.getMove()));
      return selected;
    }

    @Override
    public void stepEnded(LocalSearchStepScope<Object> scope) {
      owned();
      stepsEnded++;
      events.add(
          "stepEnded:"
              + scope.getStepIndex()
              + ":"
              + scope.getScore()
              + ":"
              + scope.getSelectedMoveCount()
              + ":"
              + scope.getAcceptedMoveCount()
              + ":"
              + MoveThreadingWorkload.fingerprint(state(scope.getWorkingSolution())));
      delegate.stepEnded(scope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Object> scope) {
      owned();
      phasesEnded++;
      events.add("phaseEnded");
      delegate.phaseEnded(scope);
    }

    @Override
    public void solvingEnded(SolverScope<Object> scope) {
      owned();
      events.add("solvingEnded");
      delegate.solvingEnded(scope);
    }
  }
}
