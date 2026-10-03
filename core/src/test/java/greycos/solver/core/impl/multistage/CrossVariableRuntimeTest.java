package greycos.solver.core.impl.multistage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
import greycos.solver.core.api.solver.multistage.BasicVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedOtherValue;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossVariableRuntimeTest {
  private static final BasicVariableReference<CrossBase, Integer> FIRST =
      BasicVariableReference.of(CrossBase.class, "first", Integer.class);
  private static final BasicVariableReference<CrossBase, Integer> SECOND =
      BasicVariableReference.of(CrossBase.class, "second", Integer.class);
  private static final ListVariableReference<CrossBase, CrossValue> VALUES =
      ListVariableReference.of(CrossBase.class, "values", CrossValue.class);
  private static final BasicVariableReference<CrossEntity, Integer> NARROW_FIRST =
      BasicVariableReference.of(CrossEntity.class, "first", Integer.class);
  private static final ListVariableReference<CrossEntity, CrossValue> NARROW_VALUES =
      ListVariableReference.of(CrossEntity.class, "values", CrossValue.class);

  @Test
  void heterogeneousProbeHasOneBudgetAndSharedSavepointAndReplaysOnAnotherClone() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(1);
      var firstStage = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      transaction.apply(firstStage.owned(firstStage.basic(FIRST).assign(entity, 1001)));
      firstStage.invalidate();
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var destination = fixture.solution.entities.get(1);
      var value = fixture.solution.values.get(1);
      var basic = evaluator.basic(FIRST);
      var list = evaluator.list(VALUES);
      var operation =
          basic.sequence(
              List.of(
                  basic.assign(entity, Integer.valueOf("1002")),
                  evaluator.basic(SECOND).assign(entity, Integer.valueOf("2002")),
                  list.place(value, destination, 0),
                  list.place(value, entity, 1),
                  list.place(value, destination, 0)));
      long count = fixture.director.getCalculationCount();
      assertThat(evaluator.evaluate(operation).score()).isEqualTo(SimpleScore.of(9010));
      assertThat(fixture.director.getCalculationCount()).isEqualTo(count + 1);
      assertThat(entity.getFirst()).isEqualTo(1001);
      assertThat(entity.getSecond()).isEqualTo(2000);
      assertThat(entity.getTotal()).isEqualTo(3001);
      assertThat(entity.getValues())
          .containsExactlyElementsOf(fixture.solution.values.subList(0, 2));
      assertThat(destination.getValues()).isEmpty();
      assertThat(fixture.solution.score).isEqualTo(fixture.baseline);
      fixture.assertShadows();
      transaction.apply(evaluator.owned(operation));
      var frozen = transaction.freeze("cross-variable replay");
      transaction.close(null);
      fixture.assertBaseline();
      try (var destinationFixture = new Fixture(fixture.descriptor)) {
        var rebased = frozen.rebase(destinationFixture.director.getMoveDirector());
        destinationFixture.director.getMoveDirector().execute(rebased);
        assertThat(destinationFixture.solution.entities.getFirst().getFirst()).isEqualTo(1002);
        assertThat(destinationFixture.solution.entities.getFirst().getSecond()).isEqualTo(2002);
        assertThat(destinationFixture.solution.entities.get(1).getValues())
            .containsExactly(destinationFixture.solution.values.get(1));
        destinationFixture.assertShadows();
        assertThat(destinationFixture.director.calculateScore().raw())
            .isEqualTo(SimpleScore.of(9010));
        fixture.assertBaseline();
      }
    }
  }

  @Test
  void validationFailureRestoresTheWholeProbeButKeepsEarlierStagePrefix() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      transaction.apply(evaluator.owned(evaluator.basic(FIRST).assign(entity, 1001)));
      var rejected =
          evaluator.sequence(
              List.of(
                  evaluator.basic(FIRST).assign(entity, 1002),
                  evaluator
                      .list(VALUES)
                      .place(fixture.solution.values.get(1), fixture.solution.entities.get(1), 0),
                  evaluator.basic(SECOND).assign(entity, 1002)));
      assertThatThrownBy(() -> evaluator.evaluate(rejected))
          .hasMessageContaining("outside the range");
      assertThat(entity.getFirst()).isEqualTo(1001);
      assertThat(entity.getSecond()).isEqualTo(2000);
      assertThat(entity.getValues()).hasSize(2);
      fixture.assertShadows();
      assertThat(evaluator.evaluate(evaluator.basic(SECOND).assign(entity, 2002)).score())
          .isEqualTo(SimpleScore.of(9008));
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @Test
  void snapshotsDistinguishVariablesButDeduplicateAliasesOfTheSameCoordinate() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = (CrossEntity) fixture.solution.entities.getFirst();
      transaction.apply(
          evaluator.owned(
              evaluator.sequence(
                  List.of(
                      evaluator.basic(FIRST).assign(entity, 1001),
                      evaluator.basic(NARROW_FIRST).assign(entity, 1002),
                      evaluator.basic(SECOND).assign(entity, 2002)))));
      var failure = new IllegalStateException("abort after aliased writes");
      transaction.abort(failure);
      assertThat(failure.getSuppressed()).isEmpty();
      fixture.assertBaseline();
      assertThatThrownBy(evaluator::currentEvaluation).hasMessageContaining("closed");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"setter", "shadow", "score", "undo"})
  void exceptionalRecoveryRestoresEveryBasicAndListCoordinate(String failureKind) {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      transaction.apply(
          evaluator.owned(
              evaluator.sequence(
                  List.of(
                      evaluator.basic(FIRST).assign(entity, 1001),
                      evaluator.basic(SECOND).assign(entity, 2001),
                      evaluator
                          .list(VALUES)
                          .place(
                              fixture.solution.values.get(1),
                              fixture.solution.entities.get(1),
                              0)))));
      switch (failureKind) {
        case "setter" -> entity.failNextSetter = true;
        case "shadow" -> entity.failNextShadow = true;
        case "score" -> entity.failNextMatch = true;
        case "undo" -> entity.failSetterAt = 2001;
        default -> throw new IllegalStateException(failureKind);
      }
      var operation =
          evaluator.sequence(
              List.of(
                  evaluator.basic(FIRST).assign(entity, 1002),
                  evaluator.basic(SECOND).assign(entity, 2002),
                  evaluator
                      .list(VALUES)
                      .place(
                          fixture.solution.values.getFirst(),
                          fixture.solution.entities.get(1),
                          0)));
      assertThatThrownBy(() -> evaluator.evaluate(operation))
          .hasStackTraceContaining("cross failure");
      transaction.close(null);
      fixture.assertBaseline();
      assertThatThrownBy(() -> evaluator.basic(FIRST).currentValue(entity))
          .hasMessageContaining("closed");
      fixture.director.assertWorkingScoreFromScratch(
          fixture.director.calculateScore(), failureKind);
    }
  }

  @Test
  void recoveryAttemptsAllCoordinatesEvenWhenOneRestoringSetterFails() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      transaction.apply(
          evaluator.owned(
              evaluator.sequence(
                  List.of(
                      evaluator.basic(FIRST).assign(entity, 1001),
                      evaluator.basic(SECOND).assign(entity, 2001),
                      evaluator
                          .list(VALUES)
                          .place(
                              fixture.solution.values.get(1),
                              fixture.solution.entities.get(1),
                              0)))));
      entity.failSetterAt = 2000;
      var original = new IllegalStateException("original failure");
      transaction.abort(original);
      assertThat(original.getSuppressed()).hasSize(1);
      assertThat(original.getSuppressed()[0]).hasStackTraceContaining("cross failure setter");
      fixture.assertBaseline();
      assertThatThrownBy(evaluator::workingSolution).hasMessageContaining("closed");
    }
  }

  @Test
  void viewsAndOperationsCannotEscapeTheirStageThreadOrCandidate() throws InterruptedException {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      var view = evaluator.basic(FIRST);
      var operation = view.assign(entity, 1001);
      var failure = new AtomicReference<Throwable>();
      var thread =
          new Thread(
              () -> {
                try {
                  view.evaluate(operation);
                } catch (Throwable error) {
                  failure.set(error);
                }
              });
      thread.start();
      thread.join(5000);
      assertThat(thread.isAlive()).isFalse();
      assertThat(failure.get())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("callback thread");
      evaluator.invalidate();
      assertThatThrownBy(() -> view.assign(entity, 1002)).hasMessageContaining("stage callback");
      assertThatThrownBy(() -> evaluator.basic(FIRST)).hasMessageContaining("stage callback");
      var nextStage = new DefaultCrossVariableMoveEvaluator<>(transaction);
      assertThatThrownBy(() -> nextStage.evaluate(operation)).hasMessageContaining("another stage");
      var foreignTransaction = fixture.transaction(10);
      var foreign = new DefaultCrossVariableMoveEvaluator<>(foreignTransaction);
      assertThatThrownBy(
              () -> nextStage.sequence(List.of(foreign.basic(SECOND).assign(entity, 2001))))
          .hasMessageContaining("another stage");
      foreignTransaction.close(null);
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @Test
  void exactReferencesCloneMembershipAndSubtypeScopesCannotBeWidened() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      assertThat(
              evaluator.basic(BasicVariableReference.of(CrossBase.class, "first", Integer.class)))
          .isSameAs(evaluator.basic(FIRST));
      assertThatThrownBy(
              () ->
                  evaluator.basic(
                      BasicVariableReference.of(CrossBase.class, "first", Number.class)))
          .hasMessageContaining("not declared");
      assertThatThrownBy(
              () ->
                  evaluator.basic(
                      BasicVariableReference.of(CrossEntity.class, "second", Integer.class)))
          .hasMessageContaining("not declared");
      var wrongClone = new CrossEntity("foreign", 1);
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.basic(FIRST).assign(wrongClone, 1001)))
          .hasMessageContaining("does not belong");
      var siblingValue = fixture.solution.values.get(2);
      assertThatThrownBy(() -> evaluator.list(NARROW_VALUES).position(siblingValue))
          .hasMessageContaining("does not belong");
      assertThatThrownBy(
              () ->
                  evaluator.evaluate(
                      evaluator
                          .list(NARROW_VALUES)
                          .place(
                              siblingValue, (CrossEntity) fixture.solution.entities.getFirst(), 0)))
          .hasMessageContaining("does not belong");
      assertThat(evaluator.list(VALUES).position(siblingValue).entity())
          .isSameAs(fixture.solution.entities.get(2));
      assertThatThrownBy(
              () ->
                  evaluator.evaluate(
                      evaluator
                          .list(VALUES)
                          .place(new CrossValue("v0"), fixture.solution.entities.getFirst(), 0)))
          .hasMessageContaining("outside this working solution");
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @Test
  void pinsAndPinnedPrefixesAreCheckedPerLiveVariableView() {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = (CrossEntity) fixture.solution.entities.getFirst();
      entity.pinned = true;
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.basic(FIRST).assign(entity, 1001)))
          .hasMessageContaining("pinned entity");
      assertThat(evaluator.list(VALUES).legalPositions(fixture.solution.values.getFirst()))
          .isEmpty();
      entity.pinned = false;
      entity.pinnedIndex = 1;
      assertThat(evaluator.list(VALUES).legalPositions(fixture.solution.values.getFirst()))
          .isEmpty();
      assertThatThrownBy(() -> evaluator.evaluate(evaluator.list(VALUES).reverse(entity, 0, 2)))
          .hasMessageContaining("pinned multistage list range");
      assertThat(evaluator.evaluate(evaluator.basic(SECOND).assign(entity, 2001)).isComplete())
          .isTrue();
      entity.pinnedIndex = 0;
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @Test
  void equalAndDistinctDescriptorDomainsShareOnlyEntityMembership() {
    try (var fixture = new Fixture()) {
      var domains = fixture.definition.session(fixture.director).domains;
      assertThat(domains.get(fixture.binding(FIRST)))
          .isSameAs(domains.get(fixture.binding(SECOND)));
      assertThat(domains.get(fixture.binding(FIRST)))
          .isNotSameAs(domains.get(fixture.binding(NARROW_FIRST)));
      var transaction = fixture.transaction(10);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      assertThat(evaluator.basic(FIRST).legalValues(entity))
          .containsExactly(1000, 1001, 1002, 1003);
      assertThat(evaluator.basic(SECOND).legalValues(entity))
          .containsExactly(2000, 2001, 2002, 2003);
      assertThat(evaluator.list(VALUES).position(fixture.solution.values.getFirst()).entity())
          .isSameAs(entity);
      assertThat(
              evaluator
                  .evaluate(evaluator.basic(FIRST).assign(entity, Integer.valueOf("1002")))
                  .isComplete())
          .isTrue();
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @Test
  void sharedScopeKeepsMutableValueMembershipSeparateByDescriptor() {
    var solution = TestdataMixedSolution.generateUninitializedSolution(2, 2, 2);
    var original = solution.getOtherValueList().getLast();
    for (var entity : solution.getEntityList()) {
      entity.setBasicValue(original);
      entity.setSecondBasicValue(original);
    }
    solution.getEntityList().getFirst().getValueList().addAll(solution.getValueList());
    var descriptor = TestdataMixedSolution.buildSolutionDescriptor();
    var basic =
        BasicVariableReference.of(
            TestdataMixedEntity.class, "basicValue", TestdataMixedOtherValue.class);
    var list =
        ListVariableReference.of(TestdataMixedEntity.class, "valueList", TestdataMixedValue.class);
    var entityDescriptor = descriptor.getEntityDescriptorStrict(TestdataMixedEntity.class);
    var basicBinding =
        new MultistageVariableBinding<>(
            basic, entityDescriptor.getGenuineVariableDescriptor("basicValue"), entityDescriptor);
    var listBinding =
        new MultistageVariableBinding<>(
            list, entityDescriptor.getGenuineVariableDescriptor("valueList"), entityDescriptor);
    var domain = new MultistageDomain<>(entityDescriptor, solution);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMixedSolution, SimpleScore>(
            descriptor,
            f ->
                new Constraint[] {
                  f.forEach(TestdataMixedEntity.class)
                      .reward(SimpleScore.ONE, entity -> entity.getBasicValue().getStrength())
                      .asConstraint("basic")
                },
            EnvironmentMode.NO_ASSERT,
            false);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      director.updateShadowVariables();
      var baseline = director.calculateScore();
      var transaction =
          new MultistageTransaction<>(
              director, java.util.Map.of(basicBinding, domain, listBinding, domain), 10, () -> {});
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = solution.getEntityList().getFirst();
      var value = solution.getValueList().getFirst();
      var operation =
          evaluator.sequence(
              List.of(
                  evaluator.basic(basic).assign(entity, solution.getOtherValueList().getFirst()),
                  evaluator.list(list).place(value, solution.getEntityList().getLast(), 0)));
      assertThat(evaluator.evaluate(operation).isComplete()).isTrue();
      assertThat(entity.getBasicValue()).isSameAs(original);
      assertThat(value.getEntity()).isSameAs(entity);
      transaction.close(null);
      assertThat(director.calculateScore()).isEqualTo(baseline);
      director.assertWorkingScoreFromScratch(baseline, "per-descriptor membership");
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 2, 3})
  void providerStagesKeepPrefixAbortAndFinalCompletenessSemanticsWithoutReplayCallbacks(
      long candidate) {
    try (var fixture = new Fixture()) {
      Stages.calls.set(0);
      var definition = fixture.definition(Stages.class, 10);
      try {
        var result =
            new MultistageMoveRequest<>(definition, candidate, 1)
                .prepare(fixture.director, () -> {}, true, null);
        fixture.assertBaseline();
        if (candidate == 0) {
          assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EVALUATED);
          assertThat(Stages.calls).hasValue(3);
          fixture.director.getMoveDirector().execute(result.move());
          assertThat(Stages.calls).hasValue(3);
          fixture.assertShadows();
          assertThat(fixture.director.calculateScore()).isEqualTo(result.score());
        } else {
          assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EMPTY);
          assertThat(result.move()).isNull();
          assertThat(Stages.calls).hasValue(candidate == 1 ? 2 : 1);
        }
      } finally {
        definition.closeEvaluationContext(fixture.director);
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void caughtBudgetOrCancellationCannotRetainMixedPrefix(boolean cancelled) {
    try (var fixture = new Fixture()) {
      var transaction = fixture.transaction(1);
      var evaluator = new DefaultCrossVariableMoveEvaluator<>(transaction);
      var entity = fixture.solution.entities.getFirst();
      transaction.apply(
          evaluator.owned(
              evaluator.sequence(
                  List.of(
                      evaluator.basic(FIRST).assign(entity, 1001),
                      evaluator.basic(SECOND).assign(entity, 2001),
                      evaluator
                          .list(VALUES)
                          .place(
                              fixture.solution.values.get(1),
                              fixture.solution.entities.get(1),
                              0)))));
      if (cancelled) {
        Thread.currentThread().interrupt();
        try {
          assertThatThrownBy(evaluator::checkTermination).isInstanceOf(CancellationException.class);
        } finally {
          Thread.interrupted();
        }
        assertThatThrownBy(evaluator::currentEvaluation).isInstanceOf(CancellationException.class);
      } else {
        evaluator.currentEvaluation();
        assertThatThrownBy(evaluator::currentEvaluation)
            .isInstanceOf(MultistageTransaction.ProbeLimitExceeded.class);
        assertThatThrownBy(() -> evaluator.basic(FIRST).assign(entity, 1002))
            .isInstanceOf(MultistageTransaction.ProbeLimitExceeded.class);
      }
      transaction.close(null);
      fixture.assertBaseline();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void providerDispatchUsesConfiguredKindWhenAProviderImplementsMultipleContracts(
      boolean crossVariable) {
    try (var fixture = new Fixture()) {
      DualProvider.basicCalls.set(0);
      DualProvider.crossCalls.set(0);
      var definition =
          crossVariable
              ? fixture.definition(DualProvider.class, 10)
              : new MultistageDefinition<>(
                  fixture.descriptor, fixture.binding(FIRST).variable(), DualProvider.class, 10);
      try {
        var result =
            new MultistageMoveRequest<>(definition, 0, 0)
                .prepare(fixture.director, () -> {}, false, null);
        assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EVALUATED);
        assertThat(result.score().raw()).isEqualTo(SimpleScore.of(crossVariable ? 9007 : 9006));
        assertThat(DualProvider.basicCalls).hasValue(crossVariable ? 0 : 1);
        assertThat(DualProvider.crossCalls).hasValue(crossVariable ? 1 : 0);
        fixture.assertBaseline();
      } finally {
        definition.closeEvaluationContext(fixture.director);
      }
    }
  }

  public static final class DualProvider
      implements BasicVariableStageProvider<CrossSolution, CrossBase, Integer, SimpleScore>,
          CrossVariableStageProvider<CrossSolution, SimpleScore> {
    static final AtomicInteger basicCalls = new AtomicInteger();
    static final AtomicInteger crossCalls = new AtomicInteger();

    @Override
    public void initialize(CrossSolution solution) {}

    @Override
    public void phaseEnded() {}

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List createStages(long candidate, RandomGenerator random) {
      return List.of(new DualStage());
    }
  }

  public static final class DualStage
      implements BasicVariableCustomStage<CrossSolution, CrossBase, Integer, SimpleScore>,
          CrossVariableCustomStage<CrossSolution, SimpleScore> {
    @Override
    public MultistageStageResult<CrossSolution> selectMove(
        BasicVariableMoveEvaluator<CrossSolution, CrossBase, Integer, SimpleScore> evaluator) {
      DualProvider.basicCalls.incrementAndGet();
      return MultistageStageResult.apply(
          evaluator.assign(evaluator.workingSolution().entities.getFirst(), 1001));
    }

    @Override
    public MultistageStageResult<CrossSolution> selectMove(
        CrossVariableMoveEvaluator<CrossSolution, SimpleScore> evaluator) {
      DualProvider.crossCalls.incrementAndGet();
      return MultistageStageResult.apply(
          evaluator.basic(SECOND).assign(evaluator.workingSolution().entities.getFirst(), 2002));
    }
  }

  public static final class Stages
      implements CrossVariableStageProvider<CrossSolution, SimpleScore> {
    static final AtomicInteger calls = new AtomicInteger();

    @Override
    public long getCandidateCount() {
      return 4;
    }

    @Override
    public List<CrossVariableCustomStage<CrossSolution, SimpleScore>> createStages(
        long candidate, RandomGenerator random) {
      if (candidate == 2)
        return List.of(
            e -> {
              calls.incrementAndGet();
              return MultistageStageResult.apply(
                  e.basic(FIRST).unassign(e.workingSolution().entities.getFirst()));
            });
      if (candidate == 3)
        return List.of(
            e -> {
              calls.incrementAndGet();
              var entity = e.workingSolution().entities.getFirst();
              return MultistageStageResult.apply(
                  e.sequence(
                      List.of(
                          e.basic(FIRST).assign(entity, 1001), e.basic(FIRST).assign(entity, 1000),
                          e.basic(SECOND).assign(entity, 2001),
                              e.basic(SECOND).assign(entity, 2000))));
            });
      return List.of(
          e -> {
            calls.incrementAndGet();
            return MultistageStageResult.apply(
                e.basic(FIRST).assign(e.workingSolution().entities.getFirst(), 1001));
          },
          e -> {
            calls.incrementAndGet();
            var solution = e.workingSolution();
            var entity = solution.entities.getFirst();
            assertThat(entity.getFirst()).isEqualTo(1001);
            assertThat(entity.getTotal()).isEqualTo(3001);
            if (candidate == 1) return MultistageStageResult.abortCandidate();
            var operation =
                e.sequence(
                    List.of(
                        e.basic(SECOND).assign(entity, 2002),
                        e.list(VALUES).place(solution.values.get(1), solution.entities.get(1), 0)));
            assertThat(e.evaluate(operation).score()).isEqualTo(SimpleScore.of(9009));
            assertThat(entity.getSecond()).isEqualTo(2000);
            return MultistageStageResult.apply(operation);
          },
          e -> {
            calls.incrementAndGet();
            assertThat(e.workingSolution().entities.getFirst().getTotal()).isEqualTo(3003);
            assertThat(e.workingSolution().entities.get(1).getValues()).hasSize(1);
            return MultistageStageResult.skip();
          });
    }
  }

  private static final class Fixture implements AutoCloseable {
    final SolutionDescriptor<CrossSolution> descriptor;
    final CrossSolution solution = new CrossSolution();
    final BavetConstraintStreamScoreDirector<CrossSolution, SimpleScore> director;
    final MultistageDefinition<CrossSolution> definition;
    final SimpleScore baseline;

    Fixture() {
      this(
          SolutionDescriptor.buildSolutionDescriptor(
              CrossSolution.class,
              CrossBase.class,
              CrossEntity.class,
              CrossSibling.class,
              CrossValue.class));
    }

    Fixture(SolutionDescriptor<CrossSolution> descriptor) {
      this.descriptor = descriptor;
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<CrossSolution, SimpleScore>(
              descriptor,
              f ->
                  new Constraint[] {
                    f.forEach(CrossBase.class)
                        .reward(SimpleScore.ONE, CrossBase::scoreValue)
                        .asConstraint("cross state")
                  },
              EnvironmentMode.NO_ASSERT,
              false);
      director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
      director.setWorkingSolution(solution);
      director.updateShadowVariables();
      baseline = director.calculateScore().raw();
      definition = definition(Stages.class, 10);
    }

    MultistageVariableBinding<CrossSolution> binding(MultistageVariableReference<?, ?> reference) {
      var entity = descriptor.getEntityDescriptorStrict(reference.entityClass());
      return new MultistageVariableBinding<>(
          reference, entity.getGenuineVariableDescriptor(reference.variableName()), entity);
    }

    MultistageDefinition<CrossSolution> definition(Class<?> provider, long probes) {
      return new MultistageDefinition<>(
          descriptor,
          List.of(
              binding(FIRST),
              binding(SECOND),
              binding(VALUES),
              binding(NARROW_FIRST),
              binding(NARROW_VALUES)),
          provider,
          probes);
    }

    MultistageTransaction<CrossSolution, SimpleScore> transaction(long probes) {
      return new MultistageTransaction<>(
          director, definition.session(director).domains, probes, () -> {});
    }

    void assertBaseline() {
      for (var entity : solution.entities) {
        assertThat(entity.getFirst()).isEqualTo(1000);
        assertThat(entity.getSecond()).isEqualTo(2000);
        assertThat(entity.getTotal()).isEqualTo(3000);
      }
      assertThat(solution.entities.getFirst().getValues())
          .containsExactly(solution.values.get(0), solution.values.get(1));
      assertThat(solution.entities.get(1).getValues()).isEmpty();
      assertThat(solution.entities.get(2).getValues()).containsExactly(solution.values.get(2));
      assertThat(solution.score).isEqualTo(baseline);
      assertShadows();
      assertThat(director.calculateScore().raw()).isEqualTo(baseline);
    }

    void assertShadows() {
      for (var entity : solution.entities) {
        assertThat(entity.getTotal()).isEqualTo(entity.getFirst() + entity.getSecond());
        for (int index = 0; index < entity.getValues().size(); index++) {
          var value = entity.getValues().get(index);
          assertThat(value.entity).isSameAs(entity);
          assertThat(
                  director
                      .getListVariableState(descriptor.getListVariableDescriptor())
                      .getElementPosition(value))
              .isEqualTo(ElementPosition.of(entity, index));
        }
      }
    }

    @Override
    public void close() {
      definition.closeEvaluationContext(director);
      director.close();
    }
  }

  @PlanningEntity
  public static class CrossBase extends TestdataObject {
    private Integer first = 1000;
    private Integer second = 2000;
    private List<CrossValue> values = new ArrayList<>();
    private int total;
    private int listWeight;
    boolean failNextSetter;
    boolean failNextShadow;
    boolean failNextMatch;
    Integer failSetterAt;

    public CrossBase() {}

    CrossBase(String code, int listWeight) {
      super(code);
      this.listWeight = listWeight;
    }

    @PlanningVariable(valueRangeProviderRefs = "firstValues")
    public Integer getFirst() {
      return first;
    }

    public void setFirst(Integer first) {
      this.first = first;
    }

    @PlanningVariable(valueRangeProviderRefs = "secondValues")
    public Integer getSecond() {
      return second;
    }

    public void setSecond(Integer second) {
      this.second = second;
      if (failNextSetter || second.equals(failSetterAt)) {
        failNextSetter = false;
        failSetterAt = null;
        throw new IllegalStateException("cross failure setter");
      }
    }

    @PlanningListVariable(valueRangeProviderRefs = "listValues")
    public List<CrossValue> getValues() {
      return values;
    }

    public void setValues(List<CrossValue> values) {
      this.values = values;
    }

    public int getTotal() {
      return total;
    }

    public void setTotal(int total) {
      this.total = total;
    }

    public int total() {
      if (failNextShadow) {
        failNextShadow = false;
        throw new IllegalStateException("cross failure shadow");
      }
      return (first == null ? 0 : first) + (second == null ? 0 : second);
    }

    public int scoreValue() {
      if (failNextMatch) {
        failNextMatch = false;
        throw new IllegalStateException("cross failure score");
      }
      return first + second + values.size() * listWeight;
    }
  }

  @PlanningEntity
  public static class CrossEntity extends CrossBase {
    @PlanningPin public boolean pinned;
    @PlanningPinToIndex public int pinnedIndex;

    public CrossEntity() {}

    CrossEntity(String code, int weight) {
      super(code, weight);
    }

    @Override
    @ShadowVariable(supplierName = "calculateTotal")
    public int getTotal() {
      return super.getTotal();
    }

    @Override
    public void setTotal(int total) {
      super.setTotal(total);
    }

    @ShadowSources({"first", "second"})
    public int calculateTotal() {
      return total();
    }
  }

  @PlanningEntity
  public static class CrossSibling extends CrossBase {
    public CrossSibling() {}

    CrossSibling(String code, int weight) {
      super(code, weight);
    }

    @Override
    @ShadowVariable(supplierName = "calculateTotal")
    public int getTotal() {
      return super.getTotal();
    }

    @Override
    public void setTotal(int total) {
      super.setTotal(total);
    }

    @ShadowSources({"first", "second"})
    public int calculateTotal() {
      return total();
    }
  }

  @PlanningEntity
  public static class CrossValue extends TestdataObject {
    @InverseRelationShadowVariable(sourceVariableName = "values")
    public CrossBase entity;

    public CrossValue() {}

    CrossValue(String code) {
      super(code);
    }
  }

  @PlanningSolution
  public static class CrossSolution {
    @PlanningEntityCollectionProperty
    public List<CrossBase> entities =
        List.of(new CrossEntity("e0", 1), new CrossEntity("e1", 2), new CrossSibling("e2", 3));

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "listValues")
    public List<CrossValue> values =
        List.of(new CrossValue("v0"), new CrossValue("v1"), new CrossValue("v2"));

    @PlanningScore public SimpleScore score;

    public CrossSolution() {
      entities.getFirst().getValues().addAll(values.subList(0, 2));
      entities.get(2).getValues().add(values.get(2));
    }

    @ValueRangeProvider(id = "firstValues")
    public ValueRange<Integer> firstValues() {
      return ValueRangeFactory.createIntValueRange(1000, 1004);
    }

    @ValueRangeProvider(id = "secondValues")
    public ValueRange<Integer> secondValues() {
      return ValueRangeFactory.createIntValueRange(2000, 2004);
    }
  }
}
