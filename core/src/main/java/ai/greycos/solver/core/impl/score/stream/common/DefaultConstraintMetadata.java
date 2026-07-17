package ai.greycos.solver.core.impl.score.stream.common;

import ai.greycos.solver.core.api.score.stream.ConstraintMetadata;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultConstraintMetadata(String id) implements ConstraintMetadata {}
