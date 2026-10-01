package greycos.solver.core.config.solver;

import java.util.HashMap;
import java.util.HashSet;

import jakarta.xml.bind.annotation.XmlEnum;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

/**
 * The environment mode also allows you to detect common bugs in your implementation.
 *
 * <p>A {@link Solver} derives separate random streams for move iteration, factories, and acceptance
 * from its random seed. The environment mode determines how an unspecified seed is chosen; an
 * explicitly configured seed takes precedence in every mode.
 */
@XmlEnum
public enum EnvironmentMode {
  /**
   * This mode turns on {@link #FULL_ASSERT} and enables variable tracking to fail-fast on a bug in
   * a {@link Move} implementation, a constraint, the engine itself or something else at the highest
   * performance cost.
   *
   * <p>Because it tracks genuine and shadow variables, it is able to report precisely what
   * variables caused the corruption and report any missed {@link ShadowVariable}'s source changed
   * events.
   *
   * <p>This mode is reproducible (see {@link #PHASE_ASSERT} mode).
   *
   * <p>This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more
   * frequently than a non assert mode.
   *
   * <p>This mode is by far the slowest of all the modes.
   */
  TRACKED_FULL_ASSERT(true),
  /**
   * This mode turns on all assertions to fail-fast on a bug in a {@link Move} implementation, a
   * constraint, the engine itself or something else at a horrible performance cost.
   *
   * <p>This mode is reproducible (see {@link #PHASE_ASSERT} mode).
   *
   * <p>This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more
   * frequently than a non assert mode.
   *
   * <p>This mode is horribly slow.
   */
  FULL_ASSERT(true),
  /**
   * This mode turns on several assertions (but not all of them) to fail-fast on a bug in a {@link
   * Move} implementation, a constraint, the engine itself or something else at an overwhelming
   * performance cost.
   *
   * <p>This mode is reproducible (see {@link #PHASE_ASSERT} mode).
   *
   * <p>This mode is non-intrusive, unlike {@link #FULL_ASSERT} and {@link #STEP_ASSERT}.
   *
   * <p>This mode is horribly slow.
   */
  NON_INTRUSIVE_FULL_ASSERT(true),
  /**
   * @deprecated Prefer {@link #STEP_ASSERT}.
   */
  @Deprecated(forRemoval = true, since = "1.20.0")
  FAST_ASSERT(true),
  /**
   * This mode turns on several assertions to fail-fast on a bug in a {@link Move} implementation, a
   * constraint rule, the engine itself or something else at a reasonable performance cost (in
   * development at least).
   *
   * <p>This mode is reproducible (see {@link #PHASE_ASSERT} mode).
   *
   * <p>This mode is intrusive because it calls the {@link InnerScoreDirector#calculateScore()} more
   * frequently than a non-assert mode.
   *
   * <p>This mode is slow.
   */
  STEP_ASSERT(true),
  /**
   * This is the default mode as it is recommended during development, and runs minimal correctness
   * checks that serve to quickly identify score corruption bugs.
   *
   * <p>This mode uses a fixed default random seed when none is configured. Repeating a search
   * requires identical inputs, configuration, software versions, ordered data, deterministic custom
   * components, and work-based termination. Under those conditions, ordinary construction
   * heuristics, local search, and ALNS repeat their assignments and scores at each completed step,
   * including with a fixed move-worker configuration.
   *
   * <p>Warning: some code can disrupt reproducibility regardless of this mode. This typically
   * happens when user code serves data such as {@link PlanningEntity planning entities} from
   * collections without defined iteration order, such as {@link HashSet} or {@link HashMap}.
   *
   * <p>Time-based termination, external cancellation, live problem changes, and asynchronous island
   * or partition orchestration can change the result despite a fixed seed. Physical
   * speculative-worker counts and elapsed durations are not part of the reproducibility contract.
   */
  PHASE_ASSERT(true),
  /**
   * @deprecated Prefer {@link #NO_ASSERT}.
   */
  @Deprecated(forRemoval = true, since = "1.20.0")
  REPRODUCIBLE(false),
  /**
   * As defined by {@link #PHASE_ASSERT}, but disables every single bug detection mechanism. This
   * mode will run negligibly faster than {@link #PHASE_ASSERT}, but will allow some bugs in user
   * code (such as score corruptions) to go unnoticed. Use this mode when you are confident that
   * your code is bug-free, or when you want to ignore a known bug temporarily.
   */
  NO_ASSERT(false),
  /**
   * The non-reproducible mode is equally fast or slightly faster than {@link #NO_ASSERT}.
   *
   * <p>Unless explicitly configured, the random seed is generated when building a solver, which
   * makes it more robust against an unlucky random seed. An unlucky random seed gives a bad result
   * on a certain data set with a certain solver configuration. Note that in most use cases, the
   * impact of the random seed is relatively low on the result. An occasional bad result is far more
   * likely to be caused by another issue (such as a score trap).
   *
   * <p>An explicit seed still takes precedence in this mode. The same input, configuration, and
   * termination requirements described by {@link #PHASE_ASSERT} apply when replaying a search.
   */
  NON_REPRODUCIBLE(false);

  private final boolean asserted;

  EnvironmentMode(boolean asserted) {
    this.asserted = asserted;
  }

  public boolean isStepAssertOrMore() {
    if (!isAsserted()) {
      return false;
    }
    return this != PHASE_ASSERT;
  }

  public boolean isAsserted() {
    return asserted;
  }

  public boolean isFullyAsserted() {
    return switch (this) {
      case TRACKED_FULL_ASSERT, FULL_ASSERT, NON_INTRUSIVE_FULL_ASSERT -> true;
      case STEP_ASSERT, FAST_ASSERT, PHASE_ASSERT, REPRODUCIBLE, NO_ASSERT, NON_REPRODUCIBLE ->
          false;
    };
  }

  public boolean isIntrusivelyAsserted() {
    return switch (this) {
      // STEP_ASSERT = former FAST_ASSERT
      case TRACKED_FULL_ASSERT, FULL_ASSERT, STEP_ASSERT, FAST_ASSERT -> true;
      // NO_ASSERT = former REPRODUCIBLE
      case NON_INTRUSIVE_FULL_ASSERT, PHASE_ASSERT, NO_ASSERT, NON_REPRODUCIBLE, REPRODUCIBLE ->
          false;
    };
  }

  public boolean isReproducible() {
    return this != NON_REPRODUCIBLE;
  }

  public boolean isTracking() {
    return this == TRACKED_FULL_ASSERT;
  }
}
