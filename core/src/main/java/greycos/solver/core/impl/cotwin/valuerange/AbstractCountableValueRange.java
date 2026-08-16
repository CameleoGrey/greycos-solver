package greycos.solver.core.impl.cotwin.valuerange;

import greycos.solver.core.api.cotwin.valuerange.CountableValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;

import org.jspecify.annotations.NullMarked;

/**
 * Abstract superclass for {@link CountableValueRange} (and therefore {@link ValueRange}).
 *
 * @see CountableValueRange
 * @see ValueRange
 * @see ValueRangeFactory
 */
@NullMarked
public abstract class AbstractCountableValueRange<T> extends AbstractValueRange<T>
    implements CountableValueRange<T> {}
