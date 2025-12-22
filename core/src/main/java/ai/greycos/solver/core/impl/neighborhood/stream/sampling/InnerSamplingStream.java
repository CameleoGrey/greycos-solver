package ai.greycos.solver.core.impl.neighborhood.stream.sampling;

import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import ai.greycos.solver.core.preview.api.neighborhood.stream.sampling.SamplingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerSamplingStream<Solution_> extends SamplingStream {

  AbstractDataset<Solution_> getDataset();
}
