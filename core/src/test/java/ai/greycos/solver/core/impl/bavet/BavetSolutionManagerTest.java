package ai.greycos.solver.core.impl.bavet;

import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.impl.score.stream.common.AbstractSolutionManagerTest;
import ai.greycos.solver.core.testcotwin.TestdataConstraintProvider;
import ai.greycos.solver.core.testcotwin.list.unassignedvar.pinned.TestdataPinnedUnassignedValuesListConstraintProvider;

final class BavetSolutionManagerTest extends AbstractSolutionManagerTest {

  @Override
  protected ScoreDirectorFactoryConfig buildScoreDirectorFactoryConfig() {
    return new ScoreDirectorFactoryConfig()
        .withConstraintProviderClass(TestdataConstraintProvider.class);
  }

  @Override
  protected ScoreDirectorFactoryConfig buildUnassignedWithPinningScoreDirectorFactoryConfig() {
    return new ScoreDirectorFactoryConfig()
        .withConstraintProviderClass(TestdataPinnedUnassignedValuesListConstraintProvider.class);
  }
}
