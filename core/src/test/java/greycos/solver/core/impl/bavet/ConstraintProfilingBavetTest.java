package greycos.solver.core.impl.bavet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.api.score.profile.ConstraintProfile.Outcome;
import greycos.solver.core.api.score.profile.ConstraintProfile.OwnershipState;
import greycos.solver.core.api.score.profile.ConstraintProfile.Role;
import greycos.solver.core.api.score.profile.ConstraintProfile.Stage;
import greycos.solver.core.api.score.profile.ConstraintProfileMeasurement;
import greycos.solver.core.api.score.profile.ConstraintProfileUnit;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishEntity;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishExtra;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishSolution;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishValue;

import org.junit.jupiter.api.Test;

class ConstraintProfilingBavetTest {
  @Test
  void anAlwaysFalseFilterRemainsStructurallyActiveAndOwnsSharedUpstreamWork() {
    var solution = TestdataSolution.generateSolution(2, 3);
    ConstraintProvider provider =
        factory -> {
          var root = factory.forEach(TestdataEntity.class);
          return new Constraint[] {
            root.penalize(SimpleScore.ONE).asConstraint("A"),
            root.filter(entity -> false).penalize(SimpleScore.ONE).asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      var entity = solution.getEntityList().getFirst();
      director.beforeVariableChanged(entity, "value");
      director.afterVariableChanged(entity, "value");
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-3));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertActivity(profile, "B", Activity.ACTIVE);
    assertThat(
            sharedRoot(profile).measurements().stream()
                .filter(row -> row.stage() == Stage.SEARCH)
                .toList())
        .isNotEmpty()
        .allSatisfy(
            row -> {
              assertThat(row.ownershipState()).isEqualTo(OwnershipState.ACTIVE);
              assertThat(names(row)).containsExactly("A", "B");
            });
    assertThat(profile.units().stream().filter(unit -> unit.kind().equals("FILTER")).toList())
        .singleElement()
        .satisfies(
            unit ->
                assertThat(unit.measurements())
                    .isNotEmpty()
                    .allSatisfy(row -> assertThat(names(row)).containsExactly("B")));
    assertReconciles(profile);
  }

  @Test
  void sharedGroupingAndReconvergingInputsKeepTheirOriginalOwners() {
    var solution = TestdataSolution.generateSolution(2, 3);
    ConstraintProvider provider =
        factory -> {
          var grouped = factory.forEach(TestdataEntity.class).groupBy(TestdataEntity::getValue);
          return new Constraint[] {
            grouped.penalize(SimpleScore.ONE).asConstraint("A"),
            grouped
                .join(grouped)
                .filter((left, right) -> left == right)
                .penalize(SimpleScore.ONE)
                .asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-4));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    var groupUnits =
        profile.units().stream().filter(unit -> unit.kind().equals("GROUP_BY")).toList();
    assertThat(groupUnits)
        .extracting(ConstraintProfileUnit::role)
        .containsExactlyInAnyOrder(Role.LIFECYCLE, Role.PROPAGATOR);
    assertThat(groupUnits)
        .allSatisfy(
            unit ->
                assertThat(unit.measurements())
                    .isNotEmpty()
                    .allSatisfy(row -> assertThat(names(row)).containsExactly("A", "B")));
    for (var role : List.of(Role.LEFT_INPUT, Role.RIGHT_INPUT)) {
      assertThat(profile.units().stream().filter(unit -> unit.role() == role).toList())
          .singleElement()
          .satisfies(
              unit -> {
                assertThat(unit.measurements())
                    .allSatisfy(row -> assertThat(names(row)).containsExactly("B"));
                assertThat(
                        unit.measurements().stream()
                            .mapToLong(ConstraintProfileMeasurement::eventCount)
                            .sum())
                    .isEqualTo(2);
              });
    }
    assertThat(profile.units().stream().filter(unit -> unit.role() == Role.ROOT).toList())
        .singleElement()
        .satisfies(
            unit ->
                assertThat(
                        unit.measurements().stream()
                            .filter(row -> row.operation() == Operation.INSERT)
                            .mapToLong(ConstraintProfileMeasurement::eventCount)
                            .sum())
                    .isEqualTo(3));
    assertReconciles(profile);
  }

