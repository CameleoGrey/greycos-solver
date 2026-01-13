package ai.greycos.solver.core.testcotwin.pinned.unassignedvar;

import ai.greycos.solver.core.api.cotwin.entity.PinningFilter;

import org.jspecify.annotations.NonNull;

public class TestdataAllowsUnassignedPinningFilter
    implements PinningFilter<
        TestdataPinnedAllowsUnassignedSolution, TestdataPinnedAllowsUnassignedEntity> {

  @Override
  public boolean accept(
      @NonNull TestdataPinnedAllowsUnassignedSolution solution,
      @NonNull TestdataPinnedAllowsUnassignedEntity entity) {
    return entity.isLocked();
  }
}
