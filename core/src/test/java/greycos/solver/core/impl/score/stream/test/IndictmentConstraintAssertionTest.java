package greycos.solver.core.impl.score.stream.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;
import greycos.solver.core.api.score.stream.test.SingleConstraintVerification;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.constraintweightoverrides.TestdataConstraintWeightOverridesSolution;
import greycos.solver.core.testcotwin.planningid.TestdataStringPlanningIdEntity;
import greycos.solver.core.testcotwin.planningid.TestdataStringPlanningIdSolution;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedEntity;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedSolution;
import greycos.solver.core.testcotwin.shadow.counting.TestdataCountingEntity;
import greycos.solver.core.testcotwin.shadow.counting.TestdataCountingSolution;
import greycos.solver.core.testcotwin.shadow.counting.TestdataCountingValue;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationEntity;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationSolution;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationValue;
import greycos.solver.core.testcotwin.shadow.multiplelistener.TestdataListMultipleShadowVariableEntity;
import greycos.solver.core.testcotwin.shadow.multiplelistener.TestdataListMultipleShadowVariableSolution;
import greycos.solver.core.testcotwin.shadow.multiplelistener.TestdataListMultipleShadowVariableValue;

import org.junit.jupiter.api.Test;

class IndictmentConstraintAssertionTest {

