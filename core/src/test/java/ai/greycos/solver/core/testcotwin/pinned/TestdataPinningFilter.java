package ai.greycos.solver.core.testcotwin.pinned;

import ai.greycos.solver.core.api.cotwin.entity.PinningFilter;

import org.jspecify.annotations.NonNull;

public class TestdataPinningFilter
    implements PinningFilter<TestdataPinnedSolution, TestdataPinnedEntity> {

  @Override
  public boolean accept(
      @NonNull TestdataPinnedSolution solution, @NonNull TestdataPinnedEntity entity) {
    return entity.isLocked();
  }
}
