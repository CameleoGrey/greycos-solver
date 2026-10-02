package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.impl.heuristic.selector.move.generic.AbstractMultistageMoveSelectorFactory;

public final class ListMultistageMoveSelectorFactory<Solution_>
    extends AbstractMultistageMoveSelectorFactory<Solution_, ListMultistageMoveSelectorConfig> {

  public ListMultistageMoveSelectorFactory(ListMultistageMoveSelectorConfig config) {
    super(
        config,
        config.getEntityClass(),
        config.getVariableName(),
        config.getStageProviderClass(),
        config.determineCandidateCountLimit(),
        config.determineProbeCountLimit(),
        true);
  }
}
