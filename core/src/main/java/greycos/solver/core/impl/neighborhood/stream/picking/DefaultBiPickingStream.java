package greycos.solver.core.impl.neighborhood.stream.picking;

import java.util.Objects;

import greycos.solver.core.impl.neighborhood.stream.BiMoveStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniLeftDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.UniRightDataset;
import greycos.solver.core.preview.api.neighborhood.BiMoveConstructor;
import greycos.solver.core.preview.api.neighborhood.stream.MoveStream;
import greycos.solver.core.preview.api.neighborhood.stream.picking.BiPickingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultBiPickingStream<Solution_, A, B>
    implements BiPickingStream<Solution_, A, B> {

  private final UniLeftDataset<Solution_, A> leftDataset;
  private final UniRightDataset<Solution_, A, B> rightDataset;

  public DefaultBiPickingStream(
      UniLeftDataset<Solution_, A> leftDataset, UniRightDataset<Solution_, A, B> rightDataset) {
    this.leftDataset = Objects.requireNonNull(leftDataset);
    this.rightDataset = Objects.requireNonNull(rightDataset);
  }

  @Override
  public MoveStream<Solution_> asMove(BiMoveConstructor<Solution_, A, B> moveConstructor) {
    return new BiMoveStream<>(leftDataset, rightDataset, Objects.requireNonNull(moveConstructor));
  }
}
