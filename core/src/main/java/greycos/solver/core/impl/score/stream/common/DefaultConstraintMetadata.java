package greycos.solver.core.impl.score.stream.common;

import greycos.solver.core.api.score.stream.ConstraintMetadata;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultConstraintMetadata(String id) implements ConstraintMetadata {}
