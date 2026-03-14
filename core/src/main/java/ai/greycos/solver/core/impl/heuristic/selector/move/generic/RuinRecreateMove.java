package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.move.Move;
import ai.greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

public class RuinRecreateMove<Solution_> extends AbstractMove<Solution_> {

  private final GenuineVariableDescriptor<Solution_> genuineVariableDescriptor;
  private final RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      constructionHeuristicPhaseBuilder;
  private final SolverScope<Solution_> solverScope;
  private final List<Object> ruinedEntityList;
  private final Set<Object> affectedValueSet;

  private Object[] recordedNewValues;

  public RuinRecreateMove(
      GenuineVariableDescriptor<Solution_> genuineVariableDescriptor,
      RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> constructionHeuristicPhaseBuilder,
      SolverScope<Solution_> solverScope,
      List<Object> ruinedEntityList,
      Set<Object> affectedValueSet) {
    this.genuineVariableDescriptor = genuineVariableDescriptor;
    this.ruinedEntityList = ruinedEntityList;
    this.affectedValueSet = affectedValueSet;
    this.constructionHeuristicPhaseBuilder = constructionHeuristicPhaseBuilder;
    this.solverScope = solverScope;
    this.recordedNewValues = null;
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    recordedNewValues = new Object[ruinedEntityList.size()];

    for (var ruinedEntity : ruinedEntityList) {
      if (scoreDirector instanceof VariableDescriptorAwareScoreDirector<Solution_> variableAware) {
        variableAware.beforeVariableChanged(genuineVariableDescriptor, ruinedEntity);
      } else {
        scoreDirector.beforeVariableChanged(
            ruinedEntity, genuineVariableDescriptor.getVariableName());
      }
      genuineVariableDescriptor.setValue(ruinedEntity, null);
      if (scoreDirector instanceof VariableDescriptorAwareScoreDirector<Solution_> variableAware) {
        variableAware.afterVariableChanged(genuineVariableDescriptor, ruinedEntity);
      } else {
        scoreDirector.afterVariableChanged(
            ruinedEntity, genuineVariableDescriptor.getVariableName());
      }
    }
    scoreDirector.triggerVariableListeners();

    var backingScoreDirector =
        scoreDirector
                instanceof
                VariableChangeRecordingScoreDirector<Solution_, ?>
                    variableChangeRecordingScoreDirector
            ? variableChangeRecordingScoreDirector.getBacking()
            : (ai.greycos.solver.core.impl.score.director.InnerScoreDirector<Solution_, ?>)
                scoreDirector;

    var constructionHeuristicPhase =
        (RuinRecreateConstructionHeuristicPhase<Solution_>)
            constructionHeuristicPhaseBuilder
                .ensureThreadSafe(backingScoreDirector)
                .withElementsToRecreate(ruinedEntityList)
                .build();

    var nestedSolverScope = new SolverScope<Solution_>(solverScope.getClock());
    nestedSolverScope.setSolver(solverScope.getSolver());
    nestedSolverScope.setScoreDirector(backingScoreDirector);
    constructionHeuristicPhase.solvingStarted(nestedSolverScope);
    constructionHeuristicPhase.solve(nestedSolverScope);
    constructionHeuristicPhase.solvingEnded(nestedSolverScope);
    scoreDirector.triggerVariableListeners();

    for (var i = 0; i < ruinedEntityList.size(); i++) {
      recordedNewValues[i] = genuineVariableDescriptor.getValue(ruinedEntityList.get(i));
    }
  }

  @Override
  public Collection<?> getPlanningEntities() {
    return ruinedEntityList;
  }

  @Override
  public Collection<?> getPlanningValues() {
    return affectedValueSet;
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public Move<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    var rebasedRuinedEntityList = rebaseList(ruinedEntityList, destinationScoreDirector);
    var rebasedAffectedValueSet = rebaseSet(affectedValueSet, destinationScoreDirector);
    return new RuinRecreateMove<>(
        genuineVariableDescriptor,
        constructionHeuristicPhaseBuilder,
        solverScope,
        rebasedRuinedEntityList,
        rebasedAffectedValueSet);
  }

  protected GenuineVariableDescriptor<Solution_> getGenuineVariableDescriptor() {
    return genuineVariableDescriptor;
  }

  protected RuinRecreateConstructionHeuristicPhaseBuilder<Solution_>
      getConstructionHeuristicPhaseBuilder() {
    return constructionHeuristicPhaseBuilder;
  }

  protected SolverScope<Solution_> getSolverScope() {
    return solverScope;
  }

  protected List<Object> getRuinedEntityList() {
    return ruinedEntityList;
  }

  protected Set<Object> getAffectedValueSet() {
    return affectedValueSet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RuinRecreateMove<?> that)) return false;
    return Objects.equals(genuineVariableDescriptor, that.genuineVariableDescriptor)
        && Objects.equals(ruinedEntityList, that.ruinedEntityList)
        && Objects.equals(affectedValueSet, that.affectedValueSet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(genuineVariableDescriptor, ruinedEntityList, affectedValueSet);
  }

  @Override
  public String toString() {
    return "RuinMove{"
        + "entities="
        + ruinedEntityList
        + ", newValues="
        + ((recordedNewValues != null) ? Arrays.toString(recordedNewValues) : "?")
        + '}';
  }
}
