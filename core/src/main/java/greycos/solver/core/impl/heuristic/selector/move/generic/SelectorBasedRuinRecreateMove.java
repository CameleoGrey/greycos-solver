package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.List;
import java.util.SequencedSet;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedRuinRecreateMove<Solution_> extends RuinRecreateMove<Solution_> {

  public SelectorBasedRuinRecreateMove(
      GenuineVariableDescriptor<Solution_> genuineVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      SolverScope<Solution_> solverScope,
      List<Object> ruinedEntityList,
      SequencedSet<Object> affectedValueSet) {
    super(
        genuineVariableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        ruinedEntityList,
        affectedValueSet);
  }

  public SelectorBasedRuinRecreateMove(
      GenuineVariableDescriptor<Solution_> genuineVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      SolverScope<Solution_> solverScope,
      List<Object> ruinedEntityList,
      SequencedSet<Object> affectedValueSet,
      long randomSeed) {
    super(
        genuineVariableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        ruinedEntityList,
        affectedValueSet,
        randomSeed);
  }

  @Override
  public SelectorBasedRuinRecreateMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    return new SelectorBasedRuinRecreateMove<>(
        getGenuineVariableDescriptor(),
        getConstructionHeuristicPhaseBuilder(),
        getSolverScope(),
        rebaseList(getRuinedEntityList(), destinationScoreDirector),
        rebaseSet(getAffectedValueSet(), destinationScoreDirector),
        getRandomSeed());
  }
}
