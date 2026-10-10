package greycos.solver.core.impl.heuristic.selector.common;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Budgeted generator. Rejection runs become ordered markers; accepted proposals remain reserved
 * until the coordinator finishes their bookkeeping. Closing discards only this cursor's work.
 */
public final class SelectionAttemptCursor<T> implements AutoCloseable {

  private static final BudgetStoppedException BUDGET_STOPPED = new BudgetStoppedException();

  private final SelectionAttemptLedger ledger;
  private final boolean setup;
  private final Supplier<Iterator<T>> sourceSupplier;
  private Iterator<T> source;
  private final ArrayDeque<SelectionAttempt<T>> ready = new ArrayDeque<>();
  private boolean sourceExhausted;
  private boolean budgetStopped;
  private boolean interrupted;
  private boolean closed;
  private boolean currentReserved;
  private long markerCount;

  SelectionAttemptCursor(SelectionAttemptLedger ledger, Supplier<Iterator<T>> sourceSupplier) {
    this(ledger, sourceSupplier, false);
  }

  SelectionAttemptCursor(
      SelectionAttemptLedger ledger, Supplier<Iterator<T>> sourceSupplier, boolean setup) {
    this.ledger = ledger;
    this.sourceSupplier = sourceSupplier;
    this.setup = setup;
  }

  /**
   * Returns the next ordered marker/proposal, or null when the source or reservation quota ends.
   * The two end reasons are deliberately distinct; no unbudgeted hasNext call proves exhaustion.
   */
  public SelectionAttempt<T> next() {
    if (closed) {
      throw new IllegalStateException("The selection attempt cursor is closed.");
    }
    if (!ready.isEmpty()) {
      return ready.removeFirst();
    }
    if (isSourceExhausted() || budgetStopped || interrupted) {
      return null;
    }
    if (ledger.isInterrupted()) {
      interrupted = true;
      return null;
    }
    if (!ledger.reserve()) {
      budgetStopped = true;
      return null;
    }
    currentReserved = true;
    var previous = SelectionAttemptContext.enter(this);
    try {
      if (source == null) {
        source = Objects.requireNonNull(sourceSupplier.get());
      }
      if (source.hasNext()) {
        T selection = source.next();
        if (selection == null) {
          throw new IllegalStateException("A top-level move proposal must not be null.");
        }
        registerFailures();
        ready.addLast(ledger.register(this, 1L, selection));
        currentReserved = false;
      } else {
        sourceExhausted = true;
        registerFailures();
        if (currentReserved) ledger.releaseUnperformedReservation();
        currentReserved = false;
      }
    } catch (BudgetStoppedException ignored) {
      registerFailures();
      if (currentReserved) {
        ledger.releaseUnperformedReservation();
        currentReserved = false;
      }
    } catch (RuntimeException | Error failure) {
      registerFailures();
      if (currentReserved) {
        ledger.releaseUnperformedReservation();
        currentReserved = false;
      }
      close();
      throw failure;
    } finally {
      SelectionAttemptContext.leave(previous);
    }
    return ready.pollFirst();
  }

  static boolean isSelectionInterruption(Throwable failure) {
    return failure instanceof BudgetStoppedException;
  }

  boolean isSetup() {
    return setup;
  }

  long getRecordedCount() {
    return markerCount;
  }

  void beforeSelection() {
    checkActive();
    if (ledger.isInterrupted()) {
      interrupted = true;
      throw BUDGET_STOPPED;
    }
    if (!currentReserved) {
      currentReserved = ledger.reserve();
      if (!currentReserved) {
        budgetStopped = true;
        throw BUDGET_STOPPED;
      }
    }
  }

  void recordSetupProposal() {
    if (!currentReserved) {
      throw new IllegalStateException(
          "A cache proposal was generated without a selection reservation.");
    }
    markerCount++;
    currentReserved = false;
  }

  void checkActive() {
    if (budgetStopped || interrupted) {
      throw BUDGET_STOPPED;
    }
  }

  void failedSelection() {
    markerCount++;
    currentReserved = false;
    if (ledger.isInterrupted()) {
      interrupted = true;
      throw BUDGET_STOPPED;
    }
    currentReserved = ledger.reserve();
    if (!currentReserved) {
      budgetStopped = true;
      throw BUDGET_STOPPED;
    }
  }

  private void registerFailures() {
    if (markerCount != 0L) {
      ready.addLast(ledger.register(this, markerCount, null));
      markerCount = 0L;
    }
  }

  public boolean isSourceExhausted() {
    return sourceExhausted
        || (source != null && ready.isEmpty() && KnownExhaustionIterator.isExhausted(source));
  }

  public boolean isInterrupted() {
    return interrupted;
  }

  public boolean isBudgetStopped() {
    return budgetStopped && !isSourceExhausted();
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      ready.clear();
      ledger.discardCursor(this);
    }
  }

  private static final class BudgetStoppedException extends RuntimeException {
    private BudgetStoppedException() {
      super(null, null, false, false);
    }
  }
}
