package ai.greycos.solver.core.testdomain.pinned.unassignedvar;

import ai.greycos.solver.core.api.domain.entity.PinningFilter;

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
