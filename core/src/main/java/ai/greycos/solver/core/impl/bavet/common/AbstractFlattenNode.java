package ai.greycos.solver.core.impl.bavet.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleState;
import ai.greycos.solver.core.impl.util.CollectionUtils;

public abstract class AbstractFlattenNode<
        InTuple_ extends Tuple, OutTuple_ extends Tuple, FlattenedItem_>
    extends AbstractNode implements TupleLifecycle<InTuple_> {

  private final int flattenStoreIndex;
  private final StaticPropagationQueue<OutTuple_> propagationQueue;

  protected AbstractFlattenNode(
      int flattenStoreIndex, TupleLifecycle<OutTuple_> nextNodesTupleLifecycle) {
    this.flattenStoreIndex = flattenStoreIndex;
    this.propagationQueue = new StaticPropagationQueue<>(nextNodesTupleLifecycle);
  }

  @Override
  public StreamKind getStreamKind() {
    return StreamKind.FLATTEN;
  }

  @Override
  public final void insert(InTuple_ tuple) {
    if (tuple.getStore(flattenStoreIndex) != null) {
      throw new IllegalStateException(
          "Impossible state: the input for the tuple (%s) was already added in the tupleStore."
              .formatted(tuple));
    }
    var iterable = extractIterable(tuple);
    if (iterable instanceof Collection<FlattenedItem_> collection) {
      var size = collection.size();
      if (size == 0) {
        return;
      }
      var bagByItem = new FlattenBagByItem<FlattenedItem_, OutTuple_>(size);
      for (var item : collection) {
        addTuple(tuple, item, bagByItem);
      }
      tuple.setStore(flattenStoreIndex, bagByItem);
    } else {
      var iterator = iterable.iterator();
      if (!iterator.hasNext()) {
        return;
      }
      var bagByItem = new FlattenBagByItem<FlattenedItem_, OutTuple_>();
      while (iterator.hasNext()) {
        addTuple(tuple, iterator.next(), bagByItem);
      }
      tuple.setStore(flattenStoreIndex, bagByItem);
    }
  }

  private void addTuple(
      InTuple_ originalTuple,
      FlattenedItem_ item,
      FlattenBagByItem<FlattenedItem_, OutTuple_> bagByItem) {
    var outTupleBag = bagByItem.getBag(item);
    outTupleBag.add(
        () -> createTuple(originalTuple, outTupleBag.value),
        propagationQueue::insert,
        propagationQueue::update);
  }

  protected abstract OutTuple_ createTuple(InTuple_ originalTuple, FlattenedItem_ item);

  protected abstract Iterable<FlattenedItem_> extractIterable(InTuple_ tuple);

  @Override
  public final void update(InTuple_ tuple) {
    FlattenBagByItem<FlattenedItem_, OutTuple_> bagByItem = tuple.getStore(flattenStoreIndex);
    if (bagByItem == null) {
      insert(tuple);
      return;
    }

    bagByItem.resetAll();
    for (var item : extractIterable(tuple)) {
      addTuple(tuple, item, bagByItem);
    }
    bagByItem.getAllBags().removeIf(bag -> bag.removeExtras(this::removeTuple));
  }

  @Override
  public final void retract(InTuple_ tuple) {
    FlattenBagByItem<FlattenedItem_, OutTuple_> bagByItem = tuple.removeStore(flattenStoreIndex);
    if (bagByItem == null) {
      return;
    }
    bagByItem.applyToAll(this::removeTuple);
  }

  private void removeTuple(OutTuple_ outTuple) {
    var state = outTuple.getState();
    if (!state.isActive()) {
      throw new IllegalStateException(
          "Impossible state: The tuple (%s) is in an unexpected state (%s)."
              .formatted(outTuple, state));
    }
    propagationQueue.retract(
        outTuple, state == TupleState.CREATING ? TupleState.ABORTING : TupleState.DYING);
  }

  @Override
  public Propagator getPropagator() {
    return propagationQueue;
  }

  private record FlattenBagByItem<FlattenedItem_, OutTuple_>(
      Map<FlattenedItem_, FlattenItemBag<FlattenedItem_, OutTuple_>> delegate) {

    FlattenBagByItem() {
      this(new LinkedHashMap<>());
    }

    FlattenBagByItem(int size) {
      this(CollectionUtils.newLinkedHashMap(size));
    }

    Collection<FlattenItemBag<FlattenedItem_, OutTuple_>> getAllBags() {
      return delegate.values();
    }

    void applyToAll(Consumer<OutTuple_> retractConsumer) {
      delegate.forEach((key, value) -> value.clear(retractConsumer));
    }

    void resetAll() {
      delegate.forEach((key, value) -> value.reset());
    }

    FlattenItemBag<FlattenedItem_, OutTuple_> getBag(FlattenedItem_ key) {
      return delegate.computeIfAbsent(key, FlattenItemBag::new);
    }
  }

  private static final class FlattenItemBag<FlattenedItem_, OutTuple_> {

    private final FlattenedItem_ value;
    private final List<OutTuple_> outTupleList = new ArrayList<>();
    private int newCount = 0;

    FlattenItemBag(FlattenedItem_ value) {
      this.value = value;
    }

    void add(
        Supplier<OutTuple_> outTupleSupplier,
        Consumer<OutTuple_> insertConsumer,
        Consumer<OutTuple_> updateConsumer) {
      var listIndex = newCount++;
      if (newCount > outTupleList.size()) {
        var inserted = outTupleSupplier.get();
        outTupleList.add(inserted);
        insertConsumer.accept(inserted);
      } else {
        updateConsumer.accept(outTupleList.get(listIndex));
      }
    }

    boolean removeExtras(Consumer<OutTuple_> retractConsumer) {
      var size = outTupleList.size();
      for (var i = size - 1; i >= newCount; i--) {
        retractConsumer.accept(outTupleList.remove(i));
      }
      return newCount == 0;
    }

    void reset() {
      newCount = 0;
    }

    void clear(Consumer<OutTuple_> retractConsumer) {
      outTupleList.forEach(retractConsumer);
      outTupleList.clear();
      newCount = 0;
    }
  }
}
