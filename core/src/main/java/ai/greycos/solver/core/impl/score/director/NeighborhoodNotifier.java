package ai.greycos.solver.core.impl.score.director;

import java.util.function.Consumer;

import ai.greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;

public final class NeighborhoodNotifier<Solution_> implements Consumer<Object> {

  private boolean tracking = false;
  private NeighborhoodsBasedMoveRepository<Solution_> moveRepository = null;

  public void setTracking(boolean tracking) {
    this.tracking = tracking;
  }

  public void setMoveRepository(NeighborhoodsBasedMoveRepository<Solution_> moveRepository) {
    this.moveRepository = moveRepository;
  }

  @Override
  public void accept(Object entity) {
    if (!tracking || moveRepository == null) {
      return;
    }
    moveRepository.update(entity);
  }
}