  @Test
  void activeIfNotExistsScorerDoesNotOwnItsPrunedRightInputRoute() {
    var solution = TestdataLavishSolution.generateSolution();
    ConstraintProvider provider =
        factory -> {
          var sharedRight = factory.forEach(TestdataLavishEntity.class);
          var deadRight =
              sharedRight.join(TestdataLavishExtra.class).map((entity, extra) -> entity);
          return new Constraint[] {
            sharedRight.penalize(SimpleScore.ONE).asConstraint("A"),
            factory
                .forEach(TestdataLavishValue.class)
                .ifNotExists(deadRight)
                .penalize(SimpleScore.ONE)
                .asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = lavishDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      director.beforeVariableChanged(solution.getFirstEntity(), "value");
      director.afterVariableChanged(solution.getFirstEntity(), "value");
      assertThat(director.calculateScore().raw())
          .isEqualTo(
              SimpleScore.of(-solution.getEntityList().size() - solution.getValueList().size()));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertActivity(profile, "B", Activity.ACTIVE);
    var root = sharedRoot(profile);
    assertThat(root.measurements())
        .anySatisfy(
            row -> {
              assertThat(row.ownershipState()).isEqualTo(OwnershipState.ENABLED_CANDIDATES);
              assertThat(row.stage()).isEqualTo(Stage.INITIALIZATION);
              assertThat(names(row)).containsExactly("A", "B");
            });
    assertThat(root.measurements().stream().filter(row -> row.stage() == Stage.SEARCH).toList())
        .isNotEmpty()
        .allSatisfy(row -> assertThat(names(row)).containsExactly("A"));
    assertReconciles(profile);
  }

  @Test
  void candidateOwnersRemainHistoricalWhenOneSharedBranchIsPruned() {
    var solution = TestdataLavishSolution.generateSolution();
    ConstraintProvider provider =
        factory -> {
          var root = factory.forEach(TestdataLavishEntity.class);
          return new Constraint[] {
            root.penalize(SimpleScore.ONE).asConstraint("A"),
            root.join(TestdataLavishExtra.class).penalize(SimpleScore.ONE).asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = lavishDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      var entity = solution.getFirstEntity();
      director.beforeVariableChanged(entity, "value");
      entity.setValue(solution.getFirstValue());
      director.afterVariableChanged(entity, "value");
      assertThat(director.calculateScore().raw())
          .isEqualTo(SimpleScore.of(-solution.getEntityList().size()));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    var root = sharedRoot(profile);
    assertThat(root.measurements())
        .anySatisfy(
            row -> {
              assertThat(row.ownershipState()).isEqualTo(OwnershipState.ENABLED_CANDIDATES);
              assertThat(row.stage()).isEqualTo(Stage.INITIALIZATION);
              assertThat(names(row)).containsExactly("A", "B");
              assertThat(row.eventCount()).isPositive();
            });
    assertThat(root.measurements().stream().filter(row -> row.stage() == Stage.SEARCH).toList())
        .isNotEmpty()
        .allSatisfy(
            row -> {
              assertThat(row.ownershipState()).isEqualTo(OwnershipState.ACTIVE);
              assertThat(names(row)).containsExactly("A");
            });
    assertActivity(profile, "A", Activity.ACTIVE);
    assertActivity(profile, "B", Activity.INACTIVE);
    assertReconciles(profile);
  }

  @Test
  void activeConcatScorerDoesNotOwnItsPrunedInputRoute() {
    var solution = TestdataLavishSolution.generateSolution();
    ConstraintProvider provider =
        factory -> {
          var root = factory.forEach(TestdataLavishEntity.class);
          var deadRoute =
              root.join(TestdataLavishExtra.class).map((entity, extra) -> (Object) entity);
          var liveRoute = factory.forEach(TestdataLavishValue.class).map(value -> (Object) value);
          return new Constraint[] {
            root.penalize(SimpleScore.ONE).asConstraint("A"),
            deadRoute.concat(liveRoute).penalize(SimpleScore.ONE).asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = lavishDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      director.beforeVariableChanged(solution.getFirstEntity(), "value");
      director.afterVariableChanged(solution.getFirstEntity(), "value");
      assertThat(director.calculateScore().raw())
          .isEqualTo(
              SimpleScore.of(-solution.getEntityList().size() - solution.getValueList().size()));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertActivity(profile, "B", Activity.ACTIVE);
    assertThat(
            sharedRoot(profile).measurements().stream()
                .filter(row -> row.stage() == Stage.SEARCH)
                .toList())
        .isNotEmpty()
        .allSatisfy(row -> assertThat(names(row)).containsExactly("A"));
    assertReconciles(profile);
  }

  @Test
  void sharedFilterAndReconvergingInputsHaveExactDistinctOwners() {
    var solution = TestdataSolution.generateSolution(2, 3);
    ConstraintProvider provider =
        factory -> {
          var shared = factory.forEach(TestdataEntity.class).filter(entity -> true);
          return new Constraint[] {
            shared.penalize(SimpleScore.ONE).asConstraint("A"),
            shared.join(shared).penalize(SimpleScore.ONE).asConstraint("B")
          };
        };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-12));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertThat(profile.units().stream().filter(unit -> unit.kind().equals("FILTER")).toList())
        .singleElement()
        .satisfies(
            unit ->
                assertThat(unit.measurements())
                    .allSatisfy(row -> assertThat(names(row)).containsExactly("A", "B")));
    for (var role : List.of(Role.LEFT_INPUT, Role.RIGHT_INPUT)) {
      assertThat(profile.units().stream().filter(unit -> unit.role() == role).toList())
          .singleElement()
          .satisfies(
              unit -> {
                assertThat(unit.measurements())
                    .allSatisfy(row -> assertThat(names(row)).containsExactly("B"));
                assertThat(
                        unit.measurements().stream()
                            .mapToLong(ConstraintProfileMeasurement::eventCount)
                            .sum())
                    .isEqualTo(3);
              });
    }
    assertReconciles(profile);
  }

  @Test
  void deferredPreparationIsMeasuredAndPreservesPreloadAndScores() {
    var solution = TestdataSolution.generateSolution(2, 3);
    var predicateCalls = new AtomicLong();
    ConstraintProvider provider =
        factory -> {
          var source = factory.forEach(TestdataEntity.class);
          return new Constraint[] {
            source
                .join(
                    source.map(entity -> entity).map(entity -> entity),
                    Joiners.filtering(
                        (a, b) -> {
                          predicateCalls.incrementAndGet();
                          return a.getValue() == b.getValue();
                        }))
                .penalize(SimpleScore.ONE)
                .asConstraint("deferred")
          };
        };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      assertThat(predicateCalls.get()).isEqualTo(9);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-5));
      var entity = solution.getEntityList().get(1);
      director.beforeVariableChanged(entity, "value");
      entity.setValue(solution.getValueList().getFirst());
      director.afterVariableChanged(entity, "value");
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-9));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertThat(profile.units().stream().filter(unit -> unit.role() == Role.PREPARATION).toList())
        .singleElement()
        .satisfies(
            unit ->
                assertThat(unit.measurements())
                    .anySatisfy(
                        row -> {
                          assertThat(row.stage()).isEqualTo(Stage.SEARCH);
                          assertThat(row.operation()).isEqualTo(Operation.PREPARE);
                          assertThat(row.eventCount()).isEqualTo(1);
                          assertThat(row.exclusiveNanos()).isPositive();
                        }));
    try (var replay = basicDirector(provider, null)) {
      replay.setWorkingSolution(solution);
      assertThat(replay.calculateScore().raw()).isEqualTo(SimpleScore.of(-9));
    }
    assertReconciles(profile);
  }

