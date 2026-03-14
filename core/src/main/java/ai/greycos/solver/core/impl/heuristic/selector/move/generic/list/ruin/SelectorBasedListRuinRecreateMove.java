package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin;

import java.util.List;
import java.util.Set;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedListRuinRecreateMove<Solution_> extends ListRuinRecreateMove<Solution_> {

  public SelectorBasedListRuinRecreateMove(
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      SolverScope<Solution_> solverScope,
      List<Object> ruinedValueList,
      Set<Object> affectedEntitySet) {
    super(
        listVariableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        ruinedValueList,
        affectedEntitySet);
  }

  @Override
  public Move<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    var rebasedListVariableDescriptor =
        ((InnerScoreDirector<Solution_, ?>) destinationScoreDirector)
            .getSolutionDescriptor()
            .getListVariableDescriptor();
    return new SelectorBasedListRuinRecreateMove<>(
        rebasedListVariableDescriptor,
        getConstructionHeuristicPhaseBuilder(),
        getSolverScope(),
        rebaseList(getRuinedValueList(), destinationScoreDirector),
        rebaseSet(getAffectedEntitySet(), destinationScoreDirector));
  }
}
