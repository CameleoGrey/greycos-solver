package ai.greycos.solver.core.impl.score.director.incremental;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorSemanticsTest;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import ai.greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesSolution;
import ai.greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListSolution;
import ai.greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListIncrementalScoreCalculator;
import ai.greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;

final class IncrementalScoreDirectorSemanticsTest extends AbstractScoreDirectorSemanticsTest {

  @Override
  protected ScoreDirectorFactory<TestdataConstraintWeightOverridesSolution, SimpleScore>
      buildScoreDirectorFactoryWithConstraintConfiguration(
          SolutionDescriptor<TestdataConstraintWeightOverridesSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withIncrementalScoreCalculatorClass(
                TestdataConstraintWeightOverridesIncrementalScoreCalculator.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataConstraintWeightOverridesSolution, SimpleScore>(
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
