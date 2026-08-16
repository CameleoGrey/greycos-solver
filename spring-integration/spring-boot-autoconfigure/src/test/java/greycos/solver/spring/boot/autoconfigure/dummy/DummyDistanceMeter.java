package greycos.solver.spring.boot.autoconfigure.dummy;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

public class DummyDistanceMeter implements NearbyDistanceMeter<Object, Object> {
  @Override
  public double getNearbyDistance(Object origin, Object destination) {
    return 0;
  }
}
