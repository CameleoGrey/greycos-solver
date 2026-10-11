package greycos.solver.math.impl.score;

import greycos.solver.core.api.score.stream.ConstraintJustification;

import org.jspecify.annotations.NullMarked;

/** Immutable scalar snapshot of one mathematical constraint match. */
@NullMarked
public record MathRowJustification(
    int rowId, String name, long activity, long bound, long residual, long violation)
    implements ConstraintJustification {}
