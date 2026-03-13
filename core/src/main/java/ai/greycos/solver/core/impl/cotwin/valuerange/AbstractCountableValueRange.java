package ai.greycos.solver.core.impl.cotwin.valuerange;

import ai.greycos.solver.core.api.cotwin.valuerange.CountableValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;

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
