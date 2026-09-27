package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AutomaticNodeSharingOperationsTest {

  private static int mappingCalls;

  @ParameterizedTest
  @EnumSource(Operation.class)
  void sharingPreservesIncrementalScoresAndMatchesAndReducesMappingWork(Operation operation) {
    var unshared = evaluate(operation, false);
    var shared = evaluate(operation, true);
    assertThat(unshared.scores()).isEqualTo(operation.scores);
    assertThat(shared.scores()).isEqualTo(operation.scores);
    assertThat(unshared.insertCalls()).isEqualTo(4);
    assertThat(shared.insertCalls()).isEqualTo(2);
    assertThat(unshared.updateCalls()).isEqualTo(2);
    assertThat(shared.updateCalls()).isEqualTo(1);
  }

  private static Result evaluate(Operation operation, boolean sharing) {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var factory =
        BavetConstraintStreamScoreDirectorFactory
            .<TestdataSolution, SimpleScore>buildScoreDirectorFactory(
                descriptor,
                new ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(operation.provider)
                    .withConstraintStreamAutomaticNodeSharing(sharing),
                EnvironmentMode.NO_ASSERT);
    var matchingValue = new TestdataValue("a");
    var otherValue = new TestdataValue("z");
    var first = new TestdataEntity("a", matchingValue);
    var second = new TestdataEntity("bb", matchingValue);
    var facts = new Object[] {matchingValue, otherValue, first, second};
    var session =
        factory.newSession(
            null,
            ConsistencyTracker.frozen(descriptor, facts),
            ConstraintMatchPolicy.ENABLED,
            true);

    mappingCalls = 0;
    for (var fact : facts) {
      session.insert(fact);
    }
    var inserted = session.calculateScore();
    assertMatches(session, operation.matchCounts.get(0), inserted);
    var insertCalls = mappingCalls;

    mappingCalls = 0;
    second.setCode("a");
    session.update(second);
    var updated = session.calculateScore();
    assertMatches(session, operation.matchCounts.get(1), updated);
    var updateCalls = mappingCalls;

    session.retract(first);
    var retracted = session.calculateScore();
    assertMatches(session, operation.matchCounts.get(2), retracted);
    return new Result(List.of(inserted, updated, retracted), insertCalls, updateCalls);
  }

  private static void assertMatches(
      BavetConstraintSession<SimpleScore> session, int count, SimpleScore expectedScore) {
    var totals = session.getConstraintMatchTotalMap();
    assertThat(totals).containsOnlyKeys(ConstraintRef.of("one"), ConstraintRef.of("two"));
    assertThat(totals.values())
        .allSatisfy(
            total -> {
              assertThat(total.getConstraintMatchCount()).isEqualTo(count);
              assertThat(total.getConstraintMatchSet())
                  .allSatisfy(
                      match ->
                          assertThat(match.getConstraintRef()).isEqualTo(total.getConstraintRef()));
            });
    assertThat(totals.get(ConstraintRef.of("two")).getScore())
        .isEqualTo(totals.get(ConstraintRef.of("one")).getScore().multiply(2));
    assertThat(
            totals.values().stream()
                .map(total -> total.getScore())
                .reduce(SimpleScore.ZERO, SimpleScore::add))
        .isEqualTo(expectedScore);
  }

  public static String key(TestdataEntity entity) {
    mappingCalls++;
    return entity.getCode();
  }

  public static int length(TestdataEntity entity) {
    mappingCalls++;
    return entity.getCode().length();
  }

  enum Operation {
    JOIN(JoinProvider.class, List.of(-3, -6, -3), List.of(1, 2, 1)),
    EXISTS(ExistsProvider.class, List.of(-3, -6, -3), List.of(1, 2, 1)),
    MAP(MapProvider.class, List.of(-9, -6, -3), List.of(2, 2, 1)),
    GROUP(GroupProvider.class, List.of(-6, -3, -3), List.of(2, 1, 1));

    private final Class<? extends ConstraintProvider> provider;
    private final List<SimpleScore> scores;
    private final List<Integer> matchCounts;

    Operation(
        Class<? extends ConstraintProvider> provider,
        List<Integer> scores,
        List<Integer> matchCounts) {
      this.provider = provider;
      this.scores = scores.stream().map(SimpleScore::of).toList();
      this.matchCounts = matchCounts;
    }
  }

  private record Result(List<SimpleScore> scores, int insertCalls, int updateCalls) {}

  public static class JoinProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .join(TestdataValue.class, Joiners.equal(entity -> key(entity), TestdataValue::getCode))
            .penalize(SimpleScore.ONE)
            .asConstraint("one"),
        factory
            .forEach(TestdataEntity.class)
            .join(TestdataValue.class, Joiners.equal(entity -> key(entity), TestdataValue::getCode))
            .penalize(SimpleScore.of(2))
            .asConstraint("two")
      };
    }
  }

  public static class ExistsProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .ifExists(
                TestdataValue.class, Joiners.equal(entity -> key(entity), TestdataValue::getCode))
            .penalize(SimpleScore.ONE)
            .asConstraint("one"),
        factory
            .forEach(TestdataEntity.class)
            .ifExists(
                TestdataValue.class, Joiners.equal(entity -> key(entity), TestdataValue::getCode))
            .penalize(SimpleScore.of(2))
            .asConstraint("two")
      };
    }
  }

  public static class MapProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .map(entity -> length(entity))
            .penalize(SimpleScore.ONE, value -> value)
            .asConstraint("one"),
        factory
            .forEach(TestdataEntity.class)
            .map(entity -> length(entity))
            .penalize(SimpleScore.of(2), value -> value)
            .asConstraint("two")
      };
    }
  }

  public static class GroupProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .groupBy(entity -> length(entity))
            .penalize(SimpleScore.ONE)
            .asConstraint("one"),
        factory
            .forEach(TestdataEntity.class)
            .groupBy(entity -> length(entity))
            .penalize(SimpleScore.of(2))
            .asConstraint("two")
      };
    }
  }
}
