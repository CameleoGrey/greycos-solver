package greycos.solver.core.api.solver.multistage;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** The selected operation, an unchanged stage, or cancellation of the enclosing candidate. */
public final class MultistageStageResult<Solution_> {

  public enum Kind {
    APPLY,
    SKIP,
    ABORT_CANDIDATE
  }

  private final Kind kind;
  private final @Nullable MultistageOperation<Solution_> operation;

  private MultistageStageResult(Kind kind, @Nullable MultistageOperation<Solution_> operation) {
    this.kind = kind;
    this.operation = operation;
  }

  /** Selects an operation created by the current stage's evaluator. */
  public static <Solution_> MultistageStageResult<Solution_> apply(
      MultistageOperation<Solution_> operation) {
    return new MultistageStageResult<>(Kind.APPLY, Objects.requireNonNull(operation));
  }

  /** Leaves the current state unchanged and continues with the next stage. */
  public static <Solution_> MultistageStageResult<Solution_> skip() {
    return new MultistageStageResult<>(Kind.SKIP, null);
  }

  /** Restores the state from before the candidate and stops its remaining stages. */
  public static <Solution_> MultistageStageResult<Solution_> abortCandidate() {
    return new MultistageStageResult<>(Kind.ABORT_CANDIDATE, null);
  }

  public Kind kind() {
    return kind;
  }

  /** The selected operation for {@link Kind#APPLY}; otherwise null. */
  public @Nullable MultistageOperation<Solution_> operation() {
    return operation;
  }
}
