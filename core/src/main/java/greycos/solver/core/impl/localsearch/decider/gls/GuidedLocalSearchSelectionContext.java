package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import greycos.solver.core.impl.heuristic.move.AbstractSelectorBasedMove;
import greycos.solver.core.preview.api.move.Move;

/** Phase-local origin priorities, published only after all candidate work has completed. */
public final class GuidedLocalSearchSelectionContext<Solution_> {

  public interface PrioritySnapshot {
    boolean isEmpty();

    Priority entityPriority(Object entity, Collection<String> variableNames);

    Priority valuePriority(Object value, String variableName);

    Priority subListPriority(Object owner, String variableName, int fromIndex, int length);
  }

  public record Priority(GuidedLocalSearchNumber numerator, long denominator)
      implements Comparable<Priority> {
    public static final Priority ZERO = new Priority(GuidedLocalSearchNumber.ZERO, 1L);

    public Priority {
      Objects.requireNonNull(numerator);
      if (denominator <= 0L || numerator.signum() < 0) {
        throw new IllegalArgumentException(
            "Origin priority requires a nonnegative numerator ("
                + numerator
                + ") and positive denominator ("
                + denominator
                + ").");
      }
    }

    public static Priority of(GuidedLocalSearchNumber numerator) {
      return numerator.signum() == 0 ? ZERO : new Priority(numerator, 1L);
    }

    @Override
    public int compareTo(Priority other) {
      return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }
  }

  private static final int TOURNAMENT_SIZE = 8;
  private PrioritySnapshot priorities;
  private long emittedOriginCount;
  private long eligibleOriginProbes;
  private long emittedOrigins;
  private long ordinaryOrigins;
  private boolean lastOriginOrdinary;

  public void publish(PrioritySnapshot priorities) {
    this.priorities = Objects.requireNonNull(priorities);
  }

  public void clear() {
    priorities = null;
    emittedOriginCount = 0;
    eligibleOriginProbes = emittedOrigins = ordinaryOrigins = 0L;
    resetLastOrigin();
  }

  /** Counts eligible child draws and returned origins since the last clear. */
  public OriginDiagnostics getDiagnostics() {
    return new OriginDiagnostics(eligibleOriginProbes, emittedOrigins, ordinaryOrigins);
  }

  public record OriginDiagnostics(
      long eligibleOriginProbes, long emittedOrigins, long ordinaryOrigins) {}

  public void resetLastOrigin() {
    lastOriginOrdinary = false;
  }

  public boolean wasLastOriginOrdinary() {
    return lastOriginOrdinary;
  }

  public void restoreLastOrigin(boolean ordinary) {
    lastOriginOrdinary = ordinary;
  }

  public boolean isOrdinaryCandidate(Move<?> move) {
    return move instanceof AbstractSelectorBasedMove<?> selectorMove
        && selectorMove.hasGuidedLocalSearchOrdinaryOrigin();
  }

  public Priority entityPriority(Object entity, Collection<String> variableNames) {
    return priorities == null ? Priority.ZERO : priorities.entityPriority(entity, variableNames);
  }

  public Priority valuePriority(Object value, String variableName) {
    return priorities == null ? Priority.ZERO : priorities.valuePriority(value, variableName);
  }

  public Priority subListPriority(Object owner, String variableName, int fromIndex, int length) {
    return priorities == null
        ? Priority.ZERO
        : priorities.subListPriority(owner, variableName, fromIndex, length);
  }

  /**
   * Reorders finite sources in bounded blocks without dropping a single selection. Only an
   * explicitly random, unbounded source may discard losing probes. Ordinary slots draw directly
   * from the child while it has remaining selections, providing unbiased calibration probes.
   */
  public <T> Iterator<T> direct(
      Iterator<T> child, boolean discardLosingProbes, Function<T, Priority> priority) {
    return new Iterator<>() {
      private final List<T> pending = new ArrayList<>(TOURNAMENT_SIZE);

      @Override
      public boolean hasNext() {
        return !pending.isEmpty() || child.hasNext();
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        boolean ordinarySlot = (++emittedOriginCount & 3L) == 0L;
        boolean inactive = priorities == null || priorities.isEmpty();
        if (ordinarySlot || inactive) {
          if (child.hasNext()) {
            lastOriginOrdinary = true;
            T selected = child.next();
            eligibleOriginProbes++;
            emittedOrigins++;
            ordinaryOrigins++;
            return selected;
          }
          // The remaining buffer has already participated in a tournament. Do not use it to
          // calibrate a scale from ordinary samples, even when this is an ordinary cadence slot.
          lastOriginOrdinary = false;
          T selected = pending.removeFirst();
          emittedOrigins++;
          return selected;
        }
        if (pending.isEmpty()) {
          for (int i = 0; i < TOURNAMENT_SIZE && child.hasNext(); i++) {
            pending.add(child.next());
            eligibleOriginProbes++;
          }
        }
        int bestIndex = 0;
        Priority bestPriority = priority.apply(pending.getFirst());
        for (int i = 1; i < pending.size(); i++) {
          Priority candidatePriority = priority.apply(pending.get(i));
          if (candidatePriority.compareTo(bestPriority) > 0) {
            bestIndex = i;
            bestPriority = candidatePriority;
          }
        }
        T selected = pending.remove(bestIndex);
        if (discardLosingProbes) {
          pending.clear();
        }
        lastOriginOrdinary = false;
        emittedOrigins++;
        return selected;
      }
    };
  }
}
