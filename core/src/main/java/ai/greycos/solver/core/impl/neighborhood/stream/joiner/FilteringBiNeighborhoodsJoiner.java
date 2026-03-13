package ai.greycos.solver.core.impl.neighborhood.stream.joiner;

import ai.greycos.solver.core.preview.api.neighborhood.stream.function.BiNeighborhoodsPredicate;
import ai.greycos.solver.core.preview.api.neighborhood.stream.joiner.BiNeighborhoodsJoiner;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record FilteringBiNeighborhoodsJoiner<Solution_, A, B>(
    BiNeighborhoodsPredicate<Solution_, A, B> filter) implements BiNeighborhoodsJoiner<A, B> {

  @Override
  public FilteringBiNeighborhoodsJoiner<Solution_, A, B> and(
      BiNeighborhoodsJoiner<A, B> otherJoiner) {
    FilteringBiNeighborhoodsJoiner<Solution_, A, B> castJoiner =
        (FilteringBiNeighborhoodsJoiner<Solution_, A, B>) otherJoiner;
    return new FilteringBiNeighborhoodsJoiner<>(filter.and(castJoiner.filter()));
  }
}
