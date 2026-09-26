package greycos.solver.core.impl.heuristic.selector.common.nearby;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.supply.Demand;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

public final class NearbyTestUtils {

  public static <Solution_> SupplyManager mockSupplyManager(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      ListVariableState<Solution_, Object, Object> listVariableState) {
    SupplyManager supplyManager = mock(SupplyManager.class);
    when(scoreDirector.getSupplyManager()).thenReturn(supplyManager);
    if (listVariableState != null) {
      when(scoreDirector.getListVariableState(any())).thenReturn(listVariableState);
    }
    when(supplyManager.demand(any()))
        .thenAnswer(
            invocation -> {
              Demand<?> demand = invocation.getArgument(0);
              if (demand instanceof NearbyDistanceMatrixDemand<?, ?>) {
                return demand.createExternalizedSupply(supplyManager);
              }
              throw new AssertionError("Unexpected non-nearby demand: " + demand);
            });
    when(supplyManager.cancel(any())).thenReturn(true);
    return supplyManager;
  }

  private NearbyTestUtils() {}
}
