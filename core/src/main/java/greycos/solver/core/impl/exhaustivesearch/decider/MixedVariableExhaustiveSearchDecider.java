package greycos.solver.core.impl.exhaustivesearch.decider;

import java.util.Arrays;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.variable.declarative.VariableSourceReference;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchLayer;
import greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

public final class MixedVariableExhaustiveSearchDecider<Solution_, Score_ extends Score<Score_>>
    extends AbstractExhaustiveSearchDecider<Solution_, Score_> {

  private final AbstractExhaustiveSearchDecider<Solution_, Score_> basicVariableDecider;
  private final AbstractExhaustiveSearchDecider<Solution_, Score_> listVariableDecider;

  private AbstractExhaustiveSearchDecider<Solution_, Score_> currentDecider;
  private boolean resetLastStep = false;
  private boolean searchListForEveryBasicAssignment;
  private boolean startListAfterStep;
  private boolean listPhaseStarted;
  private SortedSet<ExhaustiveSearchNode> basicNodeQueue;
  private List<ExhaustiveSearchLayer> basicLayerList;
  private ExhaustiveSearchNode<Solution_> basicTerminalNode;
  private ExhaustiveSearchNode<Solution_> listStartNode;

  @SuppressWarnings("unchecked")
  public MixedVariableExhaustiveSearchDecider(
      AbstractExhaustiveSearchDecider<Solution_, ? extends Score<?>> basicVariableDecider,
      AbstractExhaustiveSearchDecider<Solution_, ? extends Score<?>> listVariableDecider) {
    super(null, null, null, null, null, null, false, null);
    this.basicVariableDecider =
        (AbstractExhaustiveSearchDecider<Solution_, Score_>) basicVariableDecider;
    this.listVariableDecider =
        (AbstractExhaustiveSearchDecider<Solution_, Score_>) listVariableDecider;
    this.currentDecider = this.basicVariableDecider;
    this.currentDecider.enableAcceptUninitializedSolutions();
  }

  @Override
  public void expandNode(ExhaustiveSearchStepScope<Solution_> stepScope) {
    if (searchListForEveryBasicAssignment
        && currentDecider == basicVariableDecider
        && stepScope.getExpandingNode().getLayer().isLastLayer()) {
      var scoreDirector = stepScope.getScoreDirector();
      var listVariableDescriptor =
          stepScope.getPhaseScope().getSolutionDescriptor().getListVariableDescriptor();
      if (scoreDirector.getListVariableState(listVariableDescriptor).getUnassignedCount() == 0) {
        basicVariableDecider.doMove(stepScope, stepScope.getExpandingNode(), true, true);
      } else {
        startListAfterStep = true;
      }
      return;
    }
    currentDecider.expandNode(stepScope);
  }

  @Override
  public boolean isSolutionComplete(ExhaustiveSearchNode expandingNode) {
    return currentDecider.isSolutionComplete(expandingNode);
  }

  @Override
  public void restoreWorkingSolution(
      ExhaustiveSearchStepScope<Solution_> stepScope,
      boolean assertWorkingSolutionScoreFromScratch,
      boolean assertExpectedWorkingSolutionScore) {
    currentDecider.restoreWorkingSolution(
        stepScope, assertWorkingSolutionScoreFromScratch, assertExpectedWorkingSolutionScore);
  }

  @Override
  public boolean isEntityReinitializable(Object entity) {
    return currentDecider.isEntityReinitializable(entity);
  }

  @Override
  protected void fillLayerList(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    currentDecider.fillLayerList(phaseScope);
  }

  @Override
  protected void initStartNode(
      ExhaustiveSearchPhaseScope<Solution_> phaseScope, ExhaustiveSearchLayer layer) {
    currentDecider.initStartNode(phaseScope, layer);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    basicVariableDecider.solvingStarted(solverScope);
    listVariableDecider.solvingStarted(solverScope);
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      basicVariableDecider.solvingEnded(solverScope);
      listVariableDecider.solvingEnded(solverScope);
    } finally {
      clearSearchState();
    }
  }

  @Override
  public void phaseStarted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    var descriptor = phaseScope.getSolutionDescriptor();
    // A dependency loop requires a declarative shadow to read another declarative shadow.
    // Keep staged search when every source is a genuine variable or a built-in shadow.
    searchListForEveryBasicAssignment =
        !descriptor.hasAnyShadowVariablesInconsistentMember()
            && descriptor.getDeclarativeShadowVariableDescriptors().stream()
                .flatMap(variable -> Arrays.stream(variable.getSources()))
                .flatMap(source -> source.variableSourceReferences().stream())
                .anyMatch(VariableSourceReference::isDeclarative);
    if (searchListForEveryBasicAssignment) {
      clearSearchState();
      currentDecider = basicVariableDecider;
      startListAfterStep = false;
      listPhaseStarted = false;
      resetLastStep = false;
      ((BasicVariableExhaustiveSearchDecider<Solution_, Score_>) basicVariableDecider)
          .setDeferCompleteSolutions(true);
    }
    currentDecider.phaseStarted(phaseScope);
    if (searchListForEveryBasicAssignment) {
      var startNode = phaseScope.getLastCompletedStepScope().getExpandingNode();
      if (startNode.getLayer().isLastLayer()) {
        phaseScope.addExpandableNode(startNode);
      }
    }
  }

  @Override
  public void phaseEnded(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    try {
      basicVariableDecider.phaseEnded(phaseScope);
      if (!searchListForEveryBasicAssignment || listPhaseStarted) {
        listVariableDecider.phaseEnded(phaseScope);
      }
    } finally {
      clearSearchState();
    }
  }

  @Override
  public void stepStarted(ExhaustiveSearchStepScope<Solution_> stepScope) {
    currentDecider.stepStarted(stepScope);
    if (resetLastStep) {
      var phaseScope = stepScope.getPhaseScope();
      phaseScope.getExpandableNodeQueue().clear();
      initStartNode(phaseScope, null);
      resetLastStep = false;
    }
  }

  @Override
  public void stepEnded(ExhaustiveSearchStepScope<Solution_> stepScope) {
    currentDecider.stepEnded(stepScope);
    if (searchListForEveryBasicAssignment) {
      return;
    }
    var isBasicDecider = currentDecider == basicVariableDecider;
    var phaseScope = stepScope.getPhaseScope();
    if (isBasicDecider && phaseScope.getExpandableNodeQueue().isEmpty()) {
      this.currentDecider = listVariableDecider;
      phaseScope.getSolverScope().setWorkingSolutionFromBestSolution();
      resetLastStep = true;
      phaseStarted(phaseScope);
    }
  }

  @Override
  public void afterStep(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    if (!searchListForEveryBasicAssignment) {
      return;
    }
    var lastStep = phaseScope.getLastCompletedStepScope();
    if (startListAfterStep) {
      startListAfterStep = false;
      basicTerminalNode = lastStep.getExpandingNode();
      basicLayerList = phaseScope.getLayerList();
      basicNodeQueue = phaseScope.getExpandableNodeQueue();
      phaseScope.setExpandableNodeQueue(new TreeSet<>(basicNodeQueue.comparator()));
      // The list stage gets its own root. Preserve the basic terminal node for later restoration.
      phaseScope.setLastCompletedStepScope(
          new ExhaustiveSearchStepScope<>(phaseScope, lastStep.getStepIndex()));
      currentDecider = listVariableDecider;
      listPhaseStarted = true;
      listVariableDecider.phaseStarted(phaseScope);
      listStartNode = phaseScope.getLastCompletedStepScope().getExpandingNode();
    }
  }

  @Override
  public boolean onSearchExhausted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    if (!searchListForEveryBasicAssignment || currentDecider != listVariableDecider) {
      return false;
    }
    // Undo list assignments before resuming the suspended basic search, even when its state
    // contains a dependency loop. A different basic assignment may have sound list completions.
    var restoreStep =
        new ExhaustiveSearchStepScope<>(
            phaseScope, phaseScope.getLastCompletedStepScope().getStepIndex());
    restoreStep.setExpandingNode(listStartNode);
    listVariableDecider.restoreWorkingSolution(restoreStep, false, false);
    listVariableDecider.phaseEnded(phaseScope);
    listPhaseStarted = false;
    phaseScope.setLayerList(basicLayerList);
    phaseScope.setExpandableNodeQueue(basicNodeQueue);
    restoreStep.setExpandingNode(basicTerminalNode);
    phaseScope.setLastCompletedStepScope(restoreStep);
    currentDecider = basicVariableDecider;
    return true;
  }

  @Override
  public void releaseSearchState() {
    clearSearchState();
  }

  private void clearSearchState() {
    basicNodeQueue = null;
    basicLayerList = null;
    basicTerminalNode = null;
    listStartNode = null;
    startListAfterStep = false;
    listPhaseStarted = false;
    resetLastStep = false;
  }
}
