package ai.greycos.solver.core.api.cotwin.valuerange;

import org.jspecify.annotations.NullMarked;

/**
 * @deprecated All value ranges are countable now. Use {@link ValueRange} instead.
 * @see ValueRange
 */
@Deprecated(forRemoval = true, since = "1.1.0")
@NullMarked
public interface CountableValueRange<T> extends ValueRange<T> {}
