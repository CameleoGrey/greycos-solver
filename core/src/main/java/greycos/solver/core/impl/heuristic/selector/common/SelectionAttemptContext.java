package greycos.solver.core.impl.heuristic.selector.common;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;

/**
 * Cooperative hook for framework selection retries and lifecycle setup. It is active only during
 * budgeted selection, never during move preparation or scoring. Successful child selections and
 * wrapper passes are free; only the owner of a rejected selection invokes the hook.
 */
public final class SelectionAttemptContext {

  private static final ThreadLocal<SelectionAttemptCursor<?>> CURRENT = new ThreadLocal<>();

  private SelectionAttemptContext() {}

  static SelectionAttemptCursor<?> enter(SelectionAttemptCursor<?> cursor) {
    var previous = CURRENT.get();
    CURRENT.set(cursor);
    return previous;
  }

  static void leave(SelectionAttemptCursor<?> previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }

  public static void beforeSelection() {
    var cursor = CURRENT.get();
    if (cursor != null && cursor.isSetup()) {
      cursor.beforeSelection();
    }
  }

  public static boolean isSetup() {
    var cursor = CURRENT.get();
    return cursor != null && cursor.isSetup();
  }

  public static boolean isSelectionInterruption(Throwable failure) {
    return SelectionAttemptCursor.isSelectionInterruption(failure);
  }

  /** Every raw proposal materialized during cache construction owns one speculative unit. */
  public static void recordSetupProposal() {
    var cursor = CURRENT.get();
    if (cursor != null && cursor.isSetup()) {
      cursor.recordSetupProposal();
    }
  }

  /** Preserve a cooperative stop when framework fallback code catches an exception. */
  public static void checkActive() {
    var cursor = CURRENT.get();
    if (cursor != null) {
      cursor.checkActive();
    }
  }

  /** Lets an enclosing retry avoid charging a failure already owned by its child. */
  public static long checkpoint() {
    var cursor = CURRENT.get();
    return cursor == null ? 0L : cursor.getRecordedCount();
  }

  public static void failedSelectionSince(long checkpoint) {
    var cursor = CURRENT.get();
    if (cursor != null && cursor.getRecordedCount() == checkpoint) {
      cursor.failedSelection();
    }
  }

  public static boolean isActive() {
    return CURRENT.get() != null;
  }

  public static <T> Iterator<T> iterator(List<T> list) {
    return isActive() ? KnownExhaustionIterator.ofList(list) : list.iterator();
  }

  public static <T> ListIterator<T> listIterator(List<T> list, int index) {
    return isActive() ? KnownExhaustionIterator.ofList(list, index) : list.listIterator(index);
  }

  public static <T> Iterator<T> originalIterator(ValueRange<T> range) {
    var iterator = range.createOriginalIterator();
    return isActive() ? KnownExhaustionIterator.withSize(iterator, range.getSize()) : iterator;
  }

  /**
   * Record one rejected raw proposal or failed lower-level retry and reserve before retrying.
   * Calling this for an accepted child selection would incorrectly double-charge its parent move.
   */
  public static void failedSelection() {
    var cursor = CURRENT.get();
    if (cursor != null) {
      cursor.failedSelection();
    }
  }
}