  @Test
  void stringObjectsAreNeverInterpretedAsAssertionMessages() {
    var verification =
        verify(
            factory ->
                factory.forEach(String.class).penalize(SimpleScore.ONE).asConstraint("string"),
            TestdataStringPlanningIdSolution.class,
            TestdataEntity.class,
            TestdataStringPlanningIdEntity.class);
    var single = verification.given("actual");
    single
        .indictsWith("actual")
        .indictsWithExactly("actual")
        .originallyIndictsWith("actual")
        .originallyIndictsWithExactly("actual")
        .indictsWithMessage("terminal subset", "actual")
        .indictsExactlyWithMessage("terminal exact", "actual")
        .originallyIndictsWithMessage("original subset", "actual")
        .originallyIndictsExactlyWithMessage("original exact", "actual");

    assertThatThrownBy(() -> single.indictsWith("missing", "actual"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("missing");
    assertThatThrownBy(() -> single.indictsWithExactly("missing", "actual"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("missing");
    assertThatThrownBy(() -> single.originallyIndictsWith("missing", "actual"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("missing");
    assertThatThrownBy(() -> single.originallyIndictsWithExactly("missing", "actual"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("missing");

    verification
        .given("first", "second")
        .indictsWith("first", "second")
        .indictsWithExactly("first", "second")
        .originallyIndictsWith("first", "second")
        .originallyIndictsWithExactly("first", "second");

    verification
        .given()
        .indictsWithMessage("empty terminal subset")
        .indictsExactlyWithMessage("empty terminal exact")
        .originallyIndictsWithMessage("empty original subset")
        .originallyIndictsExactlyWithMessage("empty original exact");
    assertThatThrownBy(() -> single.indictsWithMessage("terminal subset message"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("terminal subset message");
    assertThatThrownBy(() -> single.indictsExactlyWithMessage("terminal exact message"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("terminal exact message");
    assertThatThrownBy(() -> single.originallyIndictsWithMessage("original subset message"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("original subset message");
    assertThatThrownBy(() -> single.originallyIndictsExactlyWithMessage("original exact message"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("original exact message");
  }

  @Test
  void indictmentCollectionIsOptInAndCapabilitiesAreCachedSeparately() {
    var indictmentCalls = new AtomicInteger();
    var justificationCalls = new AtomicInteger();
    var verification =
        verify(
            factory ->
                factory
                    .forEach(TestdataEntity.class)
                    .map(TestdataEntity::getCode)
                    .penalize(SimpleScore.ONE)
                    .justifyWith(
                        (code, score) ->
                            new CallJustification(justificationCalls.incrementAndGet()))
                    .indictWith(
                        code -> {
                          indictmentCalls.incrementAndGet();
                          return List.of(new Key(code));
                        })
                    .asConstraint("custom"),
            TestdataSolution.class,
            TestdataEntity.class);
    var entity = new TestdataEntity("A", new TestdataValue("value"));
    var assertion = verification.given(entity);

    assertion.penalizesBy(1);
    assertion.justifiesWithExactly(new CallJustification(1));
    assertThat(assertion.<SimpleScore>getScore()).isEqualTo(SimpleScore.of(-1));
    assertThat(indictmentCalls).hasValue(0);

    assertThat(assertion.indictsWith(new Key("A"))).isSameAs(assertion);
    assertion.indictsExactlyWithMessage("terminal", new Key("A"), new Key("A"));
    assertThat(indictmentCalls).hasValue(1);

    assertion
        .originallyIndictsWithMessage("original", entity)
        .originallyIndictsWithExactly(entity)
        .indictsWithExactly(new Key("A"))
        .justifiesWithExactly(new CallJustification(1))
        .penalizesBy(1);
    assertThat(indictmentCalls).hasValue(2);
    assertThat(justificationCalls).hasValue(3);
  }

  @Test
  void originalFirstAlsoCachesTerminalView() {
    var calls = new AtomicInteger();
    var entity = new TestdataEntity("A", new TestdataValue("value"));
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataEntity.class)
                        .penalize(SimpleScore.ONE)
                        .indictWith(
                            fact -> {
                              calls.incrementAndGet();
                              return List.of(new Key(fact.getCode()));
                            })
                        .asConstraint("custom"),
                TestdataSolution.class,
                TestdataEntity.class)
            .given(entity);
    assertion
        .originallyIndictsWithExactly(entity)
        .indictsWithExactly(new Key("A"))
        .originallyIndictsWith(entity);
    assertThat(calls).hasValue(1);
  }

  @Test
  void suppliedFactArrayIsCopied() {
    var entity = new TestdataEntity("A", new TestdataValue("value"));
    Object[] facts = {entity};
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataEntity.class)
                        .penalize(SimpleScore.ONE)
                        .asConstraint("default"),
                TestdataSolution.class,
                TestdataEntity.class)
            .given(facts);
    facts[0] = new TestdataEntity("replacement", entity.getValue());
    assertion.indictsWithExactly(entity).originallyIndictsWithExactly(entity).penalizesBy(1);
  }

  @Test
  void transformedTerminalAndOriginalViewsHaveIndependentExpectations() {
    var first = new TestdataEntity("A", new TestdataValue("first"));
    var second = new TestdataEntity("B", new TestdataValue("second"));
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataEntity.class)
                        .groupBy(ConstraintCollectors.count())
                        .penalize(SimpleScore.ONE)
                        .asConstraint("count"),
                TestdataSolution.class,
                TestdataEntity.class)
            .given(first, second);
    assertion
        .indictsWithExactly(2L)
        .originallyIndictsWith(first)
        .originallyIndictsWithExactly(first, second);
    assertThatThrownBy(() -> assertion.indictsWithMessage("terminal mismatch", first))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("terminal mismatch")
        .hasMessageContaining("Expected but not found");
    assertThatThrownBy(
            () -> assertion.originallyIndictsExactlyWithMessage("original mismatch", first))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("original mismatch")
        .hasMessageContaining("Unexpected but found");
  }

  @Test
  void emptyViewsAndEmptyExpectations() {
    var verification =
        verify(
            factory ->
                factory
                    .forEach(TestdataEntity.class)
                    .penalize(SimpleScore.ONE)
                    .asConstraint("default"),
            TestdataSolution.class,
            TestdataEntity.class);
    verification
        .given()
        .indictsWith()
        .indictsWithExactly()
        .originallyIndictsWith()
        .originallyIndictsWithExactly()
        .hasNoImpact();
    var assertion = verification.given(new TestdataEntity("A", new TestdataValue("value")));
    assertThatThrownBy(assertion::indictsWithExactly).isInstanceOf(AssertionError.class);
    assertThatThrownBy(assertion::originallyIndictsWith).isInstanceOf(AssertionError.class);
  }

  @Test
  void solutionEnrichmentIncludesProblemFactsAndHonorsDisabledConstraintOverride() {
    var verification =
        verify(
            factory ->
                factory
                    .forEach(TestdataValue.class)
                    .penalize(SimpleScore.ONE)
                    .asConstraint("value"),
            TestdataConstraintWeightOverridesSolution.class,
            TestdataEntity.class);
    var solution = TestdataConstraintWeightOverridesSolution.generateSolution(1, 1);
    var value = solution.getValueList().getFirst();
    verification
        .givenSolution(solution)
        .indictsWithExactly(value)
        .originallyIndictsWithExactly(value)
        .penalizesBy(1);

    solution.setConstraintWeightOverrides(
        ConstraintWeightOverrides.of(Map.of("value", SimpleScore.ZERO)));
    verification
        .givenSolution(solution)
        .indictsWithExactly()
        .originallyIndictsWithExactly()
        .hasNoImpact();
  }

  @Test
  void enrichmentDoesNotCallDeclarativeSuppliersAgain() {
    var entity = new TestdataCountingEntity("entity");
    var first = new TestdataCountingValue("first");
    var second = new TestdataCountingValue("second");
    entity.getValues().addAll(List.of(first, second));
    var solution =
        new TestdataCountingSolution("solution", List.of(entity), List.of(first, second));
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataCountingValue.class)
                        .filter(value -> value.getCount() != null)
                        .penalize(SimpleScore.ONE)
                        .asConstraint("counted"),
                TestdataCountingSolution.class,
                TestdataCountingEntity.class,
                TestdataCountingValue.class)
            .givenSolution(solution)
            .settingAllShadowVariables();
    // These suppliers throw if evaluated more than once.
    assertion
        .indictsWithExactly(first, second)
        .originallyIndictsWithExactly(first, second)
        .penalizesBy(2);
    assertThat(first.getCount()).isZero();
    assertThat(second.getCount()).isOne();
  }

  @Test
  void enrichmentDoesNotReinitializeInverseCollections() {
    var value = new TestdataInverseRelationValue("value");
    var entity = new TestdataInverseRelationEntity("entity", value);
    var solution = new TestdataInverseRelationSolution("solution");
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(value));
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataInverseRelationValue.class)
                        .filter(fact -> !fact.getEntities().isEmpty())
                        .penalize(SimpleScore.ONE)
                        .asConstraint("inverse"),
                TestdataInverseRelationSolution.class,
                TestdataInverseRelationEntity.class,
                TestdataInverseRelationValue.class)
            .givenSolution(solution)
            .settingAllShadowVariables();
    var inverseCollection = List.of(entity);
    value.setEntities(inverseCollection);
    assertion.indictsWithExactly(value).originallyIndictsWithExactly(value).penalizesBy(1);
    assertThat(value.getEntities()).isSameAs(inverseCollection).containsExactly(entity);
  }

  @Test
  void enrichmentDoesNotTriggerBuiltInShadowListeners() {
    var solution = TestdataListMultipleShadowVariableSolution.generateSolution(2, 2);
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataListMultipleShadowVariableValue.class)
                        .penalize(SimpleScore.ONE)
                        .asConstraint("list"),
                TestdataListMultipleShadowVariableSolution.class,
                TestdataListMultipleShadowVariableEntity.class,
                TestdataListMultipleShadowVariableValue.class)
            .givenSolution(solution)
            .settingAllShadowVariables();
    var value = solution.getValueList().getFirst();
    var entityHistory = new ArrayList<>(value.getEntityHistory());
    var indexHistory = new ArrayList<>(value.getIndexHistory());
    assertion.indictsWith(value).originallyIndictsWith(value).penalizesBy(2);
    assertThat(value.getEntityHistory()).isEqualTo(entityHistory);
    assertThat(value.getIndexHistory()).isEqualTo(indexHistory);
  }

  @Test
  void explicitShadowUpdateInvalidatesBothIndictmentViews() {
    var solution = TestdataShadowedSolution.generateSolution(1, 1);
    var entity = solution.getEntityList().getFirst();
    entity.setFirstShadow("manual");
    var assertion =
        verify(
                factory ->
                    factory
                        .forEach(TestdataShadowedEntity.class)
                        .filter(fact -> "manual".equals(fact.getFirstShadow()))
                        .penalize(SimpleScore.ONE)
                        .asConstraint("manual"),
                TestdataShadowedSolution.class,
                TestdataShadowedEntity.class)
            .givenSolution(solution);
    assertion.indictsWithExactly(entity).originallyIndictsWithExactly(entity).penalizesBy(1);
    assertThat(entity.getFirstShadow()).isEqualTo("manual");
    assertion
        .settingAllShadowVariables()
        .indictsWithExactly()
        .originallyIndictsWithExactly()
        .hasNoImpact();
    assertThat(entity.getFirstShadow()).isEqualTo(entity.getValue().getCode() + "/firstShadow");
  }

  @Test
  void customAssertionImplementationsKeepUnsupportedDefaults() {
    var assertion = mock(SingleConstraintAssertion.class, CALLS_REAL_METHODS);
    var fact = new Object();
    assertThatThrownBy(() -> assertion.indictsWith(fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.indictsWithMessage("message", fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.indictsWithExactly(fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.indictsExactlyWithMessage("message", fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.originallyIndictsWith(fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.originallyIndictsWithMessage("message", fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.originallyIndictsWithExactly(fact))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> assertion.originallyIndictsExactlyWithMessage("message", fact))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static <Solution_> SingleConstraintVerification<Solution_> verify(
      Function<ConstraintFactory, Constraint> constraint,
      Class<Solution_> solutionClass,
      Class<?>... entityClasses) {
    ConstraintProvider provider = factory -> new Constraint[] {constraint.apply(factory)};
    return ConstraintVerifier.build(provider, solutionClass, entityClasses)
        .verifyThat((ignored, factory) -> constraint.apply(factory));
  }

  private record Key(String code) {}

  private record CallJustification(int call) implements ConstraintJustification {}
}
