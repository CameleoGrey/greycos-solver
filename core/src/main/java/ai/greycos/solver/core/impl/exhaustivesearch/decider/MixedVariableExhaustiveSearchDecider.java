package ai.greycos.solver.core.impl.exhaustivesearch.decider;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchLayer;
import ai.greycos.solver.core.impl.exhaustivesearch.node.ExhaustiveSearchNode;
import ai.greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchPhaseScope;
import ai.greycos.solver.core.impl.exhaustivesearch.scope.ExhaustiveSearchStepScope;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

public final class MixedVariableExhaustiveSearchDecider<Solution_, Score_ extends Score<Score_>>
    extends AbstractExhaustiveSearchDecider<Solution_, Score_> {

  private final AbstractExhaustiveSearchDecider<Solution_, Score_> basicVariableDecider;
  private final AbstractExhaustiveSearchDecider<Solution_, Score_> listVariableDecider;

  private AbstractExhaustiveSearchDecider<Solution_, Score_> currentDecider;
  private boolean resetLastStep = false;

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
    basicVariableDecider.solvingEnded(solverScope);
    listVariableDecider.solvingEnded(solverScope);
  }

  @Override
  public void phaseStarted(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    currentDecider.phaseStarted(phaseScope);
  }

  @Override
  public void phaseEnded(ExhaustiveSearchPhaseScope<Solution_> phaseScope) {
    basicVariableDecider.phaseEnded(phaseScope);
    listVariableDecider.phaseEnded(phaseScope);
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
    var isBasicDecider = currentDecider == basicVariableDecider;
    var phaseScope = stepScope.getPhaseScope();
    if (isBasicDecider && phaseScope.getExpandableNodeQueue().isEmpty()) {
      this.currentDecider = listVariableDecider;
      phaseScope.getSolverScope().setWorkingSolutionFromBestSolution();
      resetLastStep = true;
      phaseStarted(phaseScope);
    }
  }
}
