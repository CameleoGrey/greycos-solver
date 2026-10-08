package greycos.solver.core.impl.geneticalgorithm;

import java.io.IOException;
import java.nio.file.Path;

/** Runnable basic, list-only and mixed GA islands with strict export/reload and business replay. */
public final class GeneticAlgorithmIslandExample {
  private GeneticAlgorithmIslandExample() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 0 && args.length != 7 && args.length != 8) {
      throw new IllegalArgumentException(
          "Usage: [<basic|list_only|mixed> <tasks> <seed> <aggregateScoreCalls> <assignment.csv>"
              + " <islands> <migrationRate> [<localImprovementMoves>]]");
    }
    String model = args.length == 0 ? "basic" : args[0];
    int size = args.length == 0 ? 80 : Integer.parseInt(args[1]);
    long seed = args.length == 0 ? 37L : Long.parseLong(args[2]);
    long calls = args.length == 0 ? 20000L : Long.parseLong(args[3]);
    Path output =
        Path.of(args.length == 0 ? "target/genetic-algorithm/island-assignments.csv" : args[4]);
    int islands = args.length == 0 ? 2 : Integer.parseInt(args[5]);
    double rate = args.length == 0 ? 0.00001 : Double.parseDouble(args[6]);
    long probes = args.length == 8 ? Long.parseLong(args[7]) : 0L;
    var result =
        GeneticAlgorithmIslandBenchmark.search(
            model,
            size,
            seed,
            new GeneticAlgorithmIslandBenchmark.Variant(
                "GA_ISLAND_EXAMPLE", "GA", islands, true, rate),
            "score_calls",
            calls,
            probes,
            output);
    System.out.printf(
        "model=%s islands=%d migrationRate=%s initial=%s best=%s scoreCalls=%d"
            + " complete=true feasible=%s replay=PASS exportReload=PASS assignments=%s%n",
        model,
        islands,
        rate,
        result.initial(),
        result.score(),
        result.calls(),
        result.score().isFeasible(),
        output.toAbsolutePath());
  }
}
