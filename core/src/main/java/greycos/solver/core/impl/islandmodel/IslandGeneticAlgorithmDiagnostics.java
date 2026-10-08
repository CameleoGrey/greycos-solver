package greycos.solver.core.impl.islandmodel;

import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhase;

/** Final counters for one island GA phase; retains no working solution or solver scope. */
public record IslandGeneticAlgorithmDiagnostics(
    String source,
    int phaseIndex,
    long completedGenerations,
    DefaultGeneticAlgorithmPhase.MigrationDiagnostics migration) {}
