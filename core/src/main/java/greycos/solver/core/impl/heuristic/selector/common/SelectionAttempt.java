package greycos.solver.core.impl.heuristic.selector.common;

/** An ordered reservation for a raw proposal or a run of unsuccessful selection attempts. */
public final class SelectionAttempt<T> {

  final SelectionAttemptLedger ledger;
  final SelectionAttemptCursor<?> cursor;
  private final long sequence;
  private final long cost;
  private final T selection;
  boolean settled;

  SelectionAttempt(
      SelectionAttemptLedger ledger,
      SelectionAttemptCursor<?> cursor,
      long sequence,
      long cost,
      T selection) {
    this.ledger = ledger;
    this.cursor = cursor;
    this.sequence = sequence;
    this.cost = cost;
    this.selection = selection;
  }

  public long sequence() {
    return sequence;
  }

  public long cost() {
    return cost;
  }

  /** Returns the raw proposal, or null for an unsuccessful-selection marker. */
  public T selection() {
    return selection;
  }

  public boolean isMarker() {
    return selection == null;
  }
}
