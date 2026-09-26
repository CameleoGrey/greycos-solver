package greycos.solver.core.impl.neighborhood.stream.picking;

import java.util.Objects;

import greycos.solver.core.impl.neighborhood.stream.UniMoveStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.AbstractUniEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.neighborhood.stream.joiner.BiNeighborhoodsJoinerComber;
import greycos.solver.core.preview.api.neighborhood.UniMoveConstructor;
import greycos.solver.core.preview.api.neighborhood.stream.MoveStream;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import greycos.solver.core.preview.api.neighborhood.stream.joiner.BiNeighborhoodsJoiner;
import greycos.solver.core.preview.api.neighborhood.stream.picking.BiPickingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultUniPickingStream<Solution_, A>
    implements InnerUniPickingStream<Solution_, A> {

  private final UniLeftDataset<Solution_, A> dataset;

  public DefaultUniPickingStream(UniLeftDataset<Solution_, A> dataset) {
    this.dataset = Objects.requireNonNull(dataset);
  }

  @Override
  public UniLeftDataset<Solution_, A> getDataset() {
    return dataset;
  }

  @Override
  public <B> BiPickingStream<Solution_, A, B> pick(
      UniEnumeratingStream<Solution_, B> uniEnumeratingStream,
      BiNeighborhoodsJoiner<A, B>... joiners) {
    var comber = BiNeighborhoodsJoinerComber.<Solution_, A, B>comb(joiners);
    return new DefaultBiPickingStream<>(
        dataset,
        ((AbstractUniEnumeratingStream<Solution_, B>) uniEnumeratingStream)
            .asCachedDataset(comber));
  }

  @Override
  public MoveStream<Solution_> asMove(UniMoveConstructor<Solution_, A> moveConstructor) {
    return new UniMoveStream<>(dataset, Objects.requireNonNull(moveConstructor));
  }
}
