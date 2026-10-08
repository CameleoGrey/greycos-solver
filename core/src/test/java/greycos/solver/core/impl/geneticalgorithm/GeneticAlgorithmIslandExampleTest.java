package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmIslandExampleTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list_only", "mixed"})
  void enabledAndDisabledIslandsReplayAndReload(String model) throws IOException {
    for (double rate : new double[] {0.0, 0.00001}) {
      Path output = directory.resolve(model + "-" + rate + ".csv");
      var result =
          GeneticAlgorithmIslandBenchmark.search(
              model,
              16,
              37,
              new GeneticAlgorithmIslandBenchmark.Variant("test", "GA", 2, true, rate),
              "score_calls",
              200,
              0,
              output);
      assertThat(result.score()).isGreaterThanOrEqualTo(result.initial());
      assertThat(result.calls()).isEqualTo(result.rootCalls() + result.islandCalls());
      assertThat(result.islandCalls()).isPositive();
      assertThat(result.fingerprint()).hasSize(64);
      assertThat(Files.readAllLines(output)).hasSize(model.equals("basic") ? 17 : 18);
    }
  }

  @Test
  void budgetAllocationAndMigrationDefaultsAreExplicit() {
    var variant = new GeneticAlgorithmIslandBenchmark.Variant("test", "GA", 4, true, 0.00001);
    var config =
        GeneticAlgorithmIslandBenchmark.config("mixed", 11, variant, "score_calls", 20003, 0);
    var island = (IslandModelPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(island.getIslandCount()).isEqualTo(4);
    assertThat(island.getMigrationFrequency()).isEqualTo(10);
    assertThat(island.getCompareGlobalEnabled()).isFalse();
    assertThat(island.getMoveThreadCount()).isEqualTo("NONE");
    assertThat(island.getTerminationConfig().getScoreCalculationCountLimit()).isEqualTo(5000);
    var ga = (GeneticAlgorithmPhaseConfig) island.getPhaseConfigList().getFirst();
    assertThat(ga.getMigrationRate()).isEqualTo(0.00001);
    assertThat(ga.getLocalImprovementMoveCountLimit()).isZero();
    assertThat(GeneticAlgorithmIslandBenchmark.variants()).hasSize(12);
  }

  @Test
  void rejectsInsufficientPerIslandQuotaBeforeSolve() {
    var variant = new GeneticAlgorithmIslandBenchmark.Variant("test", "GA", 4, true, 0);
    assertThatThrownBy(
            () -> GeneticAlgorithmIslandBenchmark.config("basic", 11, variant, "score_calls", 7, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("budget");
  }

  @Test
  void validatesFiniteMigrationRate() {
    assertThatThrownBy(
            () -> new GeneticAlgorithmIslandBenchmark.Variant("test", "GA", 2, true, Double.NaN))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
