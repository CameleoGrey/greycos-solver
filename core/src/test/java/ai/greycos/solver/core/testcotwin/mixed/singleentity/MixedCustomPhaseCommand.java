package ai.greycos.solver.core.testcotwin.mixed.singleentity;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.greycos.solver.core.api.solver.phase.PhaseCommand;
import ai.greycos.solver.core.api.solver.phase.PhaseCommandContext;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;

public class MixedCustomPhaseCommand implements PhaseCommand<TestdataMixedSolution> {

  @Override
  public void changeWorkingSolution(PhaseCommandContext<TestdataMixedSolution> context) {
    var scoreDirector = (ScoreDirector<TestdataMixedSolution>) mock(ScoreDirector.class);
    when(scoreDirector.getWorkingSolution()).thenReturn(context.getWorkingSolution());
    var moveIteratorFactory = new MixedCustomMoveIteratorFactory();
    var moveIterator = moveIteratorFactory.createRandomMoveIterator(scoreDirector, null);
    var move = moveIterator.next();
    context.executeAndCalculateScore(move);
  }
}
