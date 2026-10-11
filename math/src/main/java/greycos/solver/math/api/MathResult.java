package greycos.solver.math.api;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.math.config.SolveOptions;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable, independently checked best-incumbent snapshot. A successful result always contains a
 * complete in-domain assignment preserving fixed values; it may violate mathematical constraints.
 * No status proves optimality or infeasibility.
 */
@NullMarked
public final class MathResult {

  /** Mathematical feasibility of the returned assignment, without a proof claim about the model. */
  public enum Status {
    FEASIBLE,
    NO_FEASIBLE_SOLUTION_FOUND
  }

  /**
   * What the adapter observed. NORMAL_RETURN does not identify a particular limit or prove that
   * search exhausted all assignments. Early termination includes solver-thread interruption.
   */
  public enum Termination {
    NORMAL_RETURN,
    EARLY_TERMINATION_OBSERVED,
    ZERO_LIMIT,
    NO_MUTABLE_DECISIONS
  }

  private final Status status;
  private final Map<IntVar, Long> values;
  private final long objectiveValue;
  private final List<ConstraintReport> constraintReports;
  private final Termination termination;
  private final Duration elapsed;
  private final SolveOptions options;

  MathResult(
      Map<IntVar, Long> values,
      long objectiveValue,
      List<ConstraintReport> constraintReports,
      Termination termination,
      Duration elapsed,
      SolveOptions options) {
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    this.objectiveValue = objectiveValue;
    this.constraintReports = List.copyOf(constraintReports);
    status =
        this.constraintReports.stream().allMatch(report -> report.violation() == 0L)
            ? Status.FEASIBLE
            : Status.NO_FEASIBLE_SOLUTION_FOUND;
    this.termination = Objects.requireNonNull(termination, "termination");
    this.elapsed = Objects.requireNonNull(elapsed, "elapsed");
    this.options = Objects.requireNonNull(options, "options");
  }

  /** Returns independently checked feasibility of this assignment. */
  public Status status() {
    return status;
  }

  /**
   * Returns immutable values in model declaration order, keyed by the original variable handles.
   */
  public Map<IntVar, Long> values() {
    return values;
  }

  /** Returns a variable value; rejects handles not belonging to this result's model. */
  public long value(IntVar variable) {
    var value = values.get(Objects.requireNonNull(variable, "variable"));
    if (value == null) {
      throw new IllegalArgumentException(
          "The variable (" + variable.name() + ") does not belong to this result's model.");
    }
    return value;
  }

  /** Returns the original objective value, before the backend's minimization sign conversion. */
  public long objectiveValue() {
    return objectiveValue;
  }

  /** Returns independent reports in model declaration order. */
  public List<ConstraintReport> constraintReports() {
    return constraintReports;
  }

  /** Returns observed termination information, not an inferred limit or a proof status. */
  public Termination termination() {
    return termination;
  }

  /**
   * Returns adapter elapsed time, including compilation, initialization, solving and final replay.
   */
  public Duration elapsed() {
    return elapsed;
  }

  /** Returns immutable effective solve options; nested configuration accessors return copies. */
  public SolveOptions options() {
    return options;
  }
}
