package greycos.solver.math.benchmark;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.OptimizationDirection;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandDecision;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandSolution;
import greycos.solver.math.benchmark.MathQualificationSupport.AllocationMeter;
import greycos.solver.math.benchmark.MathQualificationSupport.Snapshot;
import greycos.solver.math.examples.AssignmentExample;
import greycos.solver.math.examples.KnapsackExample;
import greycos.solver.math.examples.ResourceAllocationExample;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;
import greycos.solver.math.impl.model.MathCompiler;
import greycos.solver.math.impl.score.MathConstraintProvider;
import greycos.solver.math.impl.search.MathChangeMoveIteratorFactory;

/** Manual paired-seed, fixed-move-budget qualification; no timing or quality pass threshold. */
public final class MathSolveQualification {
  private MathSolveQualification() {}

  public static void main(String[] args) {
    var options =
        MathQualificationSupport.arguments(
            args,
            Map.of(
                "moves",
                "1000",
                "warmups",
                "1",
                "repetitions",
                "3",
                "seeds",
                "0,1,2",
                "variant",
                "all",
                "example",
                "all",
                "acceptor",
                "late",
                "start",
                "declared"));
    long moves = Long.parseLong(options.get("moves"));
    int warmups = Integer.parseInt(options.get("warmups"));
    int repetitions = Integer.parseInt(options.get("repetitions"));
    if (moves < 1
        || warmups < 0
        || repetitions < 1
        || !List.of("late", "hill").contains(options.get("acceptor"))
        || !List.of("declared", "upper").contains(options.get("start"))) {
      throw new IllegalArgumentException(
          "Require moves >= 1, warmups >= 0, repetitions >= 1, acceptor=late|hill, start=declared|upper.");
    }
    var names = List.of("knapsack", "resource", "assignment");
    var selected = options.get("example");
    if (!selected.equals("all") && !names.contains(selected)) {
      throw new IllegalArgumentException("Unknown example " + selected);
    }
    var fixtures =
        (selected.equals("all") ? names : List.of(selected))
            .stream()
                .map(
                    name ->
                        new Fixture(
                            name,
                            switch (name) {
                              case "knapsack" -> KnapsackExample.model();
                              case "resource" -> ResourceAllocationExample.model();
                              case "assignment" -> AssignmentExample.model();
                              default -> throw new IllegalArgumentException(name);
                            }))
                .toList();
    var variants = MathScoringBenchmark.variants(options.get("variant"));
    var allocation = new AllocationMeter();
    boolean headerWritten = false;
    for (var fixture : fixtures) {
      var optimum = enumerateOptimum(fixture.model());
      for (var seedText : options.get("seeds").split(",")) {
        long seed = Long.parseLong(seedText);
        for (int repetition = -warmups; repetition < repetitions; repetition++) {
          for (int offset = 0; offset < variants.size(); offset++) {
            var variant = variants.get(Math.floorMod(offset + repetition, variants.size()));
            var measurement =
                run(
                    fixture.model(),
                    variant,
                    moves,
                    seed,
                    options.get("acceptor"),
                    options.get("start"),
                    allocation);
            if (repetition < 0) {
              continue;
            }
            if (!headerWritten) {
              System.out.println(
                  "example,variant,seed,repetition,acceptor,start,move_budget,move_evaluations,score_calculations,"
                      + "initialization_ns,solve_ns,first_feasible_millis,feasible,hard,objective,enumerated_optimum,objective_gap,"
                      + "solve_allocated_bytes,solve_gc_count,solve_gc_millis,assignment,replay");
              headerWritten = true;
            }
            var objective =
                MathQualificationSupport.evaluate(
                    fixture.model().objective(), measurement.values());
            var gap =
                fixture.model().objectiveDirection() == OptimizationDirection.MINIMIZE
                    ? objective.subtract(optimum)
                    : optimum.subtract(objective);
            System.out.printf(
                Locale.ROOT,
                "%s,%s,%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%s,%d,%s,%s,%s,%d,%d,%d,%s,true%n",
                fixture.name(),
                variant,
                seed,
                repetition,
                options.get("acceptor"),
                options.get("start"),
                moves,
                measurement.moves(),
                measurement.calculations(),
                measurement.initializationNanos(),
                measurement.afterSolve().nanos() - measurement.beforeSolve().nanos(),
                measurement.firstFeasibleMillis(),
                measurement.score().isFeasible(),
                measurement.score().hardScore(),
                objective,
                optimum,
                measurement.score().isFeasible() ? gap.toString() : "NA",
                measurement.afterSolve().allocatedSince(measurement.beforeSolve()),
                measurement.afterSolve().collections() - measurement.beforeSolve().collections(),
                measurement.afterSolve().gcMillis() - measurement.beforeSolve().gcMillis(),
                Arrays.stream(measurement.values())
                    .mapToObj(Long::toString)
                    .collect(Collectors.joining(";")));
          }
        }
      }
    }
  }

