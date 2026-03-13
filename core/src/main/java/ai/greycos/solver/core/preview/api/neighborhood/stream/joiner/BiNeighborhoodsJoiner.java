package ai.greycos.solver.core.preview.api.neighborhood.stream.joiner;

import ai.greycos.solver.core.api.score.stream.Joiners;
import ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;

import org.jspecify.annotations.NullMarked;

/**
 * Created with {@link NeighborhoodsJoiners}. Used by {@link UniEnumeratingStream#join(Class,
 * BiNeighborhoodsJoiner[])}, ...
 *
 * @see Joiners
 */
@NullMarked
public interface BiNeighborhoodsJoiner<A, B> {

  BiNeighborhoodsJoiner<A, B> and(BiNeighborhoodsJoiner<A, B> otherJoiner);
}
