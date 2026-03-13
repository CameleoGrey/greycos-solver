package ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating.function;

import ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import ai.greycos.solver.core.preview.api.neighborhood.stream.function.UniNeighborhoodsPredicate;

import org.jspecify.annotations.NullMarked;

/**
 * A filter that can be applied to a {@link UniEnumeratingStream} to filter out pairs of data,
 * optionally using {@link SolutionView} to query for solution state.
 *
 * @param <Solution_> the type of the solution
 * @param <A> the type of the first parameter
 */
@NullMarked
public interface UniEnumeratingFilter<Solution_, A>
    extends UniNeighborhoodsPredicate<Solution_, A> {}