  @Test
  void precomputeHasOnlyOuterUnitsAndItsInternalEvaluationIsIncluded() {
    var solution = TestdataSolution.generateSolution(2, 3);
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory
                  .precompute(
                      precompute ->
                          precompute
                              .forEachUnfiltered(TestdataValue.class)
                              .map(value -> value)
                              .filter(value -> true))
                  .penalize(SimpleScore.ONE)
                  .asConstraint("precomputed")
            };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertThat(profile.units())
        .allSatisfy(unit -> assertThat(unit.kind()).isIn("PRECOMPUTE", "SCORING"));
    assertThat(
            profile.units().stream()
                .filter(unit -> unit.kind().equals("PRECOMPUTE") && unit.role() == Role.PROPAGATOR)
                .toList())
        .singleElement()
        .satisfies(
            unit ->
                assertThat(unit.measurements())
                    .anySatisfy(
                        row -> {
                          assertThat(row.operation()).isEqualTo(Operation.RETRACT);
                          assertThat(row.exclusiveNanos()).isPositive();
                          assertThat(names(row)).containsExactly("precomputed");
                        }));
    assertReconciles(profile);
  }

  @Test
  void disabledWeightsStillPublishAnEmptyCompletedGeneration() {
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory
                  .forEach(TestdataEntity.class)
                  .penalize(SimpleScore.ZERO)
                  .asConstraint("disabled")
            };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(TestdataSolution.generateSolution(2, 3));
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertThat(profile.units()).isEmpty();
    assertThat(profile.generations())
        .singleElement()
        .satisfies(generation -> assertThat(generation.initializationCompleted()).isTrue());
    assertActivity(profile, "disabled", Activity.DISABLED_WEIGHT);
    assertThat(profile.combined().eventCount()).isZero();
    assertThat(profile.combined().measuredNanos()).isZero();
    assertThat(profile.combined().scoringWallNanos()).isPositive();
    assertReconciles(profile);
  }

  @Test
  void rebuiltSessionsKeepDistinctGenerationsAndUnitIdentities() {
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory.forEach(TestdataEntity.class).penalize(SimpleScore.ONE).asConstraint("A")
            };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      director.setWorkingSolution(TestdataSolution.generateSolution(2, 3));
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-3));
      director.setWorkingSolution(TestdataSolution.generateSolution(2, 5));
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-5));
    }
    var profile = profiler.freeze(Outcome.COMPLETED);
    assertThat(profile.generations()).hasSize(2);
    assertThat(profile.units()).extracting(ConstraintProfileUnit::unitId).doesNotHaveDuplicates();
    assertThat(profile.units().stream().filter(unit -> unit.role() == Role.ROOT).toList())
        .hasSize(2)
        .allSatisfy(unit -> assertThat(unit.measurements()).isNotEmpty());
    assertReconciles(profile);
  }

  @Test
  void exceptionalFirstSettlementPreservesErrorAndInitializationCounters() {
    var failure = new AssertionError("deliberate predicate failure");
    Predicate<TestdataEntity> predicate =
        entity -> {
          throw failure;
        };
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory
                  .forEach(TestdataEntity.class)
                  .filter(predicate)
                  .penalize(SimpleScore.ONE)
                  .asConstraint("failure")
            };
    var profiler = profiler();
    try (var director = basicDirector(provider, profiler)) {
      assertThatThrownBy(() -> director.setWorkingSolution(TestdataSolution.generateSolution(2, 3)))
          .isSameAs(failure);
    }
    var profile = profiler.freeze(Outcome.FAILED);
    assertThat(profile.generations())
        .singleElement()
        .satisfies(generation -> assertThat(generation.initializationCompleted()).isFalse());
    assertThat(profile.initialization().failureCount()).isGreaterThanOrEqualTo(2);
    assertThat(profile.search().eventCount()).isZero();
    assertReconciles(profile);
  }

  @Test
  void disabledModeHasNoProfilerOrSourceMetadata() {
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory
                  .forEach(TestdataEntity.class)
                  .filter(entity -> true)
                  .penalize(SimpleScore.ONE)
                  .asConstraint("A")
            };
    try (var director = basicDirector(provider, null)) {
      director.setWorkingSolution(TestdataSolution.generateSolution(2, 3));
      assertThat(director.getSession().getNodeNetwork().getConstraintProfiler()).isNull();
      AbstractBavetNodeNetwork network = director.getSession().getNodeNetwork();
      assertThat(network.getNodes())
          .allSatisfy(node -> assertThat(node.getLocationSet()).isEmpty());
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-3));
    }
  }

  private static DefaultConstraintProfiler profiler() {
    return new DefaultConstraintProfiler(new AtomicLong()::getAndIncrement);
  }

  private static BavetConstraintStreamScoreDirector<TestdataSolution, SimpleScore> basicDirector(
      ConstraintProvider provider, DefaultConstraintProfiler profiler) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            provider,
            EnvironmentMode.NO_ASSERT,
            profiler != null);
    return factory.createScoreDirectorBuilder().withConstraintProfiler(profiler).build();
  }

  private static BavetConstraintStreamScoreDirector<TestdataLavishSolution, SimpleScore>
      lavishDirector(ConstraintProvider provider, DefaultConstraintProfiler profiler) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataLavishSolution, SimpleScore>(
            TestdataLavishSolution.buildSolutionDescriptor(),
            provider,
            EnvironmentMode.NO_ASSERT,
            true);
    return factory.createScoreDirectorBuilder().withConstraintProfiler(profiler).build();
  }

  private static List<String> names(ConstraintProfileMeasurement row) {
    return row.owners().stream().map(ConstraintRef::id).toList();
  }

  private static ConstraintProfileUnit sharedRoot(ConstraintProfile profile) {
    return profile.units().stream()
        .filter(unit -> unit.role() == Role.ROOT)
        .filter(
            unit ->
                unit.measurements().stream().anyMatch(row -> names(row).equals(List.of("A", "B"))))
        .findFirst()
        .orElseThrow();
  }

  private static void assertActivity(ConstraintProfile profile, String name, Activity activity) {
    assertThat(profile.generations().getFirst().constraints())
        .anySatisfy(
            constraint -> {
              assertThat(constraint.constraintRef().id()).isEqualTo(name);
              assertThat(constraint.activity()).isEqualTo(activity);
            });
  }

  private static void assertReconciles(ConstraintProfile profile) {
    for (var section : List.of(profile.initialization(), profile.search(), profile.combined())) {
      assertThat(section.measuredNanos()).isBetween(0L, section.scoringWallNanos());
      assertThat(section.measuredNanos() + section.unmeasuredNanos())
          .isEqualTo(section.scoringWallNanos());
      assertThat(section.buckets().stream().mapToLong(bucket -> bucket.exclusiveNanos()).sum())
          .isEqualTo(section.measuredNanos());
    }
  }
}
