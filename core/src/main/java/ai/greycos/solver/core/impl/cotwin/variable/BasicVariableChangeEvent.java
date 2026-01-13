package ai.greycos.solver.core.impl.cotwin.variable;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record BasicVariableChangeEvent<Entity_>(Entity_ entity) implements ChangeEvent {}
