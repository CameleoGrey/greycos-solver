package greycos.solver.core.impl.bavet.common.provenance;

/** Internal build-time opt-in; disabled nodes allocate no provenance state or tuple stores. */
public interface ProvenanceNode {

  void setProvenance(TupleProvenance provenance);
}
