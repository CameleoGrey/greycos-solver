package greycos.solver.core.impl.heuristic.selector.move.generic;

import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;

public final class MultistageMoveSelectorFactory<Solution_>
    extends AbstractMultistageMoveSelectorFactory<Solution_, MultistageMoveSelectorConfig> {

  public MultistageMoveSelectorFactory(MultistageMoveSelectorConfig config) {
    super(
        config,
        config.getEntityClass(),
        config.getVariableName(),
        config.getStageProviderClass(),
        config.determineCandidateCountLimit(),
        config.determineProbeCountLimit(),
        false);
  }
}
