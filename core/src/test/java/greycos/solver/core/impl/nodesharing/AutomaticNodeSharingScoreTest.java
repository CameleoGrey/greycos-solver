package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutomaticNodeSharingScoreTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesStringConcatenationRecipes(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.ConcatenationProvider.class, sharingEnabled);
    assertScore(factory, "x", -1, 1, 0);
    assertScore(factory, "y", 0, 0, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesNestedPredicateImplementations(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.NestedPredicateProvider.class, sharingEnabled);
    assertScore(factory, "x", -1, 1, 0);
    assertScore(factory, "y", -2, 0, 1);
    assertScore(factory, "z", 0, 0, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesBooleanGroupingForEveryInput(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.BooleanGroupingProvider.class, sharingEnabled);
    // Explicit truth table for (A || B) && C and A || (B && C), respectively.
    var firstMatches = new int[] {0, 0, 0, 1, 0, 1, 0, 1};
    var secondMatches = new int[] {0, 0, 0, 1, 1, 1, 1, 1};
    for (var bits = 0; bits < 8; bits++) {
      var code = Integer.toBinaryString(bits | 8).substring(1);
      assertScore(
          factory,
          code,
          -firstMatches[bits] - 2 * secondMatches[bits],
          firstMatches[bits],
          secondMatches[bits]);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesExceptionHandlerTypesAndOrdering(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.ExceptionHandlerProvider.class, sharingEnabled);
    assertScore(factory, "invalid", -2, 0, 1);
    assertScore(factory, "1", -3, 1, 1);
    assertScore(factory, "-1", 0, 0, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void distinguishesClassConstantsFromStrings(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.TypedConstantProvider.class, sharingEnabled);
    assertScore(factory, "class", -1, 1, 0);
    assertScore(factory, "string", -2, 0, 1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesSwitchDestinations(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.SwitchProvider.class, sharingEnabled);
    assertScore(factory, "", -1, 1, 0);
    assertScore(factory, "x", -2, 0, 1);
    assertScore(factory, "xy", -3, 1, 1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesScoresForMixedLambdaKinds(boolean sharingEnabled) {
    var factory = factory(ScoreSharingProviders.MixedLambdaProvider.class, sharingEnabled);
    assertThat(factory.fireAndForget(entity("")).extractScore()).isEqualTo(SimpleScore.ZERO);
    var inliner = factory.fireAndForget(entity("ab"));
    assertThat(inliner.extractScore()).isEqualTo(SimpleScore.of(-7));
    assertThat(inliner.getConstraintMatchTotalMap()).hasSize(6);
    assertMatch(inliner.getConstraintMatchTotalMap(), "ordinary", 1, 1);
    assertMatch(inliner.getConstraintMatchTotalMap(), "serializable", 2, 1);
    assertMatch(inliner.getConstraintMatchTotalMap(), "captured one", 4, 1);
    assertMatch(inliner.getConstraintMatchTotalMap(), "captured three", 8, 0);
    assertThat(factory.fireAndForget(entity("abcd")).extractScore()).isEqualTo(SimpleScore.of(-15));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void preservesSerializableMarkerInterfacesAndCapturedValues(boolean sharingEnabled)
      throws Exception {
    var provider =
        sharingEnabled
            ? (ScoreSharingProviders.MixedLambdaProvider)
                new DefaultConstraintProviderNodeSharer()
                    .buildNodeSharedConstraintProvider(
                        ScoreSharingProviders.MixedLambdaProvider.class)
                    .getDeclaredConstructor()
                    .newInstance()
            : new ScoreSharingProviders.MixedLambdaProvider();
    if (sharingEnabled) {
      assertThat(provider.getClass().getDeclaredMethod("serializablePredicate").getDeclaringClass())
          .isSameAs(provider.getClass());
      assertThat(
              provider
                  .getClass()
                  .getDeclaredMethod("capturedPredicate", int.class)
                  .getDeclaringClass())
          .isSameAs(provider.getClass());
    }
    var ordinary = provider.ordinaryPredicate();
    var serializable = provider.serializablePredicate();
    assertThat(ordinary).isNotInstanceOf(Serializable.class);
    assertThat(serializable)
        .isInstanceOf(Serializable.class)
        .isInstanceOf(ScoreSharingProviders.Marker.class);
    assertThat(serializable).isNotSameAs(ordinary);

    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(serializable);
    }
    Predicate<TestdataEntity> restored;
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Predicate<TestdataEntity>) input.readObject();
    }
    assertThat(restored).isInstanceOf(ScoreSharingProviders.Marker.class);
    assertThat(restored.test(entity(""))).isFalse();
    assertThat(restored.test(entity("x"))).isTrue();

    var shortPredicate = provider.capturedPredicate(1);
    var longPredicate = provider.capturedPredicate(3);
    assertThat(shortPredicate).isNotSameAs(longPredicate);
    assertThat(shortPredicate.test(entity("ab"))).isTrue();
    assertThat(longPredicate.test(entity("ab"))).isFalse();
    assertThat(longPredicate.test(entity("abcd"))).isTrue();
  }

  @Test
  void sharingHalvesPredicateWorkWhilePreservingIncrementalScoresAndMatches() {
    var unshared = runIncrementalSequence(false);
    var shared = runIncrementalSequence(true);
    assertThat(unshared.scores())
        .containsExactly(
            SimpleScore.of(-3000), SimpleScore.ZERO, SimpleScore.ZERO, SimpleScore.of(-3000));
    assertThat(shared.scores()).isEqualTo(unshared.scores());
    assertThat(unshared.insertCalls()).isEqualTo(2000);
    assertThat(unshared.updateCalls()).isEqualTo(2000);
    assertThat(unshared.retractCalls()).isZero();
    assertThat(unshared.reinsertCalls()).isEqualTo(2000);
    assertThat(shared.insertCalls()).isEqualTo(1000);
    assertThat(shared.updateCalls()).isEqualTo(1000);
    assertThat(shared.retractCalls()).isZero();
    assertThat(shared.reinsertCalls()).isEqualTo(1000);
  }

  private static IncrementalResult runIncrementalSequence(boolean sharingEnabled) {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var factory = factory(ScoreSharingProviders.CountingProvider.class, sharingEnabled);
    var entities = new TestdataEntity[1000];
    for (var i = 0; i < entities.length; i++) {
      entities[i] = entity("x");
    }
    var session =
        factory.newSession(
            null,
            ConsistencyTracker.frozen(descriptor, entities),
            ConstraintMatchPolicy.ENABLED,
            true);
    var scores = new ArrayList<SimpleScore>();
    ScoreSharingInvocationCounter.reset();
    for (var entity : entities) {
      session.insert(entity);
    }
    scores.add(session.calculateScore());
    assertIncrementalMatches(session, 1000);
    var insertCalls = ScoreSharingInvocationCounter.calls();

    ScoreSharingInvocationCounter.reset();
    for (var entity : entities) {
      entity.setCode("");
      session.update(entity);
    }
    scores.add(session.calculateScore());
    assertIncrementalMatches(session, 0);
    var updateCalls = ScoreSharingInvocationCounter.calls();

    ScoreSharingInvocationCounter.reset();
    for (var entity : entities) {
      session.retract(entity);
    }
    scores.add(session.calculateScore());
    assertIncrementalMatches(session, 0);
    var retractCalls = ScoreSharingInvocationCounter.calls();

    ScoreSharingInvocationCounter.reset();
    for (var entity : entities) {
      entity.setCode("x");
      session.insert(entity);
    }
    scores.add(session.calculateScore());
    assertIncrementalMatches(session, 1000);
    return new IncrementalResult(
        List.copyOf(scores),
        insertCalls,
        updateCalls,
        retractCalls,
        ScoreSharingInvocationCounter.calls());
  }

  private static void assertIncrementalMatches(
      BavetConstraintSession<SimpleScore> session, int count) {
    var totals = session.getConstraintMatchTotalMap();
    assertThat(totals)
        .containsOnlyKeys(ConstraintRef.of("weight one"), ConstraintRef.of("weight two"));
    assertMatch(totals, "weight one", 1, count);
    assertMatch(totals, "weight two", 2, count);
    assertThat(
            totals.values().stream()
                .map(ConstraintMatchTotal::getScore)
                .reduce(SimpleScore.ZERO, SimpleScore::add))
        .isEqualTo(session.calculateScore());
  }

  private static void assertScore(
      BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore> factory,
      String code,
      int expectedScore,
      int firstCount,
      int secondCount) {
    var inliner = factory.fireAndForget(entity(code));
    assertThat(inliner.extractScore())
        .as("score for code %s", code)
        .isEqualTo(SimpleScore.of(expectedScore));
    var totals = inliner.getConstraintMatchTotalMap();
    assertThat(totals)
        .containsOnlyKeys(
            ConstraintRef.of("first"),
            ConstraintRef.of("second"),
            ConstraintRef.of("shared first"),
            ConstraintRef.of("shared second"));
    assertMatch(totals, "first", 1, firstCount);
    assertMatch(totals, "second", 2, secondCount);
    assertMatch(totals, "shared first", 1, 0);
    assertMatch(totals, "shared second", 1, 0);
  }

  private static void assertMatch(
      Map<ConstraintRef, ConstraintMatchTotal<SimpleScore>> totals,
      String name,
      int weight,
      int count) {
    var total = totals.get(ConstraintRef.of(name));
    assertThat(total).as("constraint %s", name).isNotNull();
    assertThat(total.getConstraintRef()).isEqualTo(ConstraintRef.of(name));
    assertThat(total.getConstraintWeight()).isEqualTo(SimpleScore.of(-weight));
    assertThat(total.getConstraintMatchCount()).isEqualTo(count);
    assertThat(total.getScore()).isEqualTo(SimpleScore.of(-weight * count));
    assertThat(total.getConstraintMatchSet())
        .allSatisfy(
            match -> {
              assertThat(match.getConstraintRef()).isEqualTo(ConstraintRef.of(name));
              assertThat(match.getScore()).isEqualTo(SimpleScore.of(-weight));
            });
  }

  private static BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore> factory(
      Class<? extends ConstraintProvider> providerClass, boolean sharingEnabled) {
    // Every fixture contains a genuinely eligible pair, even when the behavior under test is
    // unshareable.
    assertThat(new ConstraintProviderAnalyzer(providerClass).analyze().hasShareableLambdas())
        .isTrue();
    if (sharingEnabled) {
      assertThat(
              new DefaultConstraintProviderNodeSharer()
                  .buildNodeSharedConstraintProvider(providerClass))
          .isNotSameAs(providerClass);
    }
    return BavetConstraintStreamScoreDirectorFactory.buildScoreDirectorFactory(
        TestdataSolution.buildSolutionDescriptor(),
        new ScoreDirectorFactoryConfig()
            .withConstraintProviderClass(providerClass)
            .withConstraintStreamAutomaticNodeSharing(sharingEnabled),
        EnvironmentMode.NO_ASSERT);
  }

  private static TestdataEntity entity(String code) {
    return new TestdataEntity(code, new TestdataValue("value"));
  }

  private record IncrementalResult(
      List<SimpleScore> scores,
      int insertCalls,
      int updateCalls,
      int retractCalls,
      int reinsertCalls) {}
}
