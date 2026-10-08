package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.nio.file.Path;

/** Runnable basic, list-only and mixed GA evaluator pool with strict export/reload and replay. */
public final class GeneticAlgorithmEvaluatorExample {
  private GeneticAlgorithmEvaluatorExample() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 6) {
      throw new IllegalArgumentException(
          "Usage: [<basic|list_only|mixed> <tasks> <seed> <physicalScoreCalls> <assignment.csv> <evaluatorThreads>]");
    }
    String model = args.length == 0 ? "basic" : args[0];
    int size = args.length == 0 ? 80 : Integer.parseInt(args[1]);
    long seed = args.length == 0 ? 37L : Long.parseLong(args[2]);
    long calls = args.length == 0 ? 20000L : Long.parseLong(args[3]);
    Path output =
        Path.of(args.length == 0 ? "target/genetic-algorithm/evaluator-assignments.csv" : args[4]);
    int workers = args.length == 0 ? 2 : Integer.parseInt(args[5]);
    var result =
        GeneticAlgorithmEvaluatorBenchmark.search(
            model,
            size,
            seed,
            new GeneticAlgorithmEvaluatorBenchmark.Variant(
                "GA_POOL_EXAMPLE", "GA", 1, false, 0, workers),
            "score_calls",
            calls,
            0,
            output);
    System.out.printf(
        "model=%s evaluatorThreads=%d initial=%s best=%s scoreCalls=%d complete=true feasible=%s replay=PASS exportReload=PASS assignments=%s%n",
        model,
        workers,
        result.initial(),
        result.score(),
        result.calls(),
        result.score().isFeasible(),
        output.toAbsolutePath());
  }
}
