package greycos.solver.core.impl.geneticalgorithm;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/** A genuine assignment and its cached recipient range in the current workspace. */
@NullMarked
public record GeneticAlgorithmSlot<Solution_>(
    Object entity,
    BasicVariableDescriptor<Solution_> variableDescriptor,
    ValueRange<Object> valueRange,
    boolean movable) {}