  static Measurement run(
      MathModel model,
      String variant,
      long moves,
      long seed,
      String acceptor,
      String start,
      AllocationMeter allocation) {
    long initializationStart = System.nanoTime();
    var initial = MathSolution.initialize(MathCompiler.compile(model), seed);
    if (start.equals("upper")) {
      for (var decision : initial.getDecisions()) {
        if (!decision.isPinned()) {
          decision.setValue(model.variables().get(decision.getId()).upperBound());
        }
      }
    }
    var initialValues = values(initial);
    var initialScore = MathQualificationSupport.replay(model, initialValues);
    if (variant.equals("generic")) {
      var config =
          config(MathSolution.class, MathDecision.class, moves, seed, acceptor)
              .withConstraintProviderClass(MathConstraintProvider.class);
      var solver = SolverFactory.<MathSolution>create(config).buildSolver();
      return solve(
          model,
          solver,
          initial,
          MathSolveQualification::values,
          MathSolution::getScore,
          initialScore,
          initializationStart,
          allocation);
    }
    var hand = HandwrittenMathBaseline.initialize(model, initialValues);
    var config = config(HandSolution.class, HandDecision.class, moves, seed, acceptor);
    switch (variant) {
      case "handwritten" ->
          config.withConstraintProviderClass(HandwrittenMathBaseline.Provider.class);
      case "full" ->
          config.withEasyScoreCalculatorClass(HandwrittenMathBaseline.FullReevaluation.class);
      default -> throw new IllegalArgumentException("Unknown variant " + variant);
    }
    var solver = SolverFactory.<HandSolution>create(config).buildSolver();
    return solve(
        model,
        solver,
        hand,
        HandSolution::values,
        HandSolution::getScore,
        initialScore,
        initializationStart,
        allocation);
  }

  private static SolverConfig config(
      Class<?> solution, Class<?> entity, long moves, long seed, String acceptor) {
    var acceptorConfig =
        acceptor.equals("hill")
            ? new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING))
            : new LocalSearchAcceptorConfig().withLateAcceptanceSize(400);
    return new SolverConfig()
        .withSolutionClass(solution)
        .withEntityClasses(entity)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(seed)
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
        .withTerminationConfig(new TerminationConfig().withMoveCountLimit(moves))
        .withPhases(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(
                    new MoveIteratorFactoryConfig()
                        .withMoveIteratorFactoryClass(
                            entity == MathDecision.class
                                ? MathChangeMoveIteratorFactory.class
                                : HandwrittenChangeMoveIteratorFactory.class)
                        .withSelectionOrder(SelectionOrder.RANDOM))
                .withAcceptorConfig(acceptorConfig)
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1)));
  }

  private static <S> Measurement solve(
      MathModel model,
      Solver<S> solver,
      S initial,
      Function<S, long[]> values,
      Function<S, HardSoftScore> score,
      HardSoftScore initialScore,
      long initializationStart,
      AllocationMeter allocation) {
    long[] firstFeasibleMillis = {initialScore.isFeasible() ? 0 : -1};
    solver.addEventListener(
        event -> {
          if (firstFeasibleMillis[0] < 0
              && event.isNewBestSolutionInitialized()
              && event.getNewBestScore().isFeasible()) {
            var replayed =
                MathQualificationSupport.replay(model, values.apply(event.getNewBestSolution()));
            if (!replayed.equals(event.getNewBestScore())) {
              throw new IllegalStateException("First feasible event failed independent replay.");
            }
            firstFeasibleMillis[0] = event.getTimeMillisSpent();
          }
        });
    long initializationNanos = System.nanoTime() - initializationStart;
    var before = Snapshot.capture(allocation);
    var result = solver.solve(initial);
    var after = Snapshot.capture(allocation);
    var assignment = values.apply(result);
    var replayed = MathQualificationSupport.replay(model, assignment);
    if (!replayed.equals(score.apply(result))) {
      throw new IllegalStateException("Full solve result failed independent replay.");
    }
    var defaultSolver = (DefaultSolver<S>) solver;
    return new Measurement(
        initializationNanos,
        before,
        after,
        firstFeasibleMillis[0],
        assignment,
        replayed,
        defaultSolver.getMoveEvaluationCount(),
        defaultSolver.getScoreCalculationCount());
  }

  private static long[] values(MathSolution solution) {
    var values = new long[solution.getDecisions().size()];
    for (var decision : solution.getDecisions()) {
      values[decision.getId()] = decision.getValue();
    }
    return values;
  }

  static BigInteger enumerateOptimum(MathModel model) {
    var best = new BigInteger[1];
    enumerate(model, new long[model.variables().size()], 0, best);
    if (best[0] == null) {
      throw new IllegalStateException(
          "Tiny qualification example has no enumerated feasible assignment.");
    }
    return best[0];
  }

  private static void enumerate(MathModel model, long[] values, int id, BigInteger[] best) {
    if (id == values.length) {
      if (MathQualificationSupport.replay(model, values).isFeasible()) {
        var value = MathQualificationSupport.evaluate(model.objective(), values);
        if (best[0] == null
            || (model.objectiveDirection() == OptimizationDirection.MINIMIZE
                ? value.compareTo(best[0]) < 0
                : value.compareTo(best[0]) > 0)) {
          best[0] = value;
        }
      }
      return;
    }
    var variable = model.variables().get(id);
    var fixed = model.fixedValue(variable);
    long lower = fixed.isPresent() ? fixed.getAsLong() : variable.lowerBound();
    long upper = fixed.isPresent() ? fixed.getAsLong() : variable.upperBound();
    for (long value = lower; ; value++) {
      values[id] = value;
      enumerate(model, values, id + 1, best);
      if (value == upper) {
        break;
      }
    }
  }

  private record Fixture(String name, MathModel model) {}

  record Measurement(
      long initializationNanos,
      Snapshot beforeSolve,
      Snapshot afterSolve,
      long firstFeasibleMillis,
      long[] values,
      HardSoftScore score,
      long moves,
      long calculations) {}
}
