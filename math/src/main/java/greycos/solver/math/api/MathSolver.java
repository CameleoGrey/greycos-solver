package greycos.solver.math.api;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.math.config.SolveOptions;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;
import greycos.solver.math.impl.model.MathCompiler;
import greycos.solver.math.impl.score.MathConstraintProvider;
import greycos.solver.math.impl.search.MathChangeMoveIteratorFactory;
import greycos.solver.math.impl.solver.IndependentEvaluator;

import org.jspecify.annotations.NullMarked;

/** Entry point for bounded, serial heuristic search over immutable mathematical models. */
@NullMarked
public final class MathSolver {

  private MathSolver() {}

  /**
   * Initializes a complete assignment, runs scalar-change local search, and independently checks
   * the returned incumbent against the original expressions. Each call owns its solver and mutable
   * assignments, so the same model and options can be solved concurrently.
   *
   * <p>Interruption requests early termination and the interrupt flag is preserved. A usable
   * independently checked incumbent is returned when available; cancellation without a complete
   * assignment throws {@link CancellationException}. Numeric, configuration and evaluation errors
   * are exceptions, never infeasibility results. Time limits bound solver search, not this entire
   * method. No result proves optimality or model infeasibility.
   */
  public static MathResult solve(MathModel model, SolveOptions options) {
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(options, "options");
    long started = System.nanoTime();
    var compiled = MathCompiler.compile(model);
    var solution = MathSolution.initialize(compiled, options.seed());
    var factory = SolverFactory.<MathSolution>create(solverConfig(options));
    // Construct even for direct evaluation, so invalid expert search settings are never ignored.
    var solver = factory.buildSolver();
    MathResult.Termination termination;
    if (Thread.currentThread().isInterrupted()) {
      termination = MathResult.Termination.EARLY_TERMINATION_OBSERVED;
      SolutionManager.create(factory).update(solution);
    } else if (options.timeLimit().filter(Duration::isZero).isPresent()
        || (options.moveEvaluationLimit().isPresent()
            && options.moveEvaluationLimit().getAsLong() == 0L)) {
      termination = MathResult.Termination.ZERO_LIMIT;
      SolutionManager.create(factory).update(solution);
    } else if (model.variables().stream()
        .noneMatch(
            variable ->
                variable.lowerBound() != variable.upperBound()
                    && model.fixedValue(variable).isEmpty())) {
      termination = MathResult.Termination.NO_MUTABLE_DECISIONS;
      SolutionManager.create(factory).update(solution);
    } else {
      solution = solver.solve(solution);
      termination =
          solver.isTerminateEarly()
              ? MathResult.Termination.EARLY_TERMINATION_OBSERVED
              : MathResult.Termination.NORMAL_RETURN;
    }
    var values = new LinkedHashMap<IntVar, Long>();
    for (var decision : solution.getDecisions()) {
      int id = decision.getId();
      if (id < 0 || id >= model.variables().size()) {
        throw new IllegalStateException(
            "The returned decision has unknown variable ID (" + id + ").");
      }
      var variable = model.variables().get(id);
      var value = decision.getValue();
      if (value == null) {
        if (termination == MathResult.Termination.EARLY_TERMINATION_OBSERVED) {
          throw new CancellationException(
              "Search stopped without a value for variable (" + variable.name() + ").");
        }
        throw new IllegalStateException(
            "Search returned no value for variable (" + variable.name() + ").");
      }
      if (values.put(variable, value) != null) {
        throw new IllegalStateException("Search returned duplicate variable ID (" + id + ").");
      }
    }
    if (values.size() != model.variables().size()
        && termination == MathResult.Termination.EARLY_TERMINATION_OBSERVED) {
      throw new CancellationException("Search stopped without a complete mathematical assignment.");
    }
    var score = solution.getScore();
    if (score == null) {
      // Solver's public contract permits an unscored initial incumbent after an early stop.
      SolutionManager.create(factory).update(solution);
      score = solution.getScore();
    }
    if (score == null) {
      throw new IllegalStateException(
          "The mathematical incumbent has no backend score after evaluation.");
    }
    var replay = IndependentEvaluator.evaluate(model, values, score);
    return new MathResult(
        replay.values(),
        replay.objectiveValue(),
        replay.reports(),
        termination,
        Duration.ofNanos(System.nanoTime() - started),
        options);
  }

  private static SolverConfig solverConfig(SolveOptions options) {
    var termination =
        new TerminationConfig().withTerminationCompositionStyle(TerminationCompositionStyle.OR);
    options.timeLimit().ifPresent(termination::setSpentLimit);
    options.moveEvaluationLimit().ifPresent(termination::setMoveCountLimit);
    var changes =
        new MoveIteratorFactoryConfig()
            .withSelectionOrder(SelectionOrder.RANDOM)
            .withMoveIteratorFactoryClass(MathChangeMoveIteratorFactory.class);
    var phase =
        new LocalSearchPhaseConfig()
            .withMoveSelectorConfig(changes)
            .withAcceptorConfig(options.acceptorConfig())
            .withForagerConfig(options.foragerConfig());
    return new SolverConfig()
        .withSolutionClass(MathSolution.class)
        .withEntityClasses(MathDecision.class)
        .withConstraintProviderClass(MathConstraintProvider.class)
        .withEnvironmentMode(options.environmentMode())
        .withRandomSeed(options.seed())
        .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
        .withTerminationConfig(termination)
        .withPhases(phase);
  }
}
