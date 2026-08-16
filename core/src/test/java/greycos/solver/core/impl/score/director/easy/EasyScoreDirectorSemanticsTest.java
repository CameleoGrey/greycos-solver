package greycos.solver.core.impl.score.director.easy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorSemanticsTest;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesEasyScoreCalculator;
import greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesSolution;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

final class EasyScoreDirectorSemanticsTest extends AbstractScoreDirectorSemanticsTest {

  @Override
  protected ScoreDirectorFactory<TestdataConstraintWeightOverridesSolution, SimpleScore>
      buildScoreDirectorFactoryWithConstraintConfiguration(
          SolutionDescriptor<TestdataConstraintWeightOverridesSolution> solutionDescriptor) {
    var scoreDirectorFactoryConfig =
        new ScoreDirectorFactoryConfig()
            .withEasyScoreCalculatorClass(
                TestdataConstraintWeightOverridesEasyScoreCalculator.class);
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
            .withEasyScoreCalculatorClass(TestdataPinnedListEasyScoreCalculator.class);
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
            .withEasyScoreCalculatorClass(TestdataPinnedWithIndexListEasyScoreCalculator.class);
    var scoreDirectorFactoryFactory =
        new ScoreDirectorFactoryFactory<TestdataPinnedWithIndexListSolution, SimpleScore>(
            scoreDirectorFactoryConfig);
    return scoreDirectorFactoryFactory.buildScoreDirectorFactory(
        EnvironmentMode.PHASE_ASSERT, solutionDescriptor);
  }

  @Test
  void easyScoreCalculatorWithCustomProperties() {
    var config = new ScoreDirectorFactoryConfig();
    config.setEasyScoreCalculatorClass(TestCustomPropertiesEasyScoreCalculator.class);
    var customProperties = new HashMap<String, String>();
    customProperties.put("stringProperty", "string 1");
    customProperties.put("intProperty", "7");
    config.setEasyScoreCalculatorCustomProperties(customProperties);

    var testdataSolutionScoreDirectorFactory = buildTestdataScoreDirectoryFactory(config);
    try (var scoreDirector =
        (EasyScoreDirector<TestdataSolution, SimpleScore>)
            testdataSolutionScoreDirectorFactory.buildScoreDirector()) {
      var scoreCalculator =
          (TestCustomPropertiesEasyScoreCalculator) scoreDirector.getEasyScoreCalculator();
      assertThat(scoreCalculator.getStringProperty()).isEqualTo("string 1");
      assertThat(scoreCalculator.getIntProperty()).isEqualTo(7);
    }
  }

  private ScoreDirectorFactory<TestdataSolution, SimpleScore> buildTestdataScoreDirectoryFactory(
      ScoreDirectorFactoryConfig config, EnvironmentMode environmentMode) {
    return new ScoreDirectorFactoryFactory<TestdataSolution, SimpleScore>(config)
        .buildScoreDirectorFactory(environmentMode, TestdataSolution.buildSolutionDescriptor());
  }

  private ScoreDirectorFactory<TestdataSolution, SimpleScore> buildTestdataScoreDirectoryFactory(
      ScoreDirectorFactoryConfig config) {
    return buildTestdataScoreDirectoryFactory(config, EnvironmentMode.PHASE_ASSERT);
  }

  public static class TestCustomPropertiesEasyScoreCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {

    private String stringProperty;
    private int intProperty;

    public String getStringProperty() {
      return stringProperty;
    }

    @SuppressWarnings("unused")
    public void setStringProperty(String stringProperty) {
      this.stringProperty = stringProperty;
    }

    public int getIntProperty() {
      return intProperty;
    }

    @SuppressWarnings("unused")
    public void setIntProperty(int intProperty) {
      this.intProperty = intProperty;
    }

    @Override
    public @NonNull SimpleScore calculateScore(@NonNull TestdataSolution testdataSolution) {
      return SimpleScore.ZERO;
    }
  }
}
