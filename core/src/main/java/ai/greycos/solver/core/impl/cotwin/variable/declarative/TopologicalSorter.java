package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Comparator;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record TopologicalSorter(
    UnaryOperator<@Nullable Object> successor,
    Comparator<Object> comparator,
    UnaryOperator<Object> key) {}
