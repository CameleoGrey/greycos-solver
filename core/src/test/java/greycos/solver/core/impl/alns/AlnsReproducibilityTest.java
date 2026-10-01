package greycos.solver.core.impl.alns;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload.MixedSolution;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload.MixedWorkload;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Full trajectories across adaptive portfolios, solver reuse and independent JVM processes. */
@Timeout(120)
class AlnsReproducibilityTest {
  private static final int TRIAL_COUNT = 24;

  @ParameterizedTest
  @EnumSource(Scenario.class)
  void adaptivePortfolioTraceMatchesAcrossWorkersAndRepeatedSolves(Scenario scenario) {
    var sequential = solve(scenario, "NONE", 2);
    var expected = sequential.getFirst();
    assertThat(sequential).containsOnly(expected);
    for (var workers : List.of("2", "4")) {
      assertThat(solve(scenario, workers, 2))
          .as("%s workers=%s, including reuse of the same solver", scenario, workers)
          .containsOnly(expected);
    }
  }

  @Test
  @Timeout(180)
  void adaptivePortfolioTraceMatchesAcrossFreshJvms(@TempDir Path temporaryDirectory)
      throws Exception {
    var expected = processTrace();
    for (int fork = 0; fork < 2; fork++) {
      var output = temporaryDirectory.resolve("alns-" + fork + ".log");
      var trace = temporaryDirectory.resolve("alns-" + fork + ".trace");
      var javaExecutable =
          System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
      var process =
          new ProcessBuilder(
                  Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                  "-cp",
                  System.getProperty(
                      "surefire.test.class.path", System.getProperty("java.class.path")),
                  ProcessProbe.class.getName(),
                  trace.toString())
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      try {
        assertThat(process.waitFor(60, TimeUnit.SECONDS))
            .as("ALNS child JVM %s must finish; output: %s", fork, Files.readString(output))
            .isTrue();
        assertThat(process.exitValue()).as(Files.readString(output)).isZero();
        assertThat(Files.readString(trace)).as("ALNS child JVM %s", fork).isEqualTo(expected);
      } finally {
        process.destroyForcibly();
        assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("ALNS child JVM cleanup").isTrue();
      }
    }
  }

  private static String processTrace() {
    var result = new StringBuilder();
    for (var scenario : Scenario.values()) {
      var expected = solve(scenario, "NONE", 1).getFirst();
      var parallel = solve(scenario, "2", 1).getFirst();
      assertThat(parallel).as("%s fresh-process worker parity", scenario).isEqualTo(expected);
      result.append(scenario).append('\n');
      for (var trial : expected.trace()) {
        result.append(trial).append('\n');
      }
      result
          .append("best:")
          .append(expected.score())
          .append(':')
          .append(expected.state())
          .append('\n');
    }
    return result.toString();
  }

