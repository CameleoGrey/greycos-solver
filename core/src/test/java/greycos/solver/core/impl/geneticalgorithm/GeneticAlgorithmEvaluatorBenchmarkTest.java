package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmEvaluatorBenchmarkTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list_only", "mixed"})
  void fixedCandidatesHaveIdenticalScoresAcrossReplacementRetainedAndPool(String model)
      throws IOException {
    for (String shape : new String[] {"sparse", "broad"}) {
      var candidates = GeneticAlgorithmEvaluatorBenchmark.candidates(model, 16, 12, 37, shape);
      var baseline =
          GeneticAlgorithmEvaluatorBenchmark.evaluate(
              model, 16, candidates, "full_replacement", true, null);
      assertThat(baseline.sessions()).isEqualTo(13);
      for (String algorithm : new String[] {"retained_delta", "pool_1", "pool_2", "pool_4"}) {
        var result =
            GeneticAlgorithmEvaluatorBenchmark.evaluate(
                model,
                16,
                candidates,
                algorithm,
                true,
                directory.resolve(model + "-" + shape + "-" + algorithm + ".csv"));
        assertThat(result.score()).isEqualTo(baseline.score());
        assertThat(result.scoreDigest()).isEqualTo(baseline.scoreDigest());
        assertThat(result.fingerprint()).isEqualTo(baseline.fingerprint());
        assertThat(result.candidateDigest()).isEqualTo(baseline.candidateDigest());
        assertThat(result.sessions()).isEqualTo(result.workers() + 1L);
        assertThat(result.setupCalls()).isEqualTo(result.workers() + 1L);
      }
    }
  }
}
