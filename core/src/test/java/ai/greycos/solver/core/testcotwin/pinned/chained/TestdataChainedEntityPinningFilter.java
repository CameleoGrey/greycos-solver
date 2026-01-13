package ai.greycos.solver.core.testcotwin.pinned.chained;

import ai.greycos.solver.core.api.cotwin.entity.PinningFilter;

import org.jspecify.annotations.NonNull;

public class TestdataChainedEntityPinningFilter
    implements PinningFilter<TestdataPinnedChainedSolution, TestdataPinnedChainedEntity> {

  @Override
  public boolean accept(
      @NonNull TestdataPinnedChainedSolution scoreDirector,
      @NonNull TestdataPinnedChainedEntity entity) {
    return entity.isPinned();
  }
}
