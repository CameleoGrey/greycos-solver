package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.score.definition.AbstractScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchUnsupportedScoreTest {

  @Test
  void numericCustomScoreFailsAtSolverConstructionBeforeSearch() {
    var config =
        new SolverConfig()
            .withSolutionClass(CustomSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(CustomCalculator.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH));
    SolverFactory.create(
            config
                .copyConfig()
                .withPhases(
                    new LocalSearchPhaseConfig()
                        .withLocalSearchType(LocalSearchType.HILL_CLIMBING)))
        .buildSolver();
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .hasMessageContaining("Guided Local Search does not support custom score class")
        .hasMessageContaining(CustomScore.class.getName());
  }

  public record CustomScore(SimpleScore delegate) implements Score<CustomScore> {
    @Override
    public long structuralScore() {
      return delegate.structuralScore();
    }

    @Override
    public CustomScore add(CustomScore other) {
      return new CustomScore(delegate.add(other.delegate));
    }

    @Override
    public CustomScore subtract(CustomScore other) {
      return new CustomScore(delegate.subtract(other.delegate));
    }

    @Override
    public CustomScore multiply(double factor) {
      return new CustomScore(delegate.multiply(factor));
    }

    @Override
    public CustomScore divide(double divisor) {
      return new CustomScore(delegate.divide(divisor));
    }

    @Override
    public CustomScore power(double exponent) {
      return new CustomScore(delegate.power(exponent));
    }

    @Override
    public CustomScore abs() {
      return new CustomScore(delegate.abs());
    }

    @Override
    public CustomScore zero() {
      return new CustomScore(SimpleScore.ZERO);
    }

    @Override
    public Number[] toLevelNumbers() {
      return delegate.toLevelNumbers();
    }

    @Override
    public boolean isFeasible() {
      return delegate.isFeasible();
    }

    @Override
    public String toShortString() {
      return delegate.toShortString();
    }

    @Override
    public int compareTo(CustomScore other) {
      return delegate.compareTo(other.delegate);
    }
  }

  public static class CustomDefinition extends AbstractScoreDefinition<CustomScore> {
    private final SimpleScoreDefinition delegate = new SimpleScoreDefinition();

    public CustomDefinition() {
      super(new String[] {"score"});
    }

    @Override
    public int getFeasibleLevelsSize() {
      return 0;
    }

    @Override
    public Class<CustomScore> getScoreClass() {
      return CustomScore.class;
    }

    @Override
    public CustomScore getStructurallyFlawedScore() {
      return new CustomScore(delegate.getStructurallyFlawedScore());
    }

    @Override
    public CustomScore getStructurallyFlawedScore(CustomScore score) {
      return new CustomScore(delegate.getStructurallyFlawedScore(score.delegate()));
    }

    @Override
    public CustomScore getZeroScore() {
      return new CustomScore(SimpleScore.ZERO);
    }

    @Override
    public CustomScore getOneSoftestScore() {
      return new CustomScore(SimpleScore.ONE);
    }

    @Override
    public CustomScore parseScore(String text) {
      return new CustomScore(delegate.parseScore(text));
    }

    @Override
    public CustomScore fromLevelNumbers(Number[] values) {
      return new CustomScore(delegate.fromLevelNumbers(values));
    }

    @Override
    public CustomScore buildOptimisticBound(InitializingScoreTrend trend, CustomScore score) {
      return new CustomScore(delegate.buildOptimisticBound(trend, score.delegate()));
    }

    @Override
    public CustomScore buildPessimisticBound(InitializingScoreTrend trend, CustomScore score) {
      return new CustomScore(delegate.buildPessimisticBound(trend, score.delegate()));
    }

    @Override
    public CustomScore divideBySanitizedDivisor(CustomScore dividend, CustomScore divisor) {
      return new CustomScore(
          delegate.divideBySanitizedDivisor(dividend.delegate(), divisor.delegate()));
    }

    @Override
    public Class<?> getNumericType() {
      return long.class;
    }
  }

  public static class CustomCalculator implements EasyScoreCalculator<CustomSolution, CustomScore> {
    @Override
    public CustomScore calculateScore(CustomSolution solution) {
      return new CustomScore(SimpleScore.ZERO);
    }
  }

  @PlanningSolution
  public static class CustomSolution {
    private CustomScore score;
    private List<TestdataEntity> entityList = List.of();
    private List<TestdataValue> valueList = List.of();

    @PlanningEntityCollectionProperty
    public List<TestdataEntity> getEntityList() {
      return entityList;
    }

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<TestdataValue> getValueList() {
      return valueList;
    }

    @SuppressWarnings("removal")
    @PlanningScore(scoreDefinitionClass = CustomDefinition.class)
    public CustomScore getScore() {
      return score;
    }

    public void setScore(CustomScore score) {
      this.score = score;
    }
  }
}
