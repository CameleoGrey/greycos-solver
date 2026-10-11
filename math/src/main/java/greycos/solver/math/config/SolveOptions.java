package greycos.solver.math.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.FinalistPodiumType;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.config.solver.EnvironmentMode;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Immutable options for serial, scalar-change mathematical local search. At least one finite limit
 * is required; multiple limits are combined with OR. The default seed is zero, the environment mode
 * is {@link EnvironmentMode#PHASE_ASSERT}, and search uses Late Acceptance with a history of 400
 * scores and one accepted candidate per step.
 *
 * <p>Work limits permit seeded replay with identical inputs, ordered data and software versions.
 * Time limits and interruption do not provide that reproducibility guarantee. Configuration objects
 * are copied on input and output; modifying them does not modify these options.
 */
@NullMarked
public final class SolveOptions {

  private final @Nullable Duration timeLimit;
  private final @Nullable Long moveEvaluationLimit;
  private final long seed;
  private final EnvironmentMode environmentMode;
  private final LocalSearchAcceptorConfig acceptorConfig;
  private final LocalSearchForagerConfig foragerConfig;

  private SolveOptions(
      @Nullable Duration timeLimit,
      @Nullable Long moveEvaluationLimit,
      long seed,
      EnvironmentMode environmentMode,
      LocalSearchAcceptorConfig acceptorConfig,
      LocalSearchForagerConfig foragerConfig) {
    if (timeLimit == null && moveEvaluationLimit == null) {
      throw new IllegalArgumentException("At least one time or move evaluation limit is required.");
    }
    if (timeLimit != null) {
      validateTimeLimit(timeLimit);
    }
    if (moveEvaluationLimit != null && moveEvaluationLimit < 0L) {
      throw new IllegalArgumentException(
          "The moveEvaluationLimit (" + moveEvaluationLimit + ") must be nonnegative.");
    }
    this.timeLimit = timeLimit;
    this.moveEvaluationLimit = moveEvaluationLimit;
    this.seed = seed;
    this.environmentMode = Objects.requireNonNull(environmentMode, "environmentMode");
    this.acceptorConfig = copyAcceptor(Objects.requireNonNull(acceptorConfig, "acceptorConfig"));
    this.foragerConfig = copyForager(Objects.requireNonNull(foragerConfig, "foragerConfig"));
  }

  /**
   * Starts options with a solver-search time limit, excluding compilation, initialization and final
   * replay. The limit must be nonnegative, representable in milliseconds and use whole
   * milliseconds. Zero initializes and evaluates a complete assignment without searching.
   */
  public static SolveOptions timeLimit(Duration limit) {
    return defaults(Objects.requireNonNull(limit, "limit"), null);
  }

  /**
   * Starts options with a nonnegative limit on evaluated moves, not accepted steps or score calls.
   * Zero initializes and evaluates a complete assignment without searching.
   */
  public static SolveOptions moveEvaluationLimit(long limit) {
    return defaults(null, limit);
  }

  private static SolveOptions defaults(@Nullable Duration timeLimit, @Nullable Long moveLimit) {
    return new SolveOptions(
        timeLimit,
        moveLimit,
        0L,
        EnvironmentMode.PHASE_ASSERT,
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(List.of(AcceptorType.LATE_ACCEPTANCE))
            .withLateAcceptanceSize(400),
        new LocalSearchForagerConfig().withAcceptedCountLimit(1));
  }

  /** Returns new options replacing or adding the time limit; either limit can end search. */
  public SolveOptions withTimeLimit(Duration limit) {
    return new SolveOptions(
        Objects.requireNonNull(limit, "limit"),
        moveEvaluationLimit,
        seed,
        environmentMode,
        acceptorConfig,
        foragerConfig);
  }

  /** Returns new options replacing or adding the move limit; either limit can end search. */
  public SolveOptions withMoveEvaluationLimit(long limit) {
    return new SolveOptions(timeLimit, limit, seed, environmentMode, acceptorConfig, foragerConfig);
  }

  /** Uses the seed for independently initialized assignment and search random streams. */
  public SolveOptions withSeed(long newSeed) {
    return new SolveOptions(
        timeLimit, moveEvaluationLimit, newSeed, environmentMode, acceptorConfig, foragerConfig);
  }

  /** Replaces the environment mode; the explicit seed remains effective in every mode. */
  public SolveOptions withEnvironmentMode(EnvironmentMode mode) {
    return new SolveOptions(
        timeLimit, moveEvaluationLimit, seed, mode, acceptorConfig, foragerConfig);
  }

  /**
   * Replaces the acceptor settings with a defensive snapshot. Stable built-in acceptors and their
   * combinations are supported; preview Diversified Late Acceptance and the unimplemented {@link
   * AcceptorType#UNDO_MOVE_TABU} enum value are not supported. Invalid parameters are rejected
   * while creating options or constructing the solver, including for zero-budget solves.
   */
  public SolveOptions withAcceptorConfig(LocalSearchAcceptorConfig config) {
    return new SolveOptions(
        timeLimit, moveEvaluationLimit, seed, environmentMode, config, foragerConfig);
  }

  /**
   * Replaces the built-in forager settings with a defensive snapshot. An omitted accepted-count
   * limit defaults to one; an explicit limit must be between one and {@link Integer#MAX_VALUE} - 1.
   * Custom forager classes and custom properties are not supported.
   */
  public SolveOptions withForagerConfig(LocalSearchForagerConfig config) {
    return new SolveOptions(
        timeLimit, moveEvaluationLimit, seed, environmentMode, acceptorConfig, config);
  }

  /** Returns the configured solver-search time limit. */
  public Optional<Duration> timeLimit() {
    return Optional.ofNullable(timeLimit);
  }

  /** Returns the configured evaluated-move limit. */
  public OptionalLong moveEvaluationLimit() {
    return moveEvaluationLimit == null
        ? OptionalLong.empty()
        : OptionalLong.of(moveEvaluationLimit);
  }

  /** Returns the effective initialization and search seed. */
  public long seed() {
    return seed;
  }

  /** Returns the effective environment mode. */
  public EnvironmentMode environmentMode() {
    return environmentMode;
  }

  /** Returns an independent, mutable copy of the acceptor settings. */
  public LocalSearchAcceptorConfig acceptorConfig() {
    return copyAcceptor(acceptorConfig);
  }

  /** Returns an independent, mutable copy of the effective built-in forager settings. */
  public LocalSearchForagerConfig foragerConfig() {
    return copyForager(foragerConfig);
  }

  private static void validateTimeLimit(Duration limit) {
    if (limit.isNegative() || limit.getNano() % 1_000_000 != 0) {
      throw new IllegalArgumentException(
          "The timeLimit (" + limit + ") must be nonnegative and use whole milliseconds.");
    }
    try {
      limit.toMillis();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          "The timeLimit (" + limit + ") exceeds the supported millisecond range.", e);
    }
  }

  private static LocalSearchAcceptorConfig copyAcceptor(LocalSearchAcceptorConfig original) {
    var copy = new LocalSearchAcceptorConfig().inherit(original);
    var types = original.getAcceptorTypeList();
    if (types != null) {
      if (types.stream().anyMatch(Objects::isNull)
          || types.contains(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE)
          || types.contains(AcceptorType.UNDO_MOVE_TABU)) {
        throw new IllegalArgumentException(
            "The acceptorTypeList (" + types + ") must contain only stable built-in acceptors.");
      }
      copy.setAcceptorTypeList(new ArrayList<>(types));
    }
    validateAcceptorWindowsAndTemperature(copy);
    return copy;
  }

  private static void validateAcceptorWindowsAndTemperature(LocalSearchAcceptorConfig config) {
    var lateAcceptanceSize = config.getLateAcceptanceSize();
    if (lateAcceptanceSize != null && lateAcceptanceSize <= 0) {
      throw new IllegalArgumentException(
          "The lateAcceptanceSize (" + lateAcceptanceSize + ") must be positive.");
    }
    var stepCountingSize = config.getStepCountingHillClimbingSize();
    if (stepCountingSize != null && stepCountingSize <= 0) {
      throw new IllegalArgumentException(
          "The stepCountingHillClimbingSize (" + stepCountingSize + ") must be positive.");
    }
    var temperature = config.getSimulatedAnnealingStartingTemperature();
    if (temperature != null) {
      // Core checks temperature levels in phaseStarted(), which direct evaluation never calls.
      // A zero level, including an entirely zero temperature, is supported by the core acceptor.
      var score = HardSoftScore.parseScore(temperature);
      if (score.hardScore() < 0L || score.softScore() < 0L) {
        throw new IllegalArgumentException(
            "The simulatedAnnealingStartingTemperature ("
                + temperature
                + ") must have nonnegative hard and soft levels.");
      }
    }
  }

  private static LocalSearchForagerConfig copyForager(LocalSearchForagerConfig original) {
    if (original.getForagerClass() != null || original.getCustomProperties() != null) {
      throw new IllegalArgumentException(
          "Mathematical solving supports built-in foragers only; foragerClass and customProperties must be absent.");
    }
    int limit = Objects.requireNonNullElse(original.getAcceptedCountLimit(), 1);
    if (limit < 1 || limit == Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "The forager acceptedCountLimit ("
              + limit
              + ") must be between 1 and "
              + (Integer.MAX_VALUE - 1)
              + " for random scalar changes.");
    }
    return new LocalSearchForagerConfig()
        .withAcceptedCountLimit(limit)
        .withPickEarlyType(
            Objects.requireNonNullElse(original.getPickEarlyType(), LocalSearchPickEarlyType.NEVER))
        .withFinalistPodiumType(
            Objects.requireNonNullElse(
                original.getFinalistPodiumType(), FinalistPodiumType.HIGHEST_SCORE))
        .withBreakTieRandomly(Objects.requireNonNullElse(original.getBreakTieRandomly(), true));
  }
}
