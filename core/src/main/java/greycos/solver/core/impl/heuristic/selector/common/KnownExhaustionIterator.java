package greycos.solver.core.impl.heuristic.selector.common;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/** Reports proven exhaustion without selecting, filtering, advancing, or consuming randomness. */
public interface KnownExhaustionIterator<T> extends Iterator<T> {

  boolean isKnownExhausted();

  static boolean isExhausted(Iterator<?> iterator) {
    return iterator instanceof KnownExhaustionIterator<?> known && known.isKnownExhausted();
  }

  /** The supplied size must be the exact number of elements the source will return. */
  static <T> KnownExhaustionIterator<T> withSize(Iterator<T> source, long size) {
    return new KnownExhaustionIterator<>() {
      private long remaining = size;

      @Override
      public boolean isKnownExhausted() {
        return remaining == 0;
      }

      @Override
      public boolean hasNext() {
        return source.hasNext();
      }

      @Override
      public T next() {
        var next = source.next();
        remaining--;
        return next;
      }
    };
  }

  static <T> KnownExhaustionIterator<T> ofList(List<T> list) {
    return withSize(list.iterator(), list.size());
  }

  static <T> ListIterator<T> ofList(List<T> list, int index) {
    return new KnownListIterator<>(list.listIterator(index));
  }

  final class KnownListIterator<T> implements KnownExhaustionIterator<T>, ListIterator<T> {
    private final ListIterator<T> source;

    private KnownListIterator(ListIterator<T> source) {
      this.source = source;
    }

    @Override
    public boolean isKnownExhausted() {
      // This iterator is created only for an in-memory list, whose hasNext cannot generate work.
      return !source.hasNext();
    }

    @Override
    public boolean hasNext() {
      return source.hasNext();
    }

    @Override
    public T next() {
      return source.next();
    }

    @Override
    public boolean hasPrevious() {
      return source.hasPrevious();
    }

    @Override
    public T previous() {
      return source.previous();
    }

    @Override
    public int nextIndex() {
      return source.nextIndex();
    }

    @Override
    public int previousIndex() {
      return source.previousIndex();
    }

    @Override
    public void remove() {
      source.remove();
    }

    @Override
    public void set(T value) {
      source.set(value);
    }

    @Override
    public void add(T value) {
      source.add(value);
    }
  }
}
