package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.decider.ConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.MultiThreadedConstructionHeuristicDecider;
import greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Machine;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Visit;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhaseFactory;
import greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PhaseMoveThreadingTest {

  private static final int STEP_LIMIT = 12;

  @ParameterizedTest
  @CsvSource(
      value = {"null,null,null", "null,2,2", "2,null,2", "2,NONE,null", "2,4,4"},
      nullValues = "null")
  void constructionHeuristicUsesEffectiveMoveThreadCount(
      Integer solverThreads, String phaseThreads, Integer expectedThreads) {
    var solverPolicy = policy(solverThreads);
    var config = new ConstructionHeuristicPhaseConfig();
    config.setMoveThreadCount(phaseThreads);
    var factory =
        new DefaultConstructionHeuristicPhaseFactory<TestdataSolution>(config) {
          @Override
          protected ConstructionHeuristicForager<TestdataSolution> buildForager(
              HeuristicConfigPolicy<TestdataSolution> phasePolicy) {
            assertThat(phasePolicy.getMoveThreadCount()).isEqualTo(expectedThreads);
            return super.buildForager(phasePolicy);
          }
        };
    var phase =
        factory.getBuilder(0, true, solverPolicy, new BasicPlumbingTermination<>(false)).build();
    assertThat(phase)
        .extracting("decider")
        .satisfies(
            decider -> {
              if (expectedThreads == null) {
                assertThat(decider).isExactlyInstanceOf(ConstructionHeuristicDecider.class);
              } else {
                assertThat(decider).isInstanceOf(MultiThreadedConstructionHeuristicDecider.class);
                assertThat(
                        ((MultiThreadedConstructionHeuristicDecider<?>) decider)
                            .getMoveThreadCount())
                    .isEqualTo(expectedThreads);
              }
            });
    assertThat(solverPolicy.getMoveThreadCount()).isEqualTo(solverThreads);
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,null,null", "null,2,2", "2,null,2", "2,NONE,null", "2,4,4"},
      nullValues = "null")
  void localSearchUsesEffectiveMoveThreadCount(
      Integer solverThreads, String phaseThreads, Integer expectedThreads) {
    var solverPolicy = policy(solverThreads);
    var config = new LocalSearchPhaseConfig();
    config.setMoveThreadCount(phaseThreads);
    var factory =
        new DefaultLocalSearchPhaseFactory<TestdataSolution>(config) {
          @Override
          protected Acceptor<TestdataSolution> buildAcceptor(
              HeuristicConfigPolicy<TestdataSolution> phasePolicy, boolean neighborhoodsEnabled) {
            assertThat(phasePolicy.getMoveThreadCount()).isEqualTo(expectedThreads);
            return super.buildAcceptor(phasePolicy, neighborhoodsEnabled);
          }
        };
    var phase =
        factory.buildPhase(0, false, solverPolicy, null, new BasicPlumbingTermination<>(false));
    assertThat(phase)
        .extracting("decider")
        .satisfies(
            decider -> {
              if (expectedThreads == null) {
                assertThat(decider).isExactlyInstanceOf(LocalSearchDecider.class);
              } else {
                assertThat(decider).isInstanceOf(MultiThreadedLocalSearchDecider.class);
                assertThat(((MultiThreadedLocalSearchDecider<?>) decider).getMoveThreadCount())
                    .isEqualTo(expectedThreads);
              }
            });
    assertThat(solverPolicy.getMoveThreadCount()).isEqualTo(solverThreads);
  }

  private static HeuristicConfigPolicy<TestdataSolution> policy(Integer moveThreadCount) {
    return new HeuristicConfigPolicy.Builder<TestdataSolution>()
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withSolutionDescriptor(TestdataSolution.buildSolutionDescriptor())
        .withMoveThreadCount(moveThreadCount)
        .withInitializingScoreTrend(
            new InitializingScoreTrend(
                new InitializingScoreTrendLevel[] {InitializingScoreTrendLevel.ANY}))
        .build();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "basic,null,2", "basic,2,null", "basic,2,NONE", "basic,2,4",
        "list,null,2", "list,2,null", "list,2,NONE", "list,2,4"
      },
      nullValues = "null")
  @Timeout(60)
  void ruinRecreatePreservesIdentitiesScoresAndRepeatability(
      String workloadName, String solverThreads, String phaseThreads) {
    var workload = MoveThreadingWorkload.named(workloadName);
    assertThat(solve(workload, workloadName, solverThreads, phaseThreads))
        .isEqualTo(solve(workload, workloadName, solverThreads, phaseThreads));
  }

  @ParameterizedTest
  @CsvSource({
    "basic,easy,NONE", "basic,easy,2", "basic,incremental,NONE", "basic,incremental,2",
    "list,easy,NONE", "list,easy,2", "list,incremental,NONE", "list,incremental,2"
  })
  @Timeout(60)
  void ruinRecreateSupportsEasyAndIncrementalScoring(
      String workloadName, String scoring, String threads) {
    var workload = MoveThreadingWorkload.named(workloadName);
    assertThat(solve(workload, workloadName, threads, null, scoring))
        .isEqualTo(solve(workload, workloadName, threads, null, scoring));
  }

  private <Solution_> List<String> solve(
      Workload<Solution_> workload,
      String workloadName,
      String solverThreads,
      String phaseThreads) {
    return solve(workload, workloadName, solverThreads, phaseThreads, "streams");
  }

  private <Solution_> List<String> solve(
      Workload<Solution_> workload,
      String workloadName,
      String solverThreads,
      String phaseThreads,
      String scoring) {
    var config =
        workload.solverConfig(
            solverThreads,
            37L,
            4,
            new TerminationConfig().withStepCountLimit(STEP_LIMIT),
            EnvironmentMode.FULL_ASSERT);
    if (scoring.equals("easy")) {
      config.withScoreDirectorFactory(
          new ScoreDirectorFactoryConfig()
              .withEasyScoreCalculatorClass(
                  workloadName.equals("basic")
                      ? BasicEasyScoreCalculator.class
                      : ListEasyScoreCalculator.class));
    } else if (scoring.equals("incremental")) {
      config.withScoreDirectorFactory(
          new ScoreDirectorFactoryConfig()
              .withIncrementalScoreCalculatorClass(
                  workloadName.equals("basic")
                      ? BasicIncrementalScoreCalculator.class
                      : ListIncrementalScoreCalculator.class));
    }
    var phase = (LocalSearchPhaseConfig) config.getPhaseConfigList().get(0);
    phase.setMoveThreadCount(phaseThreads);
    phase.setMoveSelectorConfig(
        workloadName.equals("list")
            ? new ListRuinRecreateMoveSelectorConfig()
                .withMinimumRuinedCount(2)
                .withMaximumRuinedCount(3)
            : new RuinRecreateMoveSelectorConfig()
                .withMinimumRuinedCount(2)
                .withMaximumRuinedCount(3));
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    List<String> trace = new ArrayList<>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            if (stepScope instanceof LocalSearchStepScope<Solution_> localStep) {
              var independentlyCalculated = workload.recompute(stepScope.getWorkingSolution());
              assertThat(stepScope.getScore().unassignedCount()).isZero();
              assertThat(stepScope.getScore().raw()).isEqualTo(independentlyCalculated);
              trace.add(
                  localStep.getStepIndex()
                      + ":"
                      + independentlyCalculated
                      + ":"
                      + localStep.getSelectedMoveCount()
                      + ":"
                      + localStep.getAcceptedMoveCount()
                      + ":"
                      + workload.state(stepScope.getWorkingSolution()));
            }
          }
        });
    var solution = solver.solve(workload.createProblem(12));
    assertThat(trace).hasSize(STEP_LIMIT);
    assertThat(workload.score(solution)).isEqualTo(workload.recompute(solution));
    trace.add(workload.score(solution) + ":" + workload.state(solution));
    return trace;
  }

  public static final class BasicEasyScoreCalculator
      implements EasyScoreCalculator<BasicSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(BasicSolution solution) {
      var loads = new long[solution.getMachines().size()];
      long penalty = 0;
      for (var job : solution.getJobs()) {
        if (job.getMachine() != null) {
          loads[job.getMachine().id()] += job.getUnits();
          penalty += assignmentPenalty(job);
        }
      }
      for (var load : loads) {
        penalty += load * load;
      }
      return SimpleScore.of(-penalty);
    }
  }

  public static final class BasicIncrementalScoreCalculator
      implements IncrementalScoreCalculator<BasicSolution, SimpleScore> {
    private final Map<Machine, Long> loads = new IdentityHashMap<>();
    private long penalty;

    @Override
    public void resetWorkingSolution(BasicSolution solution) {
      loads.clear();
      penalty = 0;
      solution.getJobs().forEach(this::insert);
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      if (entity instanceof Job job && variableName.equals("machine") && job.getMachine() != null) {
        var load = loads.get(job.getMachine());
        var remainingLoad = load - job.getUnits();
        penalty -= assignmentPenalty(job) + load * load - remainingLoad * remainingLoad;
        loads.put(job.getMachine(), remainingLoad);
      }
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      if (entity instanceof Job job && variableName.equals("machine")) {
        insert(job);
      }
    }

    private void insert(Job job) {
      if (job.getMachine() != null) {
        var oldLoad = loads.getOrDefault(job.getMachine(), 0L);
        var newLoad = oldLoad + job.getUnits();
        penalty += assignmentPenalty(job) + newLoad * newLoad - oldLoad * oldLoad;
        loads.put(job.getMachine(), newLoad);
      }
    }

    @Override
    public SimpleScore calculateScore() {
      return SimpleScore.of(-penalty);
    }
  }

  private static long assignmentPenalty(Job job) {
    return Math.abs((long) job.getMachine().id() - job.getPreferredMachine()) * job.getUnits();
  }

  public static final class ListEasyScoreCalculator
      implements EasyScoreCalculator<ListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(ListSolution solution) {
      return SimpleScore.of(
          -solution.getVisits().stream().mapToLong(PhaseMoveThreadingTest::visitPenalty).sum());
    }
  }

  public static final class ListIncrementalScoreCalculator
      implements IncrementalScoreCalculator<ListSolution, SimpleScore> {
    private long penalty;

    @Override
    public void resetWorkingSolution(ListSolution solution) {
      penalty = solution.getVisits().stream().mapToLong(PhaseMoveThreadingTest::visitPenalty).sum();
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {
      if (entity instanceof Visit visit && variableName.equals("completion")) {
        penalty -= visitPenalty(visit);
      }
    }

    @Override
    public void afterVariableChanged(Object entity, String variableName) {
      if (entity instanceof Visit visit && variableName.equals("completion")) {
        penalty += visitPenalty(visit);
      }
    }

    @Override
    public SimpleScore calculateScore() {
      return SimpleScore.of(-penalty);
    }
  }

  private static long visitPenalty(Visit visit) {
    if (visit.getCompletion() == null) {
      return 0;
    }
    // The workload assigns each visit a due time of 100 + id % 300.
    long late = Math.max(0L, visit.getCompletion() - (100 + visit.getId() % 300));
    return visit.getCompletion() + late * late;
  }
}
