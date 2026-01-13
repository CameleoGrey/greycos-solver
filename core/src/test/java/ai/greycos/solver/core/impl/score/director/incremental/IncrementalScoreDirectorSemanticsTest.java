package ai.greycos.solver.core.impl.score.director.incremental;

import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorSemanticsTest;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import ai.greycos.solver.core.testcotwin.constraintconfiguration.TestdataConstraintConfigurationSolution;
import ai.greycos.solver.core.testcotwin.constraintconfiguration.TestdataConstraintWeighIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListSolution;
import ai.greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;

final class IncrementalScoreDirectorSemanticsTest extends AbstractScoreDirectorSemanticsTest {

  @Override
  protected ScoreDirectorFactory<TestdataConstraintConfigurationSolution, SimpleScore>
      buildScoreDirectorFactoryWithConstraintConfiguration(
          SolutionDescriptor<TestdataConstraintConfigurationSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withIncrementalScoreCalculatorClass(
                TestdataConstraintWeighIncrementalScoreCalculator.class);
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
            .withIncrementalScoreCalculatorClass(
                TestdataPinnedListIncrementalScoreCalculator.class);
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
            .withIncrementalScoreCalculatorClass(
                TestdataPinnedWithIndexListIncrementalScoreCalculator.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataPinnedWithIndexListSolution, SimpleScore>(
            scoreDirectorFactoryConfig);
    return scoreDirectorFactoryFactory.buildScoreDirectorFactory(
        EnvironmentMode.PHASE_ASSERT, solutionDescriptor);
  }
}