  private static List<Run> solve(Scenario scenario, String workers, int repetitions) {
    var workload = new MixedWorkload();
    var solver =
        (DefaultSolver<MixedSolution>)
            SolverFactory.<MixedSolution>create(config(workload, scenario, workers)).buildSolver();
    var trace = new ArrayList<String>();
    var selectedPairs = new ArrayList<AlnsOperatorPair>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<MixedSolution> step) {
            var trial = ((AlnsStepScope<?>) step).getTrialResult();
            assertThat(workload.recompute(step.getWorkingSolution())).isEqualTo(trial.afterScore());
            assertThat(trial.trialIndex()).isEqualTo(trace.size());
            selectedPairs.add(new AlnsOperatorPair(trial.destroyId(), trial.repairId()));
            // Durations and speculative worker calculations deliberately do not enter this trace.
            trace.add(
                trial.trialIndex()
                    + ":"
                    + trial.destroyId()
                    + ":"
                    + trial.repairId()
                    + ":"
                    + trial.outcome()
                    + ":"
                    + trial.beforeScore()
                    + ":"
                    + trial.candidateScore()
                    + ":"
                    + trial.afterScore()
                    + ":"
                    + trial.bestBeforeScore()
                    + ":"
                    + trial.bestAfterScore()
                    + ":"
                    + trial.destroyedCount()
                    + ":"
                    + trial.recoveryCount()
                    + ":"
                    + trial.probeCount()
                    + ":"
                    + step.getScoreDirector().getCalculationCount()
                    + ":"
                    + workload.state(step.getWorkingSolution()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<MixedSolution> phaseScope) {
            assertThat(trace).hasSize(TRIAL_COUNT);
            assertThat(workload.recompute(phaseScope.getWorkingSolution()))
                .isEqualTo(workload.score(phaseScope.getWorkingSolution()));
            trace.add(
                "ended:"
                    + phaseScope.getScoreDirector().getCalculationCount()
                    + ":"
                    + workload.state(phaseScope.getWorkingSolution()));
          }
        });
    var runs = new ArrayList<Run>(repetitions);
    for (int repetition = 0; repetition < repetitions; repetition++) {
      trace.clear();
      selectedPairs.clear();
      var solution = solver.solve(workload.createProblem(24));
      assertThat(trace).hasSize(TRIAL_COUNT + 1);
      if (scenario.selection == AlnsSelectionPolicyType.UCB) {
        // All three destroys and both repairs remain eligible throughout this initialized fixture.
        assertThat(selectedPairs.subList(0, 6)).doesNotHaveDuplicates();
      }
      assertThat(workload.recompute(solution)).isEqualTo(workload.score(solution));
      runs.add(new Run(List.copyOf(trace), workload.state(solution), workload.score(solution)));
    }
    return List.copyOf(runs);
  }

  private static SolverConfig config(MixedWorkload workload, Scenario scenario, String workers) {
    var config =
        AlnsMoveThreadingWorkload.config(
                workload,
                workers,
                19L,
                3,
                AlnsRepairOperatorType.RANDOMIZED_GREEDY,
                new TerminationConfig().withStepCountLimit(TRIAL_COUNT),
                EnvironmentMode.NO_ASSERT)
            .withMoveThreadBufferSize(1);
    var phase = (AlnsPhaseConfig) config.getPhaseConfigList().getFirst();
    phase
        .withMoveThreadingMode(scenario.mode)
        .withSelectionPolicyType(scenario.selection)
        .withAcceptanceType(scenario.acceptance)
        .withSegmentLength(4)
        .withReactionFactor(0.5)
        .withUcbExploration(1.0)
        .withLateAcceptanceSize(4)
        .withDestroyOperators(
            destroy("random", AlnsDestroyOperatorType.RANDOM),
            destroy("worst", AlnsDestroyOperatorType.WORST_REMOVAL),
            destroy("group", AlnsDestroyOperatorType.GROUP_REMOVAL));
    var randomized =
        new AlnsRepairOperatorConfig()
            .withId("randomized-3")
            .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
            .withTopK(3);
    if (scenario.mode == AlnsMoveThreadingMode.REPAIR_ATTEMPTS) {
      phase
          .withRepairAttemptCount(4)
          .withRepairOperators(
              new AlnsRepairOperatorConfig()
                  .withId("randomized-1")
                  .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
                  .withTopK(1),
              randomized);
    } else {
      phase.withRepairOperators(
          randomized,
          new AlnsRepairOperatorConfig()
              .withId("regret-2")
              .withType(AlnsRepairOperatorType.REGRET_2));
    }
    if (scenario.acceptance == AlnsAcceptanceType.SIMULATED_ANNEALING) {
      phase.withStartingTemperature("100000000").withCoolingRate(0.95);
    }
    return config;
  }

  private static AlnsDestroyOperatorConfig destroy(String id, AlnsDestroyOperatorType type) {
    return new AlnsDestroyOperatorConfig()
        .withId(id)
        .withType(type)
        .withMinimumDestroyedCount(3)
        .withMaximumDestroyedCount(3);
  }

  private record Run(List<String> trace, String state, SimpleScore score) {}

  private enum Scenario {
    PROBES_ROULETTE_LATE_ACCEPTANCE(
        AlnsMoveThreadingMode.PROBES,
        AlnsSelectionPolicyType.SEGMENTED_ROULETTE,
        AlnsAcceptanceType.LATE_ACCEPTANCE),
    PROBES_UCB_ANNEALING(
        AlnsMoveThreadingMode.PROBES,
        AlnsSelectionPolicyType.UCB,
        AlnsAcceptanceType.SIMULATED_ANNEALING),
    ATTEMPTS_ROULETTE_LATE_ACCEPTANCE(
        AlnsMoveThreadingMode.REPAIR_ATTEMPTS,
        AlnsSelectionPolicyType.SEGMENTED_ROULETTE,
        AlnsAcceptanceType.LATE_ACCEPTANCE),
    ATTEMPTS_UCB_ANNEALING(
        AlnsMoveThreadingMode.REPAIR_ATTEMPTS,
        AlnsSelectionPolicyType.UCB,
        AlnsAcceptanceType.SIMULATED_ANNEALING);

    private final AlnsMoveThreadingMode mode;
    private final AlnsSelectionPolicyType selection;
    private final AlnsAcceptanceType acceptance;

    Scenario(
        AlnsMoveThreadingMode mode,
        AlnsSelectionPolicyType selection,
        AlnsAcceptanceType acceptance) {
      this.mode = mode;
      this.selection = selection;
      this.acceptance = acceptance;
    }
  }

  public static final class ProcessProbe {
    public static void main(String[] args) throws Exception {
      Files.writeString(Path.of(args[0]), processTrace());
    }
  }
}
