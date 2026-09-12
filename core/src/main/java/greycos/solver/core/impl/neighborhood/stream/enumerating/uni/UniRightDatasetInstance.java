package greycos.solver.core.impl.neighborhood.stream.enumerating.uni;

import java.util.function.Function;

import greycos.solver.core.impl.bavet.common.index.IndexerFactory;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractRightDatasetInstance;
import greycos.solver.core.preview.api.neighborhood.stream.function.BiNeighborhoodsPredicate;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class UniRightDatasetInstance<Solution_, A, B>
    extends AbstractRightDatasetInstance<Solution_, B> {

  private final Function<@Nullable A, Object> leftFactCompositeKeyExtractor;
  private final @Nullable BiNeighborhoodsPredicate<Solution_, A, B> filter;

  public UniRightDatasetInstance(
      AbstractDataset<Solution_> parent,
      IndexerFactory<B> indexerFactory,
      @Nullable BiNeighborhoodsPredicate<Solution_, A, B> filter,
      int compositeKeyStoreIndex,
      int rightMostPositionStoreIndex) {
    super(
        parent,
        indexerFactory.buildRightKeysExtractor(),
        compositeKeyStoreIndex,
        rightMostPositionStoreIndex,
        indexerFactory.buildIndexer(false));
    this.leftFactCompositeKeyExtractor = indexerFactory.buildUniLeftFactKeysExtractor();
    this.filter = filter;
  }

  public Object produceCompositeKey(UniTuple<A> leftTuple) {
    return produceCompositeKey(leftTuple.getA());
  }

  public Object produceCompositeKey(@Nullable A a) {
    return leftFactCompositeKeyExtractor.apply(a);
  }

  public @Nullable BiNeighborhoodsPredicate<Solution_, A, B> getFilter() {
    return filter;
  }
}
