package greycos.solver.core.impl.move;

/** Optional owner-thread accounting for assignment snapshots, comparison and validation. */
public final class SolutionAssignmentDiagnostics implements AutoCloseable {
  private static final ThreadLocal<SolutionAssignmentDiagnostics> CURRENT = new ThreadLocal<>();

  private final SolutionAssignmentDiagnostics previous;
  private boolean closed;
  private long captureCount;
  private long capturedBindingCount;
  private long copiedListElementCount;
  private long captureNanos;
  private long comparisonCount;
  private long comparisonNanos;
  private long validationCount;
  private long validationNanos;
  private long accumulatorUpdateCount;
  private long accumulatorBindingCount;
  private long accumulatorListElementCount;

  private SolutionAssignmentDiagnostics() {
    previous = CURRENT.get();
    CURRENT.set(this);
  }

  public static SolutionAssignmentDiagnostics open() {
    return new SolutionAssignmentDiagnostics();
  }

  static SolutionAssignmentDiagnostics current() {
    return CURRENT.get();
  }

  void captured(long bindings, long listElements, long nanos) {
    captureCount++;
    capturedBindingCount += bindings;
    copiedListElementCount += listElements;
    captureNanos += nanos;
  }

  void compared(long nanos) {
    comparisonCount++;
    comparisonNanos += nanos;
  }

  void validated(long nanos) {
    validationCount++;
    validationNanos += nanos;
  }

  void accumulated(long bindings, long listElements) {
    accumulatorUpdateCount++;
    accumulatorBindingCount += bindings;
    accumulatorListElementCount += listElements;
  }

  public long getCaptureCount() {
    return captureCount;
  }

  public long getCapturedBindingCount() {
    return capturedBindingCount;
  }

  public long getCopiedListElementCount() {
    return copiedListElementCount;
  }

  public long getCaptureNanos() {
    return captureNanos;
  }

  public long getComparisonCount() {
    return comparisonCount;
  }

  public long getComparisonNanos() {
    return comparisonNanos;
  }

  public long getValidationCount() {
    return validationCount;
  }

  public long getValidationNanos() {
    return validationNanos;
  }

  public long getAccumulatorUpdateCount() {
    return accumulatorUpdateCount;
  }

  public long getAccumulatorBindingCount() {
    return accumulatorBindingCount;
  }

  public long getAccumulatorListElementCount() {
    return accumulatorListElementCount;
  }

  @Override
  public void close() {
    if (closed) return;
    if (CURRENT.get() != this) {
      throw new IllegalStateException(
          "Assignment diagnostics must close on their owner thread in reverse opening order.");
    }
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
    closed = true;
  }
}
