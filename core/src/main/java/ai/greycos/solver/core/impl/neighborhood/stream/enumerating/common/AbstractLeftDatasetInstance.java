package ai.greycos.solver.core.impl.neighborhood.stream.enumerating.common;

import java.util.Iterator;

import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.util.ElementAwareArrayList;
import ai.greycos.solver.core.impl.util.ElementAwareArrayList.Entry;
import ai.greycos.solver.core.impl.util.ListEntry;

import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class AbstractLeftDatasetInstance<Solution_, Tuple_ extends Tuple>
    extends AbstractDatasetInstance<Solution_, Tuple_> implements Iterable<Tuple_> {

  private final ElementAwareArrayList<Tuple_> tupleList = new ElementAwareArrayList<>();
  private final int rightSequenceStoreIndex;

  protected AbstractLeftDatasetInstance(
      AbstractDataset<Solution_> parent, int rightSequenceStoreIndex, int entryStoreIndex) {
    super(parent, entryStoreIndex);
    this.rightSequenceStoreIndex = rightSequenceStoreIndex;
  }

  public int getRightSequenceStoreIndex() {
    return rightSequenceStoreIndex;
  }

  @Override
  public void insert(Tuple_ tuple) {
    if (tuple.getStore(entryStoreIndex) != null) {
      throw new IllegalStateException(
          "Impossible state: the input for the tuple (%s) was already added in the tupleStore."
              .formatted(tuple));
    }

    tuple.setStore(entryStoreIndex, tupleList.add(tuple));
  }

  @Override
  public void update(Tuple_ tuple) {
    if (tuple.getStore(entryStoreIndex) == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s).
      insert(tuple);
    } else {
      // No need to do anything.
    }
  }

  @Override
  public void retract(Tuple_ tuple) {
    Entry<Tuple_> entry = tuple.removeStore(entryStoreIndex);
    if (entry == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s).
      return;
    }
    tupleList.remove(entry);
  }

  @Override
  public Iterator<Tuple_> iterator() {
    return new UnwrappingIterator<>(tupleList.asList().iterator());
  }

  public DefaultUniqueRandomSequence<Tuple_> buildRandomSequence() {
    return new DefaultUniqueRandomSequence<>(tupleList.asList());
  }

  public int size() {
    return tupleList.size();
  }

  record UnwrappingIterator<T>(Iterator<? extends ListEntry<T>> parentIterator)
      implements Iterator<T> {

    @Override
    public boolean hasNext() {
      return parentIterator.hasNext();
    }

    @Override
    public T next() {
      return parentIterator.next().getElement();
    }
  }
}
