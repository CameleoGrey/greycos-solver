package greycos.solver.core.impl.solver.monitoring;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Physical score work owned by one resident search session, including its temporary directors.
 *
 * <p>The session owner increments and snapshots this counter. It must not be accessed concurrently;
 * resumable sessions do not use nested move-thread pools. Closing or replacing a director does not
 * reset this counter, and importing existing logical calculation counts does not increment it.
 */
public final class ResumableSearchWorkCounter {

  private final long[] scoreCalculationCounts =
      new long[ResumableSearchWorkCategory.values().length];
  private ResumableSearchWorkCategory category = ResumableSearchWorkCategory.INITIALIZATION;

  /** Records an attempt immediately before entering the score calculation implementation. */
  public void recordScoreCalculation() {
    scoreCalculationCounts[category.ordinal()]++;
  }

  /** Temporarily classifies score work; nested scopes restore their enclosing purpose. */
  public Scope scope(ResumableSearchWorkCategory category) {
    var previousCategory = this.category;
    this.category = Objects.requireNonNull(category);
    return new Scope(previousCategory);
  }

  /** Must be called by the session owner at a completed execution boundary. */
  public ResumableSearchWorkSnapshot snapshot() {
    var counts = new EnumMap<ResumableSearchWorkCategory, Long>(ResumableSearchWorkCategory.class);
    long total = 0L;
    for (var workCategory : ResumableSearchWorkCategory.values()) {
      var count = scoreCalculationCounts[workCategory.ordinal()];
      if (count != 0L) {
        counts.put(workCategory, count);
        total = Math.addExact(total, count);
      }
    }
    return new ResumableSearchWorkSnapshot(total, counts);
  }

  public final class Scope implements AutoCloseable {

    private final ResumableSearchWorkCategory previousCategory;
    private boolean closed;

    private Scope(ResumableSearchWorkCategory previousCategory) {
      this.previousCategory = previousCategory;
    }

    @Override
    public void close() {
      if (!closed) {
        category = previousCategory;
        closed = true;
      }
    }
  }
}
