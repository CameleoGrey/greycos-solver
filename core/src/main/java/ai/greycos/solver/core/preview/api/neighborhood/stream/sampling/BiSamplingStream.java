package ai.greycos.solver.core.preview.api.neighborhood.stream.sampling;

import ai.greycos.solver.core.preview.api.neighborhood.BiMoveConstructor;
import ai.greycos.solver.core.preview.api.neighborhood.stream.MoveStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface BiSamplingStream<Solution_, A, B> extends SamplingStream {

  MoveStream<Solution_> asMove(BiMoveConstructor<Solution_, A, B> moveConstructor);
}
