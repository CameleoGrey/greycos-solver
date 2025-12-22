package ai.greycos.solver.core.impl.score.director.stream;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorSemanticsTest;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import ai.greycos.solver.core.testdomain.constraintconfiguration.TestdataConstraintConfigurationSolution;
import ai.greycos.solver.core.testdomain.constraintconfiguration.TestdataConstraintWeightConstraintProvider;
import ai.greycos.solver.core.testdomain.list.pinned.TestdataPinnedListConstraintProvider;
import ai.greycos.solver.core.testdomain.list.pinned.TestdataPinnedListSolution;
import ai.greycos.solver.core.testdomain.list.pinned.index.TestdataPinnedWithIndexListConstraintProvider;
import ai.greycos.solver.core.testdomain.list.pinned.index.TestdataPinnedWithIndexListSolution;

final class ConstraintStreamsBavetScoreDirectorSemanticsTest
    extends AbstractScoreDirectorSemanticsTest {

  @Override
  protected ScoreDirectorFactory<TestdataConstraintConfigurationSolution, SimpleScore>
      buildScoreDirectorFactoryWithConstraintConfiguration(
          SolutionDescriptor<TestdataConstraintConfigurationSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestdataConstraintWeightConstraintProvider.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataConstraintConfigurationSolution, SimpleScore>(
            scoreDirectorFactoryConfig);
    return scoreDirectorFactoryFactory.buildScoreDirectorFactory(
        EnvironmentMode.PHASE_ASSERT, solutionDescriptor);
  }

  @Override
  protected ScoreDirectorFactory<TestdataPinnedListSolution, SimpleScore>
      buildScoreDirectorFactoryWithListVariableEntityPin(
          SolutionDescriptor<TestdataPinnedListSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestdataPinnedListConstraintProvider.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataPinnedListSolution, SimpleScore>(
            scoreDirectorFactoryConfig);
    return scoreDirectorFactoryFactory.buildScoreDirectorFactory(
        EnvironmentMode.PHASE_ASSERT, solutionDescriptor);
  }

  @Override
  protected ScoreDirectorFactory<TestdataPinnedWithIndexListSolution, SimpleScore>
      buildScoreDirectorFactoryWithListVariablePinIndex(
          SolutionDescriptor<TestdataPinnedWithIndexListSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(TestdataPinnedWithIndexListConstraintProvider.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataPinnedWithIndexListSolution, SimpleScore>(
            scoreDirectorFactoryConfig);
    return scoreDirectorFactoryFactory.buildScoreDirectorFactory(
        EnvironmentMode.PHASE_ASSERT, solutionDescriptor);
  }
}
