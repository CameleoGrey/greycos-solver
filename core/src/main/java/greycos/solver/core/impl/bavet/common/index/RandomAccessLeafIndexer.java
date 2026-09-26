package greycos.solver.core.impl.bavet.common.index;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.util.ElementAwareArrayList;
import greycos.solver.core.impl.util.ListEntry;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An {@link LeafIndexer} that supports random access to its entries. It is shown to be 10-20 %
 * slower than {@link LinkedListLeafIndexer} in the micro benchmarks when used as the backend for
 * constraint streams.
 *
 * @param <T> the type of tuple being indexed
 */
@NullMarked
public final class RandomAccessLeafIndexer<T> implements LeafIndexer<T> {

  private final ElementAwareArrayList<T> tupleList = new ElementAwareArrayList<>();

  @Override
  public ListEntry<T> put(Object compositeKey, T tuple) {
    return tupleList.addEntry(tuple);
  }

  @Override
  public void remove(Object compositeKey, ListEntry<T> entry) {
    ((ElementAwareArrayList<T>.Entry) entry).remove();
  }

  @Override
  public int size(Object compositeKey) {
    return tupleList.size();
  }

  @Override
  public void forEach(Object compositeKey, Consumer<T> tupleConsumer) {
    // Reads must not compact: another iterator may hold reservations for these physical slots.
    var slotCount = tupleList.slotCount();
    for (var slot = 0; slot < slotCount; slot++) {
      var entry = tupleList.entryAt(slot);
      if (entry != null) {
        tupleConsumer.accept(entry.element());
      }
    }
  }

  @Override
  public Iterator<T> iterator(Object queryCompositeKey) {
    return new Iterator<>() {
      private final int slotCount = tupleList.slotCount();
      private int nextSlot;
      private @Nullable ElementAwareArrayList<T>.Entry nextEntry;

      @Override
      public boolean hasNext() {
        while (nextEntry == null && nextSlot < slotCount) {
          nextEntry = tupleList.entryAt(nextSlot++);
        }
        return nextEntry != null;
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var element = Objects.requireNonNull(nextEntry).element();
        nextEntry = null;
        return element;
      }
    };
  }

  @Override
  public RepeatingRandomIterator<T> randomIterator(
      Object queryCompositeKey, RandomGenerator workingRandom) {
    return RepeatingRandomIterator.of(tupleList, workingRandom);
  }

  @Override
  public UniqueRandomIterator<T> uniqueRandomIterator(
      Object queryCompositeKey, RandomGenerator workingRandom) {
    return UniqueRandomIterator.of(tupleList, workingRandom);
  }

  @Override
  public boolean isRemovable() {
    return tupleList.isEmpty();
  }

  @Override
  public String toString() {
    return "size = " + tupleList.size();
  }
}
