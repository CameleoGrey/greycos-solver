package greycos.solver.core.impl.heuristic.selector.common;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Coordinator-owned logical selection budget. Reservations restrict generation, but only ordered
 * consumption advances the logical budget. Unconsumed work is diagnostic speculative work.
 */
public final class SelectionAttemptLedger {

  private final long limit;
  private final BooleanSupplier interrupted;
  private final ArrayDeque<SelectionAttempt<?>> outstanding = new ArrayDeque<>();
  private long consumedCount;
  private long reservedCount;
  private long discardedCount;
  private long nextSequence;
  private boolean setupInterrupted;

  public SelectionAttemptLedger(long limit) {
    this(limit, () -> false);
  }

  public SelectionAttemptLedger(long limit, BooleanSupplier interrupted) {
    if (limit < 1L) {
      throw new IllegalArgumentException(
          "The selection attempt limit (" + limit + ") must be positive.");
    }
    this.limit = limit;
    this.interrupted = Objects.requireNonNull(interrupted);
  }

  public <T> SelectionAttemptCursor<T> openCursor(Iterator<T> source) {
    Objects.requireNonNull(source);
    return new SelectionAttemptCursor<>(this, () -> source);
  }

  /** Includes selector iterator construction in cooperative selection accounting. */
  public <T> SelectionAttemptCursor<T> openCursor(Supplier<Iterator<T>> sourceSupplier) {
    return new SelectionAttemptCursor<>(this, Objects.requireNonNull(sourceSupplier));
  }

  /**
   * Bounds lifecycle selection work before it becomes an ordered candidate source. Cache entries
   * are speculative until selected; all setup reservations are released into discarded work.
   */
  public boolean runSetup(Runnable callback) {
    Objects.requireNonNull(callback);
    setupInterrupted = false;
    try (var cursor =
        new SelectionAttemptCursor<Object>(
            this,
            () ->
                new Iterator<>() {
                  @Override
                  public boolean hasNext() {
                    callback.run();
                    return false;
                  }

                  @Override
                  public Object next() {
                    throw new UnsupportedOperationException(
                        "A selection setup callback has no candidate.");
                  }
                },
            true)) {
      cursor.next();
      setupInterrupted = cursor.isInterrupted();
      return cursor.isSourceExhausted() && !setupInterrupted;
    }
  }

  public boolean wasSetupInterrupted() {
    return setupInterrupted;
  }

  boolean isInterrupted() {
    return Thread.currentThread().isInterrupted() || interrupted.getAsBoolean();
  }

  boolean reserve() {
    if (!hasUnreservedCapacity()) {
      return false;
    }
    reservedCount++;
    return true;
  }

  void releaseUnperformedReservation() {
    reservedCount--;
  }

  <T> SelectionAttempt<T> register(SelectionAttemptCursor<?> cursor, long cost, T selection) {
    var attempt = new SelectionAttempt<>(this, cursor, nextSequence++, cost, selection);
    outstanding.addLast(attempt);
    return attempt;
  }

  /** Settle after evaluation, undo, acceptance, and forager bookkeeping for this proposal. */
  public void consume(SelectionAttempt<?> attempt) {
    requireOutstanding(attempt);
    if (outstanding.peekFirst() != attempt) {
      throw new IllegalStateException(
          "The selection attempt (" + attempt.sequence() + ") must be consumed in source order.");
    }
    outstanding.removeFirst();
    attempt.settled = true;
    reservedCount -= attempt.cost();
    consumedCount += attempt.cost();
  }

  /** Release a speculative reservation without charging the logical budget. */
  public void discard(SelectionAttempt<?> attempt) {
    requireOutstanding(attempt);
    outstanding.remove(attempt);
    attempt.settled = true;
    reservedCount -= attempt.cost();
    discardedCount += attempt.cost();
  }

  void discardCursor(SelectionAttemptCursor<?> cursor) {
    var iterator = outstanding.iterator();
    while (iterator.hasNext()) {
      var attempt = iterator.next();
      if (attempt.cursor == cursor) {
        iterator.remove();
        attempt.settled = true;
        reservedCount -= attempt.cost();
        discardedCount += attempt.cost();
      }
    }
  }

  private void requireOutstanding(SelectionAttempt<?> attempt) {
    if (attempt.ledger != this || attempt.settled) {
      throw new IllegalStateException(
          "The selection attempt (" + attempt.sequence() + ") is foreign or already settled.");
    }
  }

  public long getLimit() {
    return limit;
  }

  public long getConsumedCount() {
    return consumedCount;
  }

  public long getReservedCount() {
    return reservedCount;
  }

  public long getDiscardedCount() {
    return discardedCount;
  }

  public boolean hasUnreservedCapacity() {
    return reservedCount < limit - consumedCount;
  }

  public boolean isExhausted() {
    return consumedCount == limit;
  }
}
