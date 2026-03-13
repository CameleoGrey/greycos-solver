package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin;

import java.util.List;
import java.util.Set;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
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
}
