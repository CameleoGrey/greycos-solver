package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.BaseValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.FirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.Owner;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.SecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListNoInverseSolution.ValueMarker;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BavetMultipleListFilteringTest {

  @Test
  void inverseFamiliesUseTheirOwnAssignmentAndBasicVariablesForCommonSuperclass() {
    var descriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 2, 1);
    var first = solution.getFirstValueList().getFirst();
    var second = solution.getSecondValueList().getFirst();
    first.setEntity(null);
    second.setQuantity(null);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultipleListSolution, SimpleScore>(
            descriptor,
            streams ->
                new Constraint[] {
                  streams
                      .forEach(TestdataObject.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("common"),
                  streams
                      .forEach(TestdataMultipleListFirstValue.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("first"),
                  streams
                      .forEach(TestdataMultipleListSecondValue.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("second")
                },
            EnvironmentMode.PHASE_ASSERT);
    var session =
        factory.newSession(
            solution, new ConsistencyTracker<>(), ConstraintMatchPolicy.ENABLED, true);
    descriptor.visitAll(solution, session::insert);
    assertMatches(session, "common", 3); // One owner and one assigned value from each family.
    assertMatches(session, "first", 1);
    assertMatches(session, "second", 1);

    second.setQuantity(1);
    session.update(second);
    assertMatches(session, "common", 4);
    assertMatches(session, "second", 2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void noInverseCommonTypesReactToOwnerOnlyUpdatesAndCombineBasicAndConsistencyFilters(
      boolean registeredSuperclass) {
    var descriptor = TestdataMultipleListNoInverseSolution.buildSolutionDescriptor();
    var solution = new TestdataMultipleListNoInverseSolution();
    var owner = new Owner(); // Its uninitialized basic variable must not hide list membership.
    var first = new FirstValue();
    var firstUnassigned = new FirstValue();
    var second = new SecondValue();
    var plain = new BaseValue();
    solution.owners.add(owner);
    solution.firstValues.addAll(List.of(first, firstUnassigned));
    solution.secondValues.add(second);
    solution.otherValues.add(plain);
    owner.first.add(first);
    owner.second.add(second);
    var tracker = new ConsistencyTracker<TestdataMultipleListNoInverseSolution>();
    for (var value : List.of(first, firstUnassigned, second, plain)) {
      tracker
          .getDeclarativeEntityConsistencyState(
              descriptor.findEntityDescriptorOrFail(value.getClass()))
          .setEntityIsInconsistentSkippingProcessor(value, false);
    }
    var sourceClass = registeredSuperclass ? BaseValue.class : ValueMarker.class;
    ConstraintProvider provider =
        streams -> {
          assertThat(streams.forEach(sourceClass)).isSameAs(streams.forEach(sourceClass));
          return new Constraint[] {
            streams.forEach(sourceClass).penalize(SimpleScore.ONE).asConstraint("assigned"),
            streams
                .forEachIncludingUnassigned(sourceClass)
                .penalize(SimpleScore.ONE)
                .asConstraint("consistent"),
            streams.forEachUnfiltered(sourceClass).penalize(SimpleScore.ONE).asConstraint("all"),
            streams.forEach(FirstValue.class).penalize(SimpleScore.ONE).asConstraint("first")
          };
        };
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataMultipleListNoInverseSolution, SimpleScore>(
            descriptor, provider, EnvironmentMode.PHASE_ASSERT);
    var session = factory.newSession(solution, tracker, ConstraintMatchPolicy.ENABLED, true);
    descriptor.visitAll(solution, session::insert);
    assertMatches(session, "assigned", 3);
    assertMatches(session, "consistent", 4);
    assertMatches(session, "all", 4);
    assertMatches(session, "first", 1);

    // Deliberately update only the owner: no value has an inverse/index shadow to trigger updates.
    owner.first.clear();
    owner.second.clear();
    session.update(owner);
    assertMatches(session, "assigned", 1);
    assertMatches(session, "first", 0);

    owner.first.add(firstUnassigned);
    owner.second.add(second);
    session.update(owner);
    assertMatches(session, "assigned", 3);
    assertMatches(session, "first", 1);

    second.basicValue = null;
    session.update(second);
    assertMatches(session, "assigned", 2);
    assertMatches(session, "consistent", 4);

    tracker
        .getDeclarativeEntityConsistencyState(
            descriptor.findEntityDescriptorOrFail(FirstValue.class))
        .setEntityIsInconsistentSkippingProcessor(firstUnassigned, true);
    session.update(firstUnassigned);
    assertMatches(session, "assigned", 1);
    assertMatches(session, "consistent", 3);
    assertMatches(session, "all", 4);
    assertMatches(session, "first", 0);
  }

  @Test
  void frozenTrackerPreservesVerifierNullBasicVariableBehavior() {
    var descriptor = TestdataMultipleListNoInverseSolution.buildSolutionDescriptor();
    var solution = new TestdataMultipleListNoInverseSolution();
    var first = new FirstValue();
    first.basicValue = null;
    var second = new SecondValue();
    second.basicValue = null;
    var owner = new Owner();
    owner.first.add(first);
    owner.second.add(second);
    solution.owners.add(owner);
    solution.firstValues.add(first);
    solution.secondValues.add(second);
    var tracker =
        new ConsistencyTracker<TestdataMultipleListNoInverseSolution>(
            new IdentityHashMap<>(), new HashMap<>(), true);
    tracker.entityToIsInconsistentMap().put(first, false);
    tracker.entityToIsInconsistentMap().put(second, false);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataMultipleListNoInverseSolution, SimpleScore>(
            descriptor,
            streams ->
                new Constraint[] {
                  streams
                      .forEach(ValueMarker.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("assigned")
                },
            EnvironmentMode.PHASE_ASSERT);
    var session = factory.newSession(solution, tracker, ConstraintMatchPolicy.ENABLED, true);
    descriptor.visitAll(solution, session::insert);
    assertMatches(session, "assigned", 2);
  }

  private static void assertMatches(
      BavetConstraintSession<SimpleScore> session, String name, int expected) {
    session.calculateScore();
    assertThat(
            session.getConstraintMatchTotalMap().values().stream()
                .filter(total -> total.getConstraintRef().id().equals(name))
                .findFirst()
                .orElseThrow()
                .getConstraintMatchCount())
        .isEqualTo(expected);
  }
}
