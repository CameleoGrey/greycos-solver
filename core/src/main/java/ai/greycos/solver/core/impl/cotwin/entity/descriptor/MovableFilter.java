package ai.greycos.solver.core.impl.cotwin.entity.descriptor;

import java.util.function.BiPredicate;

@FunctionalInterface
interface MovableFilter<Solution_> extends BiPredicate<Solution_, Object> {

  default MovableFilter<Solution_> and(MovableFilter<Solution_> other) {
    return (solution, entity) -> test(solution, entity) && other.test(solution, entity);
  }
}
