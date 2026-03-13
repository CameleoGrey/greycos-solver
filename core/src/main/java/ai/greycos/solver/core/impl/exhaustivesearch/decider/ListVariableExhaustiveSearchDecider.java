package ai.greycos.solver.core.impl.exhaustivesearch.decider;

import java.util.ArrayList;
import java.util.Collections;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import ai.greycos.solver.core.impl.exhaustivesearch.node.bounder.ScoreBounder;
import ai.greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import ai.greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import ai.greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.greycos.solver.core.impl.heuristic.selector.entity.mimic.ManualEntityMimicRecorder;
import ai.greycos.solver.core.impl.neighborhood.MoveRepository;
import ai.greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import ai.greycos.solver.core.impl.solver.termination.PhaseTermination;
import ai.greycos.solver.core.impl.util.MutableInt;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.move.builtin.Moves;

public final class ListVariableExhaustiveSearchDecider<Solution_, Score_ extends Score<Score_>>
    extends AbstractExhaustiveSearchDecider<Solution_, Score_> {

  private ListVariableStateSupply<Solution_, ?, ?> listVariableState;

  public ListVariableExhaustiveSearchDecider(
      String logIndentation,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      PhaseTermination<Solution_> termination,
      EntitySelector<Solution_> sourceEntitySelector,
      ManualEntityMimicRecorder<Solution_> manualEntityMimicRecorder,
      MoveRepository<Solution_> moveRepository,
      boolean scoreBounderEnabled,
      ScoreBounder<?> scoreBounder) {
    super(
        logIndentation,
        bestSolutionRecaller,
        termination,
        sourceEntitySelector,
        manualEntityMimicRecorder,
        moveRepository,
        scoreBounderEnabled,
        scoreBounder);
  }

  @Override
  public void expandNode(ExhaustiveSearchStepScope<Solution_> stepScope) {
    var phaseScope = stepScope.getPhaseScope();
    var expandingNode = stepScope.getExpandingNode();
    var moveIndex = new MutableInt(0);
    if (listVariableState.getUnassignedCount() == 0) {
      moveIndex.increment();
      doMove(stepScope, expandingNode, true, true);
      phaseScope.addMoveEvaluationCount(expandingNode.getMove(), 1);
    } else {
      for (var i = expandingNode.getLayer().getDepth(); i < phaseScope.getLayerList().size(); i++) {
        var layer = phaseScope.getLayerList().get(i);
        if (layer.isLastLayer()) {
          break;
        }
        manualEntityMimicRecorder.setRecordedEntity(layer.getEntity());
        expandNode(stepScope, expandingNode, layer, moveIndex);
        stepScope.setSelectedMoveCount(moveIndex.longValue());
      }
    }
  }

  @Override
  public boolean isSolutionComplete(ExhaustiveSearchNode expandingNode) {
    return listVariableState.getUnassignedCount() <= 1;
  }

  @Override
  public boolean isEntityReinitializable(Object entity) {
    return true;
  }

  @Override
  public void restoreWorkingSolution(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      boolean assertWorkingSolutionScoreFromScratch,
      boolean assertExpectedWorkingSolutionScore) {
    var phaseScope = stepScope.getPhaseScope();
    var undoNode = phaseScope.getLastCompletedStepScope().getExpandingNode();
    var unassignMoveList = new ArrayList<Move<Solution_>>();
    while (undoNode.getUndoMove() != null) {
      unassignMoveList.add(undoNode.getUndoMove());
      undoNode = undoNode.getParent();
    }
    var assignNode = stepScope.getExpandingNode();
    var assignMoveList = new ArrayList<Move<Solution_>>();
    while (assignNode.getMove() != null) {
      assignMoveList.add(assignNode.getMove());
      assignNode = assignNode.getParent();
    }
    Collections.reverse(assignMoveList);
    var allMoves = new ArrayList<Move<Solution_>>(unassignMoveList.size() + assignMoveList.size());
    allMoves.addAll(unassignMoveList);
    allMoves.addAll(assignMoveList);
    if (allMoves.isEmpty()) {
      return;
    }
    var compositeMove = Moves.compose(allMoves);
    phaseScope.getScoreDirector().executeMove(compositeMove);
    var score = phaseScope.<Score_>calculateScore();
    stepScope.getExpandingNode().setScore(score);
    phaseScope.getSolutionDescriptor().setScore(phaseScope.getWorkingSolution(), score.raw());
  }

  @Override
  public void phaseStarted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    super.phaseStarted(phaseScope);
    var listVariableDescriptor = phaseScope.getSolutionDescriptor().getListVariableDescriptor();
    this.listVariableState =
        phaseScope
            .getSolverScope()
            .getScoreDirector()
            .getListVariableStateSupply(listVariableDescriptor);
  }

  @Override
  public void phaseEnded(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    super.phaseEnded(phaseScope);
    this.listVariableState = null;
  }
}
