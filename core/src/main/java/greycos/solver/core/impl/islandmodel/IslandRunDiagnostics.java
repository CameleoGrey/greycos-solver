package greycos.solver.core.impl.islandmodel;

import java.util.List;

/** Final local work for one island occurrence. A negative session count means uninstrumented. */
public record IslandRunDiagnostics(
    int islandId,
    String source,
    long physicalScoreCalculationCount,
    long moveEvaluationCount,
    long bavetSessionCount,
    List<IslandGeneticAlgorithmDiagnostics> geneticAlgorithmPhases) {
  public IslandRunDiagnostics {
    geneticAlgorithmPhases = List.copyOf(geneticAlgorithmPhases);
  }
}
