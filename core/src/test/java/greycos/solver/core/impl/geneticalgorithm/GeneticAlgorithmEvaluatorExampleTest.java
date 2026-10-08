package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmEvaluatorExampleTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list_only", "mixed"})
  void everyPoolSizeReplaysAndReloads(String model) throws IOException {
    for (int workers : new int[] {1, 2, 4}) {
      Path output = directory.resolve(model + "-" + workers + ".csv");
      var result =
          GeneticAlgorithmEvaluatorBenchmark.search(
              model,
              16,
              37,
              new GeneticAlgorithmEvaluatorBenchmark.Variant("test", "GA", 1, false, 0, workers),
              "score_calls",
              200,
              0,
              output);
      assertThat(result.score()).isGreaterThanOrEqualTo(result.initial());
      assertThat(result.calls()).isEqualTo(result.rootCalls());
      assertThat(result.pool().calls()).isPositive().isLessThanOrEqualTo(result.calls());
      assertThat(result.pool().sessions()).isEqualTo(workers);
      assertThat(result.fingerprint()).hasSize(64);
      assertThat(Files.readAllLines(output)).hasSize(model.equals("basic") ? 17 : 18);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list_only", "mixed"})
  void seedingBudgetCanFinishBeforeConfiguredPoolStarts(String model) throws IOException {
    var result =
        GeneticAlgorithmEvaluatorBenchmark.search(
            model,
            16,
            37,
            new GeneticAlgorithmEvaluatorBenchmark.Variant("test", "GA", 1, false, 0, 4),
            "score_calls",
            2,
            0,
            directory.resolve(model + "-never-started.csv"));
    assertThat(result.score()).isGreaterThanOrEqualTo(result.initial());
    assertThat(result.pool().calls()).isZero();
    assertThat(result.pool().sessions()).isZero();
    assertThat(result.pool().setupNanos()).isZero();
    assertThat(result.pool().submitted()).isZero();
    assertThat(result.pool().workerCalls()).isEmpty();
    assertThat(result.coordinatorSessions()).isPositive();
  }

  @Test
  void newExampleArgumentsAreExplicitAndResourceBaselineHasCoordinator() {
    var variant = new GeneticAlgorithmEvaluatorBenchmark.Variant("test", "GA", 1, false, 0, 4);
    var config =
        GeneticAlgorithmEvaluatorBenchmark.config("mixed", 11, variant, "score_calls", 20003, 0);
    var ga = (GeneticAlgorithmPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(ga.getEvaluatorThreadCount()).isEqualTo(4);
    assertThat(ga.getLocalImprovementMoveCountLimit()).isZero();
    assertThat(ga.getTerminationConfig().getScoreCalculationCountLimit()).isEqualTo(20003);
    assertThat(GeneticAlgorithmEvaluatorBenchmark.variants()).hasSize(12);
    assertThatThrownBy(() -> GeneticAlgorithmEvaluatorExample.main(new String[] {"basic"}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Usage");
    assertThatThrownBy(
            () -> new GeneticAlgorithmEvaluatorBenchmark.Variant("test", "GA", 2, true, 0, 2))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
