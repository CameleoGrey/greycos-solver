package greycos.solver.core.impl.neighborhood.stream.sampling;

import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.preview.api.neighborhood.stream.sampling.SamplingStream;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerSamplingStream<Solution_> extends SamplingStream {

  AbstractDataset<Solution_> getDataset();
}
