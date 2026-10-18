package greycos.solver.core.impl.bavet.common.provenance;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Provenance accessors configured once while building an explicitly enabled analysis session. */
@NullMarked
public record TupleProvenance(
    @Nullable ProvenanceAccessor left,
    @Nullable ProvenanceAccessor right,
    ProvenanceAccessor output) {}
