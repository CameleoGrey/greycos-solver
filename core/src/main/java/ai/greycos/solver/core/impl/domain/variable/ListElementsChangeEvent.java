package ai.greycos.solver.core.impl.domain.variable;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ListElementsChangeEvent<Entity_>(
    Entity_ entity, int changeStartIndexInclusive, int changeEndIndexExclusive)
    implements ChangeEvent {}
