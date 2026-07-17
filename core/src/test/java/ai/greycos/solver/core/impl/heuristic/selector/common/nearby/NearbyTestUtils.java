package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

public final class NearbyTestUtils {

  public static SupplyManager mockSupplyManager(
      InnerScoreDirector<?, ?> scoreDirector, Supply nonNearbySupply) {
    SupplyManager supplyManager = mock(SupplyManager.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    when(supplyManager.demand(any()))
        .thenAnswer(
            invocation -> {
              Demand<?> demand = invocation.getArgument(0);
              if (demand instanceof NearbyDistanceMatrixDemand<?, ?>) {
                return demand.createExternalizedSupply(supplyManager);
              }
              if (nonNearbySupply != null) {
                return nonNearbySupply;
              }
              throw new AssertionError("Unexpected non-nearby demand: " + demand);
            });
    when(supplyManager.cancel(any())).thenReturn(true);
    return supplyManager;
  }

  private NearbyTestUtils() {}
}
