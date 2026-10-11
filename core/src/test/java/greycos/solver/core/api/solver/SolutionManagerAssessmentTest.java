package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;

import greycos.solver.core.api.cotwin.variable.InconsistentSolutionException;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.AnalyzableIncrementalScoreCalculator;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistry;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEasyScoreCalculator;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencyEntity;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencySolution;
import greycos.solver.core.testcotwin.shadow.dependency.TestdataDependencyValue;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldConstraintProvider;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldEntity;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldSolution;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldValue;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEasyScoreCalculator;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SolutionManagerAssessmentTest {

  enum Backend {
    CONSTRAINT_STREAMS,
    EASY,
    INCREMENTAL,
    ANALYZABLE_INCREMENTAL
  }

  @ParameterizedTest
  @EnumSource(Backend.class)
  void requiredBasicAssignmentsAreCountedIndependentlyOfScoringBackend(Backend backend) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class);
    switch (backend) {
      case CONSTRAINT_STREAMS ->
          config.withConstraintProviderClass(TestdataConstraintProvider.class);
      case EASY -> config.withEasyScoreCalculatorClass(AssignedEntityCalculator.class);
      case INCREMENTAL ->
          config.withScoreDirectorFactory(
              new ScoreDirectorFactoryConfig()
                  .withIncrementalScoreCalculatorClass(AssignedEntityIncrementalCalculator.class));
      case ANALYZABLE_INCREMENTAL ->
          config.withScoreDirectorFactory(
              new ScoreDirectorFactoryConfig()
                  .withIncrementalScoreCalculatorClass(AnalyzableAssignedEntityCalculator.class));
    }
    var manager =
        SolutionManager.<TestdataSolution, SimpleScore>create(SolverFactory.create(config));
    var solution = TestdataSolution.generateUninitializedSolution(2, 3);
    solution.getEntityList().getFirst().setValue(solution.getValueList().getFirst());
    solution.setScore(SimpleScore.of(999));

    var assessment = manager.assess(solution);

    assertThat(assessment.score()).isEqualTo(SimpleScore.of(-1)).isEqualTo(solution.getScore());
    assertThat(assessment.missingRequiredAssignmentCount()).isEqualTo(2);
    assertThat(assessment.isInitialized()).isFalse();
    assertThat(assessment.isStructurallySound()).isTrue();
    assertThat(manager.assess(TestdataSolution.generateUninitializedSolution(0, 0)))
        .isEqualTo(new SolutionAssessment<>(SimpleScore.ZERO, 0));
  }

  @Test
  void optionalBasicAssignmentsAreExcluded() {
    var manager =
        SolutionManager.<TestdataAllowsUnassignedSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataAllowsUnassignedSolution.class)
                    .withEntityClasses(TestdataAllowsUnassignedEntity.class)
                    .withEasyScoreCalculatorClass(
                        TestdataAllowsUnassignedEasyScoreCalculator.class)));
    var solution = TestdataAllowsUnassignedSolution.generateSolution(2, 3);
    solution.getEntityList().forEach(entity -> entity.setValue(null));

    assertThat(manager.assess(solution)).isEqualTo(new SolutionAssessment<>(SimpleScore.of(-3), 0));
  }

  @Test
  void requiredListOmissionsAreCountedAndShadowsRepaired() {
    var manager =
        SolutionManager.<TestdataListSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataListSolution.class)
                    .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
                    .withEasyScoreCalculatorClass(ListCalculator.class)));
    var solution = TestdataListSolution.generateUninitializedSolution(3, 1);
    var entity = solution.getEntityList().getFirst();
    var assignedValue = solution.getValueList().getFirst();
    entity.getValueList().add(assignedValue);
    assignedValue.setIndex(999);

    assertThat(manager.assess(solution)).isEqualTo(new SolutionAssessment<>(SimpleScore.of(-1), 2));
    assertThat(assignedValue.getEntity()).isSameAs(entity);
    assertThat(assignedValue.getIndex()).isZero();

    entity.getValueList().addAll(solution.getValueList().subList(1, 3));
    assertThat(manager.assess(solution).isInitialized()).isTrue();
  }

  @Test
  void optionalListOmissionsAreExcluded() {
    var manager =
        SolutionManager.<TestdataAllowsUnassignedValuesListSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataAllowsUnassignedValuesListSolution.class)
                    .withEntityClasses(
                        TestdataAllowsUnassignedValuesListEntity.class,
                        TestdataAllowsUnassignedValuesListValue.class)
                    .withEasyScoreCalculatorClass(
                        TestdataAllowsUnassignedValuesListEasyScoreCalculator.class)));
    var solution = TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(3, 1);

    assertThat(manager.assess(solution)).isEqualTo(new SolutionAssessment<>(SimpleScore.ZERO, 0));
  }

  @Test
  void duplicateListAssignmentsCannotHideAnOmittedRequiredValue() {
    var manager =
        SolutionManager.<TestdataListSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataListSolution.class)
                    .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
                    .withEasyScoreCalculatorClass(ListCalculator.class)));
    var solution = TestdataListSolution.generateUninitializedSolution(3, 1);
    var values = solution.getValueList();
    solution
        .getEntityList()
        .getFirst()
        .getValueList()
        .addAll(List.of(values.get(0), values.get(0), values.get(1)));

    assertThatThrownBy(() -> manager.assess(solution))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void listPinningDoesNotAlterCompletenessOrGenuineAssignments() {
    var manager =
        SolutionManager.<TestdataPinnedWithIndexListSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataPinnedWithIndexListSolution.class)
                    .withEntityClasses(
                        TestdataPinnedWithIndexListEntity.class,
                        TestdataPinnedWithIndexListValue.class)
                    .withEasyScoreCalculatorClass(PinnedListCalculator.class)));
    var solution = TestdataPinnedWithIndexListSolution.generateUninitializedSolution(3, 2);
    var pinnedEntity = solution.getEntityList().getFirst();
    var pinnedValue = solution.getValueList().getFirst();
    pinnedEntity.getValueList().add(pinnedValue);
    pinnedEntity.setPinIndex(1);
    pinnedEntity.setPinned(true);

    assertThat(manager.assess(solution).missingRequiredAssignmentCount()).isEqualTo(2);
    assertThat(pinnedEntity.getValueList()).containsExactly(pinnedValue);
    assertThat(pinnedValue.getEntity()).isSameAs(pinnedEntity);
    assertThat(pinnedEntity.getPinIndex()).isEqualTo(1);
  }

  @Test
  void mixedModelAddsMissingBasicVariablesAndListValuesAfterShadowRepair() {
    var manager =
        SolutionManager.<TestdataMixedSolution, SimpleScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataMixedSolution.class)
                    .withEntityClasses(
                        TestdataMixedEntity.class,
                        TestdataMixedValue.class,
                        TestdataMixedOtherValue.class)
                    .withEasyScoreCalculatorClass(TestdataMixedEasyScoreCalculator.class)));
    var solution = TestdataMixedSolution.generateUninitializedSolution(2, 3, 2);
    var entity = solution.getEntityList().getFirst();
    entity.setBasicValue(solution.getOtherValueList().getFirst());
    entity.setDeclarativeShadowVariableValue(999);
    entity.getValueList().add(solution.getValueList().getFirst());

    var assessment = manager.assess(solution);

    assertThat(assessment.missingRequiredAssignmentCount()).isEqualTo(5);
    assertThat(assessment.score()).isEqualTo(solution.getScore());
    assertThat(entity.getDeclarativeShadowVariableValue())
        .isEqualTo(entity.getBasicValue().getStrength());
    assertThat(solution.getValueList().getFirst().getEntity()).isSameAs(entity);
    assertThat(solution.getValueList().getFirst().getIndex()).isZero();
  }

  @Test
  void structuralFailureUsesUpdatePolicyAndNeverCertifiesSentinelInitialization() {
    var first = new TestdataDependencyNoInconsistentFieldValue("first");
    var second =
        new TestdataDependencyNoInconsistentFieldValue(
            "second", Duration.ofHours(1), List.of(first));
    var missing = new TestdataDependencyNoInconsistentFieldValue("missing");
    var entity = new TestdataDependencyNoInconsistentFieldEntity("entity");
    entity.setValues(List.of(second, first));
    var solution =
        new TestdataDependencyNoInconsistentFieldSolution(
            List.of(entity), List.of(first, second, missing));
    var manager =
        SolutionManager.create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataDependencyNoInconsistentFieldSolution.class)
                    .withEntityClasses(
                        TestdataDependencyNoInconsistentFieldEntity.class,
                        TestdataDependencyNoInconsistentFieldValue.class)
                    .withConstraintProviderClass(
                        TestdataDependencyNoInconsistentFieldConstraintProvider.class)));

    assertThatThrownBy(() -> manager.assess(solution))
        .isInstanceOf(InconsistentSolutionException.class)
        .hasMessageContaining("Solution assessment");
    assertThat(solution.getScore().structuralScore()).isNegative();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void legacyMarkerLoopsRetainNativeScoreSemantics(boolean penalize) {
    var first = new TestdataDependencyValue("first", Duration.ofMinutes(1));
    var second = new TestdataDependencyValue("second", Duration.ofMinutes(1), List.of(first));
    var entity = new TestdataDependencyEntity();
    entity.setValues(List.of(second, first));
    var solution = new TestdataDependencySolution(List.of(entity), List.of(first, second));
    var manager =
        SolutionManager.<TestdataDependencySolution, HardSoftScore>create(
            SolverFactory.create(
                new SolverConfig()
                    .withSolutionClass(TestdataDependencySolution.class)
                    .withEntityClasses(
                        TestdataDependencyEntity.class, TestdataDependencyValue.class)
                    .withEasyScoreCalculatorClass(
                        penalize
                            ? PenalizedMarkerCalculator.class
                            : UnpenalizedMarkerCalculator.class)));

    var assessment = manager.assess(solution);

    assertThat(assessment.isInitialized()).isTrue();
    assertThat(assessment.isStructurallySound()).isTrue();
    assertThat(solution.getValues()).anyMatch(value -> Boolean.TRUE.equals(value.getIsInvalid()));
    assertThat(assessment.score().isFeasible()).isEqualTo(!penalize);
  }

  @Test
  void assessmentValidatesItsContractAndThirdPartyManagersHaveADefault() {
    assertThatThrownBy(() -> new SolutionAssessment<SimpleScore>(null, 0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SolutionAssessment<>(SimpleScore.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new SolutionAssessment<>(new SimpleScore(-1, 0), 0).isStructurallySound()).isFalse();
    var thirdPartyManager = mock(SolutionManager.class, CALLS_REAL_METHODS);
    assertThatThrownBy(() -> thirdPartyManager.assess(new Object()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  public static class AssignedEntityCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          -solution.getEntityList().stream().filter(entity -> entity.getValue() != null).count());
    }
  }

  public static class AssignedEntityIncrementalCalculator
      implements IncrementalScoreCalculator<TestdataSolution, SimpleScore> {
    private TestdataSolution solution;

    @Override
    public void resetWorkingSolution(TestdataSolution solution) {
      this.solution = solution;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {}

    @Override
    public void afterVariableChanged(Object entity, String variableName) {}

    @Override
    public SimpleScore calculateScore() {
      return new AssignedEntityCalculator().calculateScore(solution);
    }
  }

  public static class AnalyzableAssignedEntityCalculator extends AssignedEntityIncrementalCalculator
      implements AnalyzableIncrementalScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public void enableConstraintMatch(ConstraintMatchRegistry<SimpleScore> registry) {
      throw new AssertionError("Assessment must not enable constraint matching.");
    }
  }

  public static class ListCalculator
      implements EasyScoreCalculator<TestdataListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataListSolution solution) {
      return SimpleScore.of(
          -solution.getEntityList().stream()
              .mapToInt(entity -> entity.getValueList().size())
              .sum());
    }
  }

  public static class PinnedListCalculator
      implements EasyScoreCalculator<TestdataPinnedWithIndexListSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataPinnedWithIndexListSolution solution) {
      return SimpleScore.of(
          -solution.getEntityList().stream()
              .mapToInt(entity -> entity.getValueList().size())
              .sum());
    }
  }

  public static class UnpenalizedMarkerCalculator
      implements EasyScoreCalculator<TestdataDependencySolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataDependencySolution solution) {
      return HardSoftScore.ZERO;
    }
  }

  public static class PenalizedMarkerCalculator
      implements EasyScoreCalculator<TestdataDependencySolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataDependencySolution solution) {
      return HardSoftScore.of(
          -solution.getValues().stream()
              .filter(value -> Boolean.TRUE.equals(value.getIsInvalid()))
              .count(),
          0);
    }
  }
}
