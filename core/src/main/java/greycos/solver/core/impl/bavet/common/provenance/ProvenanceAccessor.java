package greycos.solver.core.impl.bavet.common.provenance;

import java.util.Objects;

import greycos.solver.core.impl.bavet.common.tuple.Tuple;

import org.jspecify.annotations.NullMarked;

/**
 * A provenance slot belonging to a logical stream, which may share a physical tuple with others.
 */
@NullMarked
public record ProvenanceAccessor(int storeIndex) {

  public LineageHandle get(Tuple tuple) {
    return Objects.requireNonNull(
        tuple.getStore(storeIndex), "The tuple has no original-source lineage in this stream.");
  }

  public void set(Tuple tuple, LineageHandle handle) {
    tuple.setStore(storeIndex, Objects.requireNonNull(handle));
  }

  public void clear(Tuple tuple) {
    tuple.removeStore(storeIndex);
  }
}
