package greycos.solver.core.impl.exhaustivesearch.decider;

import java.util.ArrayList;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.exhaustivesearch.event.ExhaustiveSearchPhaseLifecycleListener;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchLayer;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import greycos.solver.core.impl.exhaustivesearch.node.bounder.ScoreBounder;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.ManualEntityMimicRecorder;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.phase.scope.SolverLifecyclePoint;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.util.MutableInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class AbstractExhaustiveSearchDecider<
        Solution_, Score_ extends Score<Score_>>
    implements ExhaustiveSearchPhaseLifecycleListener<Solution_>
    permits BasicVariableExhaustiveSearchDecider,
        ListVariableExhaustiveSearchDecider,
        MixedVariableExhaustiveSearchDecider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AbstractExhaustiveSearchDecider.class);

  private final String logIndentation;
  protected final BestSolutionRecaller<Solution_> bestSolutionRecaller;
  private final PhaseTermination<Solution_> termination;
  protected final EntitySelector<Solution_> sourceEntitySelector;
  protected final ManualEntityMimicRecorder<Solution_> manualEntityMimicRecorder;
  private final MoveRepository<Solution_> moveRepository;
  protected final boolean scoreBounderEnabled;
  private final ScoreBounder<?> scoreBounder;

  // Flag that allows to accept partially initialized solutions
  protected boolean acceptUninitializedSolutions = false;
  private boolean assertMoveScoreFromScratch = false;
  private boolean assertExpectedUndoMoveScore = false;
  private boolean requireCompleteSolutionForPessimisticBound;

  AbstractExhaustiveSearchDecider(
      String logIndentation,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      PhaseTermination<Solution_> termination,
      EntitySelector<Solution_> sourceEntitySelector,
      ManualEntityMimicRecorder<Solution_> manualEntityMimicRecorder,
      MoveRepository<Solution_> moveRepository,
      boolean scoreBounderEnabled,
      ScoreBounder<?> scoreBounder) {
    this.logIndentation = logIndentation;
    this.bestSolutionRecaller = bestSolutionRecaller;
    this.termination = termination;
    this.sourceEntitySelector = sourceEntitySelector;
    this.manualEntityMimicRecorder = manualEntityMimicRecorder;
    this.moveRepository = moveRepository;
    this.scoreBounderEnabled = scoreBounderEnabled;
    this.scoreBounder = scoreBounder;
  }

  public void enableAssertions(EnvironmentMode environmentMode) {
    this.assertMoveScoreFromScratch = environmentMode.isFullyAsserted();
    this.assertExpectedUndoMoveScore = environmentMode.isIntrusivelyAsserted();
  }

  @SuppressWarnings("unchecked")
  public ScoreBounder<Score_> getScoreBounder() {
    return (ScoreBounder<Score_>) scoreBounder;
  }

  protected void enableAcceptUninitializedSolutions() {
    acceptUninitializedSolutions = true;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  public abstract void expandNode(ExhaustiveSearchStepScope<Solution_> stepScope);

  public abstract boolean isSolutionComplete(ExhaustiveSearchNode<Solution_> expandingNode);

  public abstract void restoreWorkingSolution(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      boolean assertWorkingSolutionScoreFromScratch,
      boolean assertExpectedWorkingSolutionScore);

  public abstract boolean isEntityReinitializable(Object entity);

  protected void expandNode(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> expandingNode,
      ExhaustiveSearchLayer moveLayer,
      MutableInt moveIndex) {
    var phaseScope = stepScope.getPhaseScope();
    for (var move : moveRepository) {
      var moveNode = new ExhaustiveSearchNode<>(moveLayer, expandingNode);
      moveIndex.increment();
      moveNode.setMove(move);
      doMove(stepScope, moveNode, isSolutionComplete(moveNode), false);
      phaseScope.addMoveEvaluationCount(move, 1);
      phaseScope.getSolverScope().checkYielding();
      if (termination.isPhaseTerminated(stepScope.getPhaseScope())) {
        break;
      }
    }
  }

  protected void doMove(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> moveNode,
      boolean isSolutionComplete,
      boolean skipMoveExecution) {
    var scoreDirector = stepScope.<Score_>getScoreDirector();
    var move = moveNode.getMove();
    if (!skipMoveExecution) {
      var undoMove =
          scoreDirector
              .getMoveDirector()
              .executeTemporaryProducingUndoMove(
                  move, score -> processMove(stepScope, moveNode, isSolutionComplete, score));
      moveNode.setUndoMove(undoMove);
    } else if (requireCompleteSolutionForPessimisticBound) {
      processMove(stepScope, moveNode, isSolutionComplete, scoreDirector.calculateScore());
    }
    var executionPoint = SolverLifecyclePoint.of(stepScope, moveNode.getTreeId());
    if (assertExpectedUndoMoveScore) {
      var startingStepScore = stepScope.<Score_>getStartingStepScore();
      // In BRUTE_FORCE a stepScore can be null because it was not calculated
      if (startingStepScore != null) {
        scoreDirector.assertExpectedUndoMoveScore(move, startingStepScore, executionPoint);
      }
    }
    var nodeScore = moveNode.getScore();
    LOGGER.trace(
        "{}        Move treeId ({}), score ({}), move ({}).",
        logIndentation,
        executionPoint.treeId(),
        nodeScore == null ? "null" : nodeScore,
        moveNode.getMove());
  }

  private void processMove(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> moveNode,
      boolean isSolutionComplete,
      InnerScore<Score_> score) {
    if (score.isStructurallyFlawed()) {
      moveNode.setScore(score);
      // A later assignment may remove a dependency loop, for example by inserting a list value
      // between the two elements involved. Keep partial nodes, without bounding their skipped
      // score.
      if (!isSolutionComplete) {
        moveNode.setOptimisticBound(null);
        stepScope.getPhaseScope().addExpandableNode(moveNode);
      }
      return;
    }
    if (!scoreBounderEnabled) {
      processMoverWithoutBounder(stepScope, moveNode, score, isSolutionComplete);
    } else {
      processMoverWithBounder(stepScope, moveNode, score, isSolutionComplete);
    }
  }

  private void processMoverWithoutBounder(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> moveNode,
      InnerScore<Score_> score,
      boolean isSolutionComplete) {
    var phaseScope = stepScope.getPhaseScope();
    if (isSolutionComplete) {
      moveNode.setScore(score);
      if (assertMoveScoreFromScratch) {
        phaseScope.assertWorkingScoreFromScratch(score, moveNode.getMove());
      }
      bestSolutionRecaller.processWorkingSolutionDuringMove(score, stepScope);
    } else {
      phaseScope.addExpandableNode(moveNode);
    }
  }

  private void processMoverWithBounder(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      ExhaustiveSearchNode<Solution_> moveNode,
      InnerScore<Score_> score,
      boolean isSolutionComplete) {
    var phaseScope = stepScope.getPhaseScope();
    moveNode.setScore(score);
    if (assertMoveScoreFromScratch) {
      phaseScope.assertWorkingScoreFromScratch(score, moveNode.getMove());
    }
    if (isSolutionComplete) {
      // There is no point in bounding a fully initialized score
      if (!requireCompleteSolutionForPessimisticBound || score.isFullyAssigned()) {
        phaseScope.registerPessimisticBound(score);
      }
      bestSolutionRecaller.processWorkingSolutionDuringMove(score, stepScope);
    } else {
      var scoreDirector = phaseScope.<Score_>getScoreDirector();
      var castScoreBounder = this.getScoreBounder();
      var optimisticBound = castScoreBounder.calculateOptimisticBound(scoreDirector, score);
      moveNode.setOptimisticBound(optimisticBound);
      var bestPessimisticBound = (InnerScore<Score_>) phaseScope.getBestPessimisticBound();
      if (bestPessimisticBound == null || optimisticBound.compareTo(bestPessimisticBound) > 0) {
        // It's still worth investigating this node further (no need to prune it)
        phaseScope.addExpandableNode(moveNode);
        if (!requireCompleteSolutionForPessimisticBound) {
          var pessimisticBound = castScoreBounder.calculatePessimisticBound(scoreDirector, score);
          phaseScope.registerPessimisticBound(pessimisticBound);
        }
      }
    }
  }

  protected void fillLayerList(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    var stepScope = new ExhaustiveSearchStepScope<>(phaseScope);
    sourceEntitySelector.stepStarted(stepScope);
    var entitySize = sourceEntitySelector.getSize();
    if (entitySize > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The entitySelector (%s) has an entitySize (%d) which is higher than Integer.MAX_VALUE."
              .formatted(sourceEntitySelector, entitySize));
    }
    var layerList = new ArrayList<ExhaustiveSearchLayer>((int) entitySize);
    var depth = 0;
    for (var entity : sourceEntitySelector) {
      var layer = new ExhaustiveSearchLayer(depth, entity);
      if (!isEntityReinitializable(entity)) {
        continue;
      }
      depth++;
      layerList.add(layer);
    }
    var lastLayer = new ExhaustiveSearchLayer(depth, null);
    layerList.add(lastLayer);
    sourceEntitySelector.stepEnded(stepScope);
    phaseScope.setLayerList(layerList);
  }

  protected void initStartNode(
      ExhaustiveSearchPhaseScope<Solution_> phaseScope, ExhaustiveSearchLayer layer) {
    var startLayer = layer == null ? phaseScope.getLayerList().getFirst() : layer;
    var startNode = new ExhaustiveSearchNode<Solution_>(startLayer, null);
    var complete = isStartNodeComplete(startNode);

    if (scoreBounderEnabled) {
      var scoreDirector = phaseScope.<Score_>getScoreDirector();
      var score = scoreDirector.calculateScore();
      startNode.setScore(score);
      ScoreBounder<Score_> bounder = getScoreBounder();
      if (requireCompleteSolutionForPessimisticBound) {
        // A sound partial solution need not have any sound completion. Only an actual incumbent
        // establishes a pruning threshold; business-score trends do not establish consistency.
        var bestScore = phaseScope.getSolverScope().getBestScore();
        phaseScope.setBestPessimisticBound(
            bestScore != null && bestScore.isFullyAssigned() && !bestScore.isStructurallyFlawed()
                ? bestScore
                : null);
      } else {
        phaseScope.setBestPessimisticBound(
            score.isStructurallyFlawed()
                ? null
                : complete ? score : bounder.calculatePessimisticBound(scoreDirector, score));
      }
      if (!score.isStructurallyFlawed()) {
        startNode.setOptimisticBound(
            complete ? score : bounder.calculateOptimisticBound(scoreDirector, score));
      }
    }
    if (!startLayer.isLastLayer()) {
      phaseScope.addExpandableNode(startNode);
    }
    phaseScope.getLastCompletedStepScope().setExpandingNode(startNode);
  }

  protected boolean isStartNodeComplete(ExhaustiveSearchNode<Solution_> startNode) {
    return startNode.getLayer().isLastLayer();
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    sourceEntitySelector.solvingStarted(solverScope);
    moveRepository.solvingStarted(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    sourceEntitySelector.solvingEnded(solverScope);
    moveRepository.solvingEnded(solverScope);
  }

  @Override
  public void phaseStarted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    var solutionDescriptor = phaseScope.getSolutionDescriptor();
    requireCompleteSolutionForPessimisticBound =
        !solutionDescriptor.hasAnyShadowVariablesInconsistentMember()
            && !solutionDescriptor.getDeclarativeShadowVariableDescriptors().isEmpty();
    sourceEntitySelector.phaseStarted(phaseScope);
    moveRepository.phaseStarted(phaseScope);
    fillLayerList(phaseScope);
    initStartNode(phaseScope, null);
  }

  @Override
  public void phaseEnded(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    sourceEntitySelector.phaseEnded(phaseScope);
    moveRepository.phaseEnded(phaseScope);
  }

  @Override
  public void stepStarted(ExhaustiveSearchStepScope<Solution_> stepScope) {
    moveRepository.stepStarted(stepScope);
  }

  @Override
  public void stepEnded(ExhaustiveSearchStepScope<Solution_> stepScope) {
    moveRepository.stepEnded(stepScope);
  }

  public void afterStep(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    // Most deciders do not switch search stages after the completed step has been recorded.
  }

  public boolean onSearchExhausted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    return false;
  }

  public void releaseSearchState() {
    // Only a mixed search retains suspended queues outside the phase scope.
  }
}
