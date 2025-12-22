package ai.greycos.solver.core.impl.domain.variable;

public sealed interface ChangeEvent permits BasicVariableChangeEvent, ListElementsChangeEvent {}
