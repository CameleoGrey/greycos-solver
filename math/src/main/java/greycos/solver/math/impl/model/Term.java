package greycos.solver.math.impl.model;

import org.jspecify.annotations.NullMarked;

/** One nonzero normalized coefficient, joined to a decision by its model-local ID. */
@NullMarked
public record Term(Row row, int variableId, long coefficient) {}
