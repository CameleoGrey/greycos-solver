package greycos.solver.core.impl.multistage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;

import org.junit.jupiter.api.Test;

class MultistageRuntimeTest {

  @Test
  void internalBasicReadsAndOperationConstructionDoNotPollThePhasePredicate() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    var checks = new AtomicInteger();
    var terminated = new AtomicBoolean();
    var cancellation = new CancellationException("explicit cancellation");
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              () -> {
                checks.incrementAndGet();
                if (terminated.get()) throw cancellation;
              });
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      for (int i = 0; i < 2000; i++) {
        evaluator.workingSolution();
        evaluator.currentValue(solution.entity);
        evaluator.owned(evaluator.assign(solution.entity, 1001));
      }
      assertThat(checks).hasValue(0);
      terminated.set(true);
      assertThatThrownBy(evaluator::checkTermination).isSameAs(cancellation);
      assertThat(checks).hasValue(1);
      terminated.set(false);
      assertThatThrownBy(() -> evaluator.currentValue(solution.entity)).isSameAs(cancellation);
      assertThatThrownBy(() -> evaluator.assign(solution.entity, 1001)).isSameAs(cancellation);
      assertThat(checks).hasValue(1);
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void legalValuesAndSequencesPollInBoundedBatches() {
    var solution = new NumericSolution();
    solution.rangeEnd = 10000;
    var descriptor = NumericSolution.descriptor();
    var checks = new AtomicInteger();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              checks::incrementAndGet);
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThat(evaluator.legalValues(solution.entity)).hasSize(9000);
      assertThat(checks.get()).isBetween(2, 150);
      checks.set(0);
      var operations =
          new ArrayList<
              greycos.solver.core.api.solver.multistage.MultistageOperation<NumericSolution>>();
      for (int i = 0; i < 128; i++) operations.add(evaluator.assign(solution.entity, 1001));
      evaluator.sequence(operations);
      assertThat(checks.get()).isBetween(2, 10);
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void legalValueEnumerationStillObservesCancellationInsideLargeRange() {
    var solution = new NumericSolution();
    solution.rangeEnd = 10000;
    var descriptor = NumericSolution.descriptor();
    var checks = new AtomicInteger();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              () -> {
                if (checks.incrementAndGet() == 2)
                  throw new CancellationException("enumeration cancelled");
              });
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThatThrownBy(() -> evaluator.legalValues(solution.entity))
          .isInstanceOf(CancellationException.class);
      assertThat(checks).hasValue(2);
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void cancellationAfterBalancedMutationPreventsScoringAndRestoresBaseline() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      long originalCount = director.getCalculationCount();
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              () -> {
                if (solution.entity.getValue() == 1001)
                  throw new CancellationException("mutation finished");
              });
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1001)))
          .isInstanceOf(CancellationException.class);
      assertThat(director.getCalculationCount()).isEqualTo(originalCount);
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void cancellationAfterScoreProbeRestoresBaselineBeforeReturning() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      long originalCount = director.getCalculationCount();
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              () -> {
                if (director.getCalculationCount() > originalCount)
                  throw new CancellationException("score finished");
              });
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1001)))
          .isInstanceOf(CancellationException.class);
      assertThat(director.getCalculationCount()).isEqualTo(originalCount + 1);
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void listReadAndConstructionCostsDoNotScaleWithFullTerminationChecks() {
    var descriptor = TestdataListSolution.buildSolutionDescriptor();
    var solution = TestdataListSolution.generateInitializedSolution(1024, 2);
    var variable =
        descriptor
            .findEntityDescriptorOrFail(TestdataListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataListSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(TestdataListEntity.class)
                      .reward(SimpleScore.ONE)
                      .asConstraint("entities")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    var checks = new AtomicInteger();
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var transaction =
          new MultistageTransaction<>(
              director,
              variable,
              new MultistageDomain<>(variable, solution),
              10,
              checks::incrementAndGet);
      var evaluator =
          new DefaultListVariableMoveEvaluator<
              TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore>(
              transaction);
      var value = solution.getValueList().getFirst();
      assertThat(evaluator.legalPositions(value)).hasSize(1025);
      assertThat(checks.get()).isBetween(2, 40);
      checks.set(0);
      for (int i = 0; i < 2000; i++) {
        evaluator.position(value);
        evaluator.workingSolution();
        evaluator.owned(evaluator.place(value, solution.getEntityList().getFirst(), 0));
      }
      assertThat(checks).hasValue(0);
      transaction.close(null);
      assertListShadows(solution);
    }
  }

  @Test
  void explicitSubtypeSelectionCannotMutateSiblingWithSameInheritedVariable() {
    var solution = new NumericSolution();
    var sibling = new NumericSibling();
    solution.siblings = List.of(sibling);
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var policy =
          HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy(descriptor)
              .cloneBuilder()
              .withMultistageMoveSelectionEnabled(true)
              .build();
      var selector =
          MoveSelectorFactory.<NumericSolution>create(
                  new MultistageMoveSelectorConfig()
                      .withEntityClass(NumericEntity.class)
                      .withVariableName("value")
                      .withStageProviderClass(ScopedProvider.class))
              .buildMoveSelector(
                  policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
      var solver = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
      var phase = SelectorTestUtils.phaseStarted(selector, solver);
      var step = SelectorTestUtils.stepStarted(selector, phase);
      try {
        var iterator = selector.iterator();
        var allowed = (MultistageMoveRequest<NumericSolution>) iterator.next();
        assertThat(allowed.prepare(director, () -> {}, false, (view, move) -> {}).status())
            .isEqualTo(PreparedMoveEvaluation.Status.EVALUATED);
        assertNumericBaseline(director, solution);
        var forbidden = (MultistageMoveRequest<NumericSolution>) iterator.next();
        assertThatThrownBy(() -> forbidden.prepare(director, () -> {}, false, (view, move) -> {}))
            .hasMessageContaining("does not belong to multistage variable");
        assertThat(sibling.getValue()).isEqualTo(1000);
        assertNumericBaseline(director, solution);
      } finally {
        selector.stepEnded(step);
        selector.phaseEnded(phase);
        selector.solvingEnded(solver);
      }
    }
  }

  @Test
  void inheritedVariableRespectsPinDeclaredOnConcreteEntity() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      solution.entity.pinned = true;
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1001)))
          .hasMessageContaining("pinned entity");
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void providerSessionsResetOnGenerationAndSolutionReplacementButNotOtherOwnerClose() {
    CountingProvider.initializations.set(0);
    CountingProvider.closes.set(0);
    var descriptor = NumericSolution.descriptor();
    var first = new NumericSolution();
    var second = new NumericSolution();
    try (var firstDirector = numericDirector(descriptor, first);
        var secondDirector = numericDirector(descriptor, second)) {
      var definition = definition(descriptor, CountingProvider.class, 10);
      definition.candidateCount(firstDirector);
      definition.candidateCount(firstDirector);
      definition.candidateCount(secondDirector);
      assertThat(CountingProvider.initializations).hasValue(2);
      definition.invalidateEvaluationContexts();
      definition.candidateCount(firstDirector);
      assertThat(CountingProvider.initializations).hasValue(3);
      assertThat(CountingProvider.closes).hasValue(1);
      definition.closeEvaluationContext(secondDirector);
      definition.candidateCount(firstDirector);
      assertThat(CountingProvider.initializations).hasValue(3);
      firstDirector.setWorkingSolution(new NumericSolution());
      definition.candidateCount(firstDirector);
      definition.closeEvaluationContext(firstDirector);
      definition.closeEvaluationContext(firstDirector);
      assertThat(CountingProvider.initializations).hasValue(4);
      assertThat(CountingProvider.closes).hasValue(4);
    }
  }

  @Test
  void failedProviderInitializationClosesOnceAndPreservesCleanupFailure() {
    CountingProvider.initializations.set(0);
    CountingProvider.closes.set(0);
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, new NumericSolution())) {
      var definition = definition(descriptor, FailingInitializationProvider.class, 10);
      assertThatThrownBy(() -> definition.candidateCount(director))
          .hasMessage("provider initialization")
          .satisfies(
              failure -> {
                assertThat(failure.getSuppressed()).hasSize(1);
                assertThat(failure.getSuppressed()[0]).hasMessage("provider cleanup");
              });
      definition.closeEvaluationContext(director);
      assertThat(CountingProvider.initializations).hasValue(1);
      assertThat(CountingProvider.closes).hasValue(1);
    }
  }

  @Test
  void solutionValueOwnershipSurvivesEmptyEntityPopulation() {
    var descriptor = TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor();
    var solution = TestdataAllowsUnassignedValuesListSolution.generateUninitializedSolution(1, 0);
    var variable =
        descriptor
            .findEntityDescriptorOrFail(TestdataAllowsUnassignedValuesListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataAllowsUnassignedValuesListSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(TestdataAllowsUnassignedValuesListEntity.class)
                      .reward(SimpleScore.ONE)
                      .asConstraint("entities")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultListVariableMoveEvaluator<
              TestdataAllowsUnassignedValuesListSolution,
              TestdataAllowsUnassignedValuesListEntity,
              TestdataAllowsUnassignedValuesListValue,
              SimpleScore>(transaction);
      var value = solution.getValueList().getFirst();
      assertThat(evaluator.position(value).isUnassigned()).isTrue();
      assertThat(evaluator.legalPositions(value)).hasSize(1).allMatch(p -> p.isUnassigned());
      assertThat(evaluator.evaluate(evaluator.unassign(value)).isComplete()).isTrue();
      transaction.close(null);
    }
  }

  @Test
  void scoringFailureDuringProbeRestoresIncrementalState() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      solution.entity.failNextMatch = true;
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1001)))
          .hasStackTraceContaining("failure evaluating constraint match");
      transaction.close(null);
      assertNumericBaseline(director, solution);
      director.assertWorkingScoreFromScratch(director.calculateScore(), "recovered scoring probe");
    }
  }

  @Test
  void shadowSupplierFailureDuringProbeRestoresIncrementalState() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      solution.entity.failNextShadow = true;
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1001)))
          .hasStackTraceContaining("failure updating shadow");
      transaction.close(null);
      assertNumericBaseline(director, solution);
      director.assertShadowVariablesAreNotStale(
          director.calculateScore(), "recovered shadow probe");
    }
  }

  @Test
  void currentEvaluationRestoresStoredScoreOnSuccessAndScoringFailure() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      transaction.apply(evaluator.owned(evaluator.assign(solution.entity, 1001)));
      assertThat(evaluator.currentEvaluation().score()).isEqualTo(SimpleScore.of(1001));
      assertThat(solution.score).isEqualTo(SimpleScore.of(1000));
      solution.failNextScore = true;
      assertThatThrownBy(evaluator::currentEvaluation)
          .hasStackTraceContaining("failure after setting the score");
      assertThat(solution.score).isEqualTo(SimpleScore.of(1000));
      transaction.close(null);
      assertNumericBaseline(director, solution);
    }
  }

  @Test
  void generatedNumericRangeAndInheritedVariableRemainLegalAcrossProbeAndReplay() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      assertThat(evaluator.legalValues(solution.entity)).containsExactly(1000, 1001, 1002, 1003);
      var operation = evaluator.assign(solution.entity, Integer.valueOf("1002"));
      assertThat(evaluator.evaluate(operation).score()).isEqualTo(SimpleScore.of(1002));
      assertNumericBaseline(director, solution);
      transaction.apply(evaluator.owned(operation));
      assertThat(solution.entity.getDoubled()).isEqualTo(2004);
      var frozen = transaction.freeze("numeric");
      transaction.close(null);
      assertNumericBaseline(director, solution);
      director.getMoveDirector().execute(frozen);
      assertThat(solution.entity.getValue()).isEqualTo(1002);
      assertThat(solution.entity.getDoubled()).isEqualTo(2004);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(1002));
    }
  }

  @Test
  void partialSetterFailureRebuildsIncrementalStateFromSnapshot() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var variable =
          descriptor
              .findEntityDescriptorOrFail(NumericEntity.class)
              .getGenuineVariableDescriptor("value");
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultBasicVariableMoveEvaluator<
              NumericSolution, NumericEntity, Integer, SimpleScore>(transaction);
      transaction.apply(evaluator.owned(evaluator.assign(solution.entity, 1001)));
      solution.entity.failNextAssignment = true;
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.assign(solution.entity, 1003)))
          .hasStackTraceContaining("failure after setting the genuine variable");
      transaction.close(null);
      assertNumericBaseline(director, solution);
      director.assertShadowVariablesAreNotStale(
          director.calculateScore(), "recovered multistage transaction");
    }
  }

  @Test
  void catchingBudgetFailureCannotCommitPartialCandidate() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var definition = definition(descriptor, BudgetCatchingProvider.class, 1);
      var result =
          new MultistageMoveRequest<>(definition, 0, 1)
              .prepare(director, () -> {}, true, (view, move) -> {});
      assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EMPTY);
      assertThat(result.move()).isNull();
      assertNumericBaseline(director, solution);
      definition.closeEvaluationContext(director);
    }
  }

  @Test
  void catchingCancellationCannotCommitPartialCandidate() {
    var solution = new NumericSolution();
    var descriptor = NumericSolution.descriptor();
    try (var director = numericDirector(descriptor, solution)) {
      var definition = definition(descriptor, CancellationCatchingProvider.class, 10);
      var result =
          new MultistageMoveRequest<>(definition, 0, 1)
              .prepare(
                  director,
                  () -> {
                    if (solution.entity.getValue() == 1002)
                      throw new CancellationException("cancel probe");
                  },
                  true,
                  (view, move) -> {});
      assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.CANCELLED);
      assertNumericBaseline(director, solution);
      definition.closeEvaluationContext(director);
    }
  }

  @Test
  void listSequenceResolvesShiftedPositionsAndRestoresEveryShadow() {
    var descriptor = TestdataListSolution.buildSolutionDescriptor();
    var solution = TestdataListSolution.generateInitializedSolution(6, 2);
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    var originalLeft = List.copyOf(left.getValueList());
    var originalRight = List.copyOf(right.getValueList());
    var first = originalLeft.getFirst();
    var second = originalLeft.get(1);
    var variable =
        descriptor
            .findEntityDescriptorOrFail(TestdataListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataListSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(TestdataListEntity.class)
                      .reward(SimpleScore.ONE, e -> e.getValueList().size())
                      .asConstraint("assigned")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var baseline = director.calculateScore();
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultListVariableMoveEvaluator<
              TestdataListSolution, TestdataListEntity, TestdataListValue, SimpleScore>(
              transaction);
      var operation =
          evaluator.sequence(
              List.of(
                  evaluator.unassign(first),
                  evaluator.unassign(second),
                  evaluator.place(second, right, 1),
                  evaluator.place(first, right, 2),
                  evaluator.swap(originalLeft.getLast(), originalRight.getFirst()),
                  evaluator.reverse(right, 0, 3)));
      assertThat(evaluator.evaluate(operation).isComplete()).isTrue();
      assertThat(left.getValueList()).containsExactlyElementsOf(originalLeft);
      assertThat(right.getValueList()).containsExactlyElementsOf(originalRight);
      assertListShadows(solution);
      assertThat(director.calculateScore()).isEqualTo(baseline);
      transaction.apply(evaluator.owned(operation));
      var expectedLeft = List.copyOf(left.getValueList());
      var expectedRight = List.copyOf(right.getValueList());
      var frozen = transaction.freeze("list");
      transaction.close(null);
      assertListShadows(solution);
      director.getMoveDirector().execute(frozen);
      assertThat(left.getValueList()).containsExactlyElementsOf(expectedLeft);
      assertThat(right.getValueList()).containsExactlyElementsOf(expectedRight);
      assertListShadows(solution);
    }
  }

  @Test
  void optionalListUnassignmentRetainsRemovedValueForTabu() {
    var descriptor = TestdataAllowsUnassignedValuesListSolution.buildSolutionDescriptor();
    var value = new TestdataAllowsUnassignedValuesListValue("value");
    var entity = TestdataAllowsUnassignedValuesListEntity.createWithValues("entity", value);
    var solution = new TestdataAllowsUnassignedValuesListSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(value));
    var variable =
        descriptor
            .findEntityDescriptorOrFail(TestdataAllowsUnassignedValuesListEntity.class)
            .getGenuineVariableDescriptor("valueList");
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataAllowsUnassignedValuesListSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(TestdataAllowsUnassignedValuesListEntity.class)
                      .reward(SimpleScore.ONE)
                      .asConstraint("entities")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.calculateScore();
      var transaction =
          new MultistageTransaction<>(
              director, variable, new MultistageDomain<>(variable, solution), 10, () -> {});
      var evaluator =
          new DefaultListVariableMoveEvaluator<
              TestdataAllowsUnassignedValuesListSolution,
              TestdataAllowsUnassignedValuesListEntity,
              TestdataAllowsUnassignedValuesListValue,
              SimpleScore>(transaction);
      transaction.apply(evaluator.owned(evaluator.unassign(value)));
      var frozen = transaction.freeze("unassign");
      assertThat(frozen.getPlanningValues()).containsExactly(value);
      transaction.close(null);
      assertThat(entity.getValueList()).containsExactly(value);
      director.getMoveDirector().execute(frozen);
      assertThat(value.getEntity()).isNull();
      assertThat(value.getIndex()).isNull();
    }
  }

  private static void assertListShadows(TestdataListSolution solution) {
    for (var entity : solution.getEntityList()) {
      for (int i = 0; i < entity.getValueList().size(); i++) {
        assertThat(entity.getValueList().get(i).getEntity()).isSameAs(entity);
        assertThat(entity.getValueList().get(i).getIndex()).isEqualTo(i);
      }
    }
  }

  private static MultistageDefinition<NumericSolution> definition(
      SolutionDescriptor<NumericSolution> descriptor, Class<?> provider, long probes) {
    GenuineVariableDescriptor<NumericSolution> variable =
        descriptor
            .findEntityDescriptorOrFail(NumericEntity.class)
            .getGenuineVariableDescriptor("value");
    return new MultistageDefinition<>(descriptor, variable, provider, probes);
  }

  private static BavetConstraintStreamScoreDirector<NumericSolution, SimpleScore> numericDirector(
      SolutionDescriptor<NumericSolution> descriptor, NumericSolution solution) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<NumericSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(NumericEntity.class)
                      .reward(SimpleScore.ONE, NumericEntity::scoreValue)
                      .asConstraint("value")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    var director = factory.createScoreDirectorBuilder().build();
    director.setWorkingSolution(solution);
    director.updateShadowVariables();
    director.calculateScore();
    return director;
  }

  private static void assertNumericBaseline(
      BavetConstraintStreamScoreDirector<NumericSolution, SimpleScore> director,
      NumericSolution solution) {
    assertThat(solution.entity.getValue()).isEqualTo(1000);
    assertThat(solution.entity.getDoubled()).isEqualTo(2000);
    assertThat(solution.score).isEqualTo(SimpleScore.of(1000));
    assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(1000));
  }

  public static class BudgetCatchingProvider
      implements BasicVariableStageProvider<NumericSolution, NumericEntity, Integer, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<BasicVariableCustomStage<NumericSolution, NumericEntity, Integer, SimpleScore>>
        createStages(long index, RandomGenerator random) {
      return List.of(
          e -> MultistageStageResult.apply(e.assign(e.workingSolution().entity, 1001)),
          e -> {
            var entity = e.workingSolution().entity;
            e.evaluate(e.assign(entity, 1002));
            try {
              e.evaluate(e.assign(entity, 1003));
            } catch (RuntimeException ignored) {
              /* The budget must remain exhausted. */
            }
            return MultistageStageResult.skip();
          });
    }
  }

  public static class CountingProvider
      implements BasicVariableStageProvider<NumericSolution, NumericEntity, Integer, SimpleScore> {
    static final AtomicInteger initializations = new AtomicInteger();
    static final AtomicInteger closes = new AtomicInteger();

    @Override
    public void initialize(NumericSolution solution) {
      initializations.incrementAndGet();
    }

    @Override
    public void phaseEnded() {
      closes.incrementAndGet();
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<BasicVariableCustomStage<NumericSolution, NumericEntity, Integer, SimpleScore>>
        createStages(long index, RandomGenerator random) {
      return List.of();
    }
  }

  public static class ScopedProvider
      implements BasicVariableStageProvider<NumericSolution, NumericBase, Integer, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 2;
    }

    @Override
    public List<BasicVariableCustomStage<NumericSolution, NumericBase, Integer, SimpleScore>>
        createStages(long index, RandomGenerator random) {
      return List.of(
          e ->
              MultistageStageResult.apply(
                  e.assign(
                      index == 0
                          ? e.workingSolution().entity
                          : e.workingSolution().siblings.getFirst(),
                      1001)));
    }
  }

  public static class FailingInitializationProvider extends CountingProvider {
    @Override
    public void initialize(NumericSolution solution) {
      super.initialize(solution);
      throw new IllegalStateException("provider initialization");
    }

    @Override
    public void phaseEnded() {
      super.phaseEnded();
      throw new IllegalStateException("provider cleanup");
    }
  }

  public static class CancellationCatchingProvider
      implements BasicVariableStageProvider<NumericSolution, NumericEntity, Integer, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<BasicVariableCustomStage<NumericSolution, NumericEntity, Integer, SimpleScore>>
        createStages(long index, RandomGenerator random) {
      return List.of(
          e -> MultistageStageResult.apply(e.assign(e.workingSolution().entity, 1001)),
          e -> {
            try {
              e.evaluate(e.assign(e.workingSolution().entity, 1002));
            } catch (CancellationException ignored) {
              /* Cancellation must remain sticky after rollback. */
            }
            return MultistageStageResult.skip();
          });
    }
  }

  @PlanningEntity
  public static class NumericBase {
    private Integer value = 1000;
    boolean failNextAssignment;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public Integer getValue() {
      return value;
    }

    public void setValue(Integer value) {
      this.value = value;
      if (failNextAssignment) {
        failNextAssignment = false;
        throw new IllegalStateException("failure after setting the genuine variable");
      }
    }
  }

  @PlanningEntity
  public static class NumericEntity extends NumericBase {
    private int doubled = 2000;
    @PlanningPin public boolean pinned;
    boolean failNextMatch;
    boolean failNextShadow;

    @ShadowVariable(supplierName = "doubleValue")
    public int getDoubled() {
      return doubled;
    }

    public void setDoubled(int value) {
      doubled = value;
    }

    @ShadowSources("value")
    public int doubleValue() {
      if (failNextShadow) {
        failNextShadow = false;
        throw new IllegalStateException("failure updating shadow");
      }
      return getValue() == null ? 0 : getValue() * 2;
    }

    public int scoreValue() {
      if (failNextMatch) {
        failNextMatch = false;
        throw new IllegalStateException("failure evaluating constraint match");
      }
      return getValue();
    }
  }

  @PlanningEntity
  public static class NumericSibling extends NumericBase {}

  @PlanningSolution
  public static class NumericSolution {
    NumericEntity entity = new NumericEntity();
    List<NumericSibling> siblings = List.of();
    SimpleScore score;
    boolean failNextScore;
    int rangeEnd = 1004;

    public static SolutionDescriptor<NumericSolution> descriptor() {
      return SolutionDescriptor.buildSolutionDescriptor(
          NumericSolution.class, NumericBase.class, NumericEntity.class, NumericSibling.class);
    }

    @PlanningEntityCollectionProperty
    public List<NumericEntity> getEntities() {
      return List.of(entity);
    }

    @PlanningEntityCollectionProperty
    public List<NumericSibling> getSiblings() {
      return siblings;
    }

    @ValueRangeProvider(id = "values")
    public ValueRange<Integer> values() {
      return ValueRangeFactory.createIntValueRange(1000, rangeEnd);
    }

    @PlanningScore
    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
      if (failNextScore) {
        failNextScore = false;
        throw new IllegalStateException("failure after setting the score");
      }
    }
  }
}
