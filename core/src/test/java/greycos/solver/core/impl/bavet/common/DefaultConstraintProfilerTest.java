package greycos.solver.core.impl.bavet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.api.score.profile.ConstraintProfile.Outcome;
import greycos.solver.core.api.score.profile.ConstraintProfile.OwnershipState;
import greycos.solver.core.api.score.profile.ConstraintProfile.Role;
import greycos.solver.core.api.score.profile.ConstraintProfile.Stage;
import greycos.solver.core.api.score.profile.ConstraintProfileBucket;
import greycos.solver.core.api.score.profile.ConstraintProfileMeasurement;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Generation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;

import org.junit.jupiter.api.Test;

class DefaultConstraintProfilerTest {
  private static final ConstraintRef A = ConstraintRef.of("A");
  private static final ConstraintRef B = ConstraintRef.of("B");
  private final AtomicLong clock = new AtomicLong();
  private final DefaultConstraintProfiler profiler = new DefaultConstraintProfiler(clock::get);
  private final Generation generation = profiler.newGeneration(List.of(A, B));

  @Test
  void exclusiveParentAndChild() {
    Unit parent = unit();
    Unit child = unit();
    candidates(Map.of(parent, List.of(A), child, List.of(B)));
    wall(
        Stage.INITIALIZATION,
        () ->
            invoke(
                parent,
                () -> {
                  clock.set(2);
                  invoke(child, () -> clock.set(5));
                  clock.set(10);
                }));
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(bucket(result, A).exclusiveNanos()).isEqualTo(7);
    assertThat(bucket(result, B).exclusiveNanos()).isEqualTo(3);
    assertThat(result.initialization().scoringWallNanos()).isEqualTo(10);
    assertThat(result.initialization().measuredNanos()).isEqualTo(10);
    assertThat(result.combined().eventCount()).isEqualTo(2);
  }

  @Test
  void siblingsRecursionAndUnmeasuredDispatch() {
    Unit unit = unit();
    candidates(Map.of(unit, List.of(A)));
    wall(
        Stage.SEARCH,
        () -> {
          clock.set(1);
          invoke(
              unit,
              () -> {
                clock.set(2);
                invoke(unit, () -> clock.set(4));
                clock.set(5);
                invoke(unit, () -> clock.set(8));
                clock.set(10);
              });
          clock.set(12);
        });
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.search().scoringWallNanos()).isEqualTo(12);
    assertThat(result.search().measuredNanos()).isEqualTo(9);
    assertThat(result.search().unmeasuredNanos()).isEqualTo(3);
    assertThat(result.search().coverage()).isEqualTo(0.75);
    assertThat(bucket(result, A).eventCount()).isEqualTo(3);
  }

  @Test
  void nestedWallSectionsSplitInvocationWithoutDuplicatingCounts() {
    Unit unit = unit();
    candidates(Map.of(unit, List.of(A)));
    wall(
        Stage.SEARCH,
        () ->
            invoke(
                unit,
                () -> {
                  clock.set(2);
                  wall(Stage.INITIALIZATION, () -> clock.set(5));
                  clock.set(10);
                }));
    // A complete initialization invocation must not make a partial bucket's average available.
    wall(Stage.INITIALIZATION, () -> invoke(unit, () -> clock.set(12)));
    var result = profiler.freeze(Outcome.COMPLETED);
    var initialization = result.initialization().buckets().getFirst();
    var search = result.search().buckets().getFirst();
    assertThat(initialization.exclusiveNanos()).isEqualTo(5);
    assertThat(initialization.eventCount()).isEqualTo(1);
    assertThat(initialization.partialInvocations()).isTrue();
    assertThat(initialization.averageNanosPerCall()).isNull();
    assertThat(search.exclusiveNanos()).isEqualTo(7);
    assertThat(search.eventCount()).isEqualTo(1);
    assertThat(search.partialInvocations()).isTrue();
    assertThat(search.averageNanosPerCall()).isNull();
    assertThat(bucket(result, A).exclusiveNanos()).isEqualTo(12);
    assertThat(bucket(result, A).partialInvocations()).isFalse();
    assertThat(bucket(result, A).averageNanosPerCall()).isEqualTo(6.0);
  }

  @Test
  void nestedUnitInAnotherSectionDoesNotBecomeParentExclusiveTime() {
    Unit parent = unit();
    Unit child = unit();
    candidates(Map.of(parent, List.of(A), child, List.of(B)));
    wall(
        Stage.SEARCH,
        () ->
            invoke(
                parent,
                () -> {
                  clock.set(2);
                  wall(Stage.INITIALIZATION, () -> invoke(child, () -> clock.set(5)));
                  clock.set(10);
                }));
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.initialization().buckets()).hasSize(1);
    assertThat(result.initialization().buckets().getFirst().owners()).containsExactly(B);
    assertThat(result.search().buckets().getFirst().exclusiveNanos()).isEqualTo(7);
    assertThat(result.search().buckets().getFirst().partialInvocations()).isFalse();
    assertThat(result.combined().scoringWallNanos()).isEqualTo(10);
  }

  @Test
  void splitTimingRowWithNoEnteredCallsHasNoAverage() {
    Unit unit = unit();
    wall(
        Stage.SEARCH,
        () ->
            invoke(
                unit,
                () -> {
                  clock.set(2);
                  wall(Stage.INITIALIZATION, () -> clock.set(5));
                  clock.set(10);
                }));
    var result = profiler.freeze(Outcome.COMPLETED);
    var bucket = result.initialization().buckets().getFirst();
    assertThat(bucket.exclusiveNanos()).isEqualTo(3);
    assertThat(bucket.eventCount()).isZero();
    assertThat(bucket.partialInvocations()).isTrue();
    assertThat(bucket.averageNanosPerCall()).isNull();
    assertThat(result.combined().eventCount()).isEqualTo(1);
    assertThat(result.combined().buckets().getFirst().averageNanosPerCall()).isEqualTo(10.0);
  }

  @Test
  void loggingIsExcludedEvenInsideAnOpenUnitAndNestedWall() {
    Unit unit = unit();
    wall(
        Stage.INITIALIZATION,
        () ->
            invoke(
                unit,
                () -> {
                  clock.set(2);
                  profiler.enterExcluded();
                  try {
                    clock.set(102);
                  } finally {
                    profiler.exitExcluded(null);
                  }
                  wall(Stage.INITIALIZATION, () -> clock.set(110));
                }));
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.combined().scoringWallNanos()).isEqualTo(10);
    assertThat(result.combined().measuredNanos()).isEqualTo(10);
  }

  @Test
  void epochIsCapturedOnEntryAndSharedOwnersAreCountedOnce() {
    Unit unit = unit();
    candidates(Map.of(unit, List.of(A, B, A)));
    wall(
        Stage.INITIALIZATION,
        () ->
            invoke(
                unit,
                () -> {
                  clock.set(2);
                  generation.setConstraintActivity(A, Activity.ACTIVE);
                  generation.setConstraintActivity(B, Activity.INACTIVE);
                  generation.resolveOwnership(OwnershipState.ACTIVE, Map.of(unit, List.of(A)));
                  clock.set(4);
                }));
    generation.completeInitialization();
    wall(generation.stage(), () -> invoke(unit, () -> clock.set(7)));
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.initialization().buckets().getFirst().owners()).containsExactly(A, B);
    assertThat(result.initialization().buckets().getFirst().exclusiveNanos()).isEqualTo(4);
    assertThat(result.search().buckets().getFirst().owners()).containsExactly(A);
    assertThat(result.combined().measuredNanos()).isEqualTo(7);
    assertThat(result.units().getFirst().measurements()).hasSize(2);
    assertThat(result.generations().getFirst().initializationCompleted()).isTrue();
  }

  @Test
  void nestedFailureAndErrorPreserveIdentityAndRestoreBothStacks() {
    Unit unit = unit();
    candidates(Map.of(unit, List.of(A)));
    var failure = new AssertionError("delegate failure");
    assertThatThrownBy(
            () ->
                wall(
                    Stage.INITIALIZATION,
                    () ->
                        invoke(
                            unit,
                            () -> {
                              clock.set(2);
                              invoke(
                                  unit,
                                  () -> {
                                    clock.set(5);
                                    throw failure;
                                  });
                            })))
        .isSameAs(failure);
    wall(Stage.SEARCH, () -> invoke(unit, () -> clock.set(8)));
    var result = profiler.freeze(Outcome.FAILED);
    assertThat(result.combined().eventCount()).isEqualTo(3);
    assertThat(result.combined().failureCount()).isEqualTo(2);
    assertThat(result.initialization().measuredNanos()).isEqualTo(5);
    assertThat(result.search().measuredNanos()).isEqualTo(3);
  }

  @Test
  void recordingFailureIsSuppressedAndCannotProduceFabricatedProfile() {
    Unit unit = unit();
    var failure = new IllegalArgumentException("original");
    assertThatThrownBy(
            () ->
                wall(
                    Stage.INITIALIZATION,
                    () ->
                        invoke(
                            unit,
                            () -> {
                              clock.set(-1);
                              throw failure;
                            })))
        .isSameAs(failure);
    assertThat(failure.getSuppressed()).isNotEmpty();
    assertThatThrownBy(() -> profiler.freeze(Outcome.FAILED))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accounting failure");
  }

  @Test
  void emptyCallbacksAndEmptyWallDenominators() {
    Unit unit = unit();
    wall(Stage.INITIALIZATION, () -> invoke(unit, () -> {}));
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.combined().eventCount()).isEqualTo(1);
    assertThat(result.combined().coverage()).isNull();
    assertThat(result.combined().buckets().getFirst().owners()).isEmpty();
    assertThat(result.combined().buckets().getFirst().measuredShare()).isNull();
    assertThat(result.combined().buckets().getFirst().averageNanosPerCall()).isEqualTo(0.0);
  }

  @Test
  void emptyPropagationPassesStillCountEachOperation() {
    Unit unit = profiler.registerUnit(generation, "FOR_EACH", Role.PROPAGATOR, List.of());
    var queue = new StaticPropagationQueue<Tuple>(TupleLifecycle.recording());
    var propagation = new ProfilingPropagator(profiler, unit, queue);
    wall(Stage.INITIALIZATION, propagation::propagateEverything);
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.combined().eventCount()).isEqualTo(3);
    assertThat(result.units().getFirst().measurements())
        .extracting(row -> row.operation())
        .containsExactly(Operation.INSERT, Operation.UPDATE, Operation.RETRACT);
  }

  @Test
  void preparationHasSeparateOperationAndFailureCounter() {
    Unit unit = profiler.registerUnit(generation, "JOIN", Role.PREPARATION, List.of());
    var delegate = mock(DeferredSettleAware.class);
    var failure = new IllegalStateException("preparation failed");
    org.mockito.Mockito.doAnswer(
            invocation -> {
              clock.set(6);
              throw failure;
            })
        .when(delegate)
        .prepareForSettle();
    var preparation = new ProfilingDeferredSettleAware(profiler, unit, delegate);
    assertThatThrownBy(() -> wall(Stage.SEARCH, preparation::prepareForSettle)).isSameAs(failure);
    var row = profiler.freeze(Outcome.FAILED).units().getFirst().measurements().getFirst();
    assertThat(row.operation()).isEqualTo(Operation.PREPARE);
    assertThat(row.exclusiveNanos()).isEqualTo(6);
    assertThat(row.eventCount()).isEqualTo(1);
    assertThat(row.failureCount()).isEqualTo(1);
  }

  @Test
  void generationsAndUnitIdsNeverAlias() {
    Unit first = unit();
    var secondGeneration = profiler.newGeneration(List.of(A));
    var second = profiler.registerUnit(secondGeneration, "FILTER", Role.LIFECYCLE, List.of());
    wall(
        Stage.INITIALIZATION,
        () -> {
          invoke(first, () -> clock.set(2));
          invoke(second, () -> clock.set(5));
        });
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.generations()).hasSize(2);
    assertThat(result.units()).extracting(row -> row.unitId()).doesNotHaveDuplicates();
    assertThat(result.combined().measuredNanos()).isEqualTo(5);
    assertThatThrownBy(() -> profiler.newGeneration(List.of(A)))
        .hasMessageContaining("already been frozen");
    assertThatThrownBy(() -> profiler.freeze(Outcome.COMPLETED))
        .hasMessageContaining("already been frozen");
  }

  @Test
  void stackGrowthAndFinalSnapshotDoNotConsultClock() {
    Unit unit = unit();
    wall(Stage.SEARCH, () -> recurse(unit, 40));
    clock.set(-100);
    var result = profiler.freeze(Outcome.COMPLETED);
    assertThat(result.combined().eventCount()).isEqualTo(41);
    assertThat(result.combined().measuredNanos()).isEqualTo(1);
  }

  @Test
  void sameSectionWallsKeepUnitBoundariesAndUnmeasuredDispatchWithoutExtraClockReads() {
    var reads = new AtomicLong();
    var collecting =
        new DefaultConstraintProfiler(
            () -> {
              reads.incrementAndGet();
              return clock.get();
            });
    var run = collecting.newGeneration(List.of(A));
    var measured = collecting.registerUnit(run, "FILTER", Role.LIFECYCLE, List.of());
    collecting.enterWall(Stage.SEARCH);
    clock.set(2);
    collecting.enterWall(Stage.SEARCH);
    clock.set(3);
    collecting.enterUnit(measured, Operation.INSERT);
    clock.set(7);
    collecting.exitUnit(null);
    clock.set(9);
    collecting.exitWall(null);
    clock.set(12);
    collecting.exitWall(null);

    var result = collecting.freeze(Outcome.COMPLETED);
    assertThat(reads.get()).isEqualTo(4);
    assertThat(result.search().scoringWallNanos()).isEqualTo(12);
    assertThat(result.search().measuredNanos()).isEqualTo(4);
    assertThat(result.search().unmeasuredNanos()).isEqualTo(8);
    assertThat(result.search().eventCount()).isEqualTo(1);
  }

  @Test
  void nestedExclusionsAndSectionChangesRetainTheirClockCheckpoints() {
    var reads = new AtomicLong();
    var collecting =
        new DefaultConstraintProfiler(
            () -> {
              reads.incrementAndGet();
              return clock.get();
            });
    collecting.enterWall(Stage.SEARCH);
    clock.set(2);
    collecting.enterExcluded();
    clock.set(3);
    collecting.enterExcluded();
    clock.set(5);
    collecting.enterWall(Stage.INITIALIZATION);
    clock.set(8);
    collecting.exitWall(null);
    clock.set(10);
    collecting.exitExcluded(null);
    clock.set(12);
    collecting.exitExcluded(null);
    clock.set(15);
    collecting.exitWall(null);

    var result = collecting.freeze(Outcome.COMPLETED);
    assertThat(reads.get()).isEqualTo(6);
    assertThat(result.initialization().scoringWallNanos()).isEqualTo(3);
    assertThat(result.search().scoringWallNanos()).isEqualTo(5);
    assertThat(result.combined().eventCount()).isZero();
    assertThat(result.combined().unmeasuredNanos()).isEqualTo(8);
  }

  @Test
  void accountingMatchesCompletedIntervalModelAcrossNestedScripts() {
    for (int seed = 0; seed < 32; seed++) {
      var script = new IntervalScript();
      exerciseIntervals(script, new Random(seed), 4);
      script.assertMatches();
    }
  }

  private static void exerciseIntervals(IntervalScript script, Random random, int depth) {
    script.wall(random.nextBoolean() ? Stage.INITIALIZATION : Stage.SEARCH);
    script.advance(random.nextInt(4));
    script.enter(random.nextInt(2), Operation.values()[random.nextInt(Operation.values().length)]);
    script.advance(random.nextInt(6));
    if (random.nextBoolean()) script.changeOwners();
    if (depth > 0) exerciseIntervals(script, random, depth - 1);
    script.advance(random.nextInt(6));
    script.wall(null);
    script.advance(random.nextInt(8));
    if (depth > 0 && random.nextBoolean()) exerciseIntervals(script, random, depth - 1);
    script.advance(random.nextInt(8));
    script.endWall();
    script.advance(random.nextInt(6));
    script.exit(random.nextBoolean());
    script.advance(random.nextInt(4));
    script.endWall();
  }

  /**
   * Independent oracle: record complete invocation intervals and wall-section spans, then
   * intersect them and subtract direct child intervals after the script has finished.
   */
  private static final class IntervalScript {
    private final AtomicLong clock = new AtomicLong();
    private final DefaultConstraintProfiler collecting = new DefaultConstraintProfiler(clock::get);
    private final Generation generation = collecting.newGeneration(List.of(A, B));
    private final Unit[] units = {
      collecting.registerUnit(generation, "FILTER", Role.LIFECYCLE, List.of()),
      collecting.registerUnit(generation, "JOIN", Role.LEFT_INPUT, List.of())
    };
    private final List<Stage> walls = new ArrayList<>();
    private final List<InvocationInterval> calls = new ArrayList<>();
    private final List<InvocationInterval> openCalls = new ArrayList<>();
    private final List<WallInterval> spans = new ArrayList<>();
    private List<List<ConstraintRef>> owners = List.of(List.of(A), List.of(B));
    private OwnershipState state = OwnershipState.ENABLED_CANDIDATES;
    private int epoch = 1;

    private IntervalScript() {
      generation.resolveOwnership(state, Map.of(units[0], owners.get(0), units[1], owners.get(1)));
    }

    private void wall(Stage stage) {
      if (stage == null) collecting.enterExcluded();
      else collecting.enterWall(stage);
      walls.add(stage);
    }

    private void endWall() {
      if (walls.removeLast() == null) collecting.exitExcluded(null);
      else collecting.exitWall(null);
    }

    private void advance(long elapsed) {
      long start = clock.get();
      long end = clock.addAndGet(elapsed);
      if (!walls.isEmpty() && walls.getLast() != null) {
        spans.add(new WallInterval(start, end, walls.getLast()));
      }
    }

    private void enter(int index, Operation operation) {
      collecting.enterUnit(units[index], operation);
      var call =
          new InvocationInterval(
              units[index].unitId(),
              epoch,
              state,
              operation,
              owners.get(index),
              walls.getLast(),
              clock.get());
      if (!openCalls.isEmpty()) openCalls.getLast().children.add(call);
      calls.add(call);
      openCalls.add(call);
    }

    private void exit(boolean failed) {
      collecting.exitUnit(failed ? new AssertionError("script callback failure") : null);
      var call = openCalls.removeLast();
      call.end = clock.get();
      call.failed = failed;
    }

    private void changeOwners() {
      owners = epoch % 2 == 0 ? List.of(List.of(B), List.of(A)) : List.of(List.of(A, B), List.of());
      state = OwnershipState.ACTIVE;
      generation.setConstraintActivity(A, Activity.ACTIVE);
      generation.setConstraintActivity(B, Activity.ACTIVE);
      generation.resolveOwnership(state, Map.of(units[0], owners.get(0), units[1], owners.get(1)));
      epoch++;
    }

    private long overlap(InvocationInterval call, Stage stage) {
      return spans.stream()
          .filter(span -> span.stage == stage)
          .mapToLong(
              span -> Math.max(0, Math.min(call.end, span.end) - Math.max(call.start, span.start)))
          .sum();
    }

    private void assertMatches() {
      assertThat(openCalls).isEmpty();
      assertThat(walls).isEmpty();
      var expectedCounters = new HashMap<MeasurementKey, ExpectedCounter>();
      for (var call : calls) {
        long[] exclusive = new long[Stage.values().length];
        for (Stage stage : Stage.values()) {
          exclusive[stage.ordinal()] = overlap(call, stage);
          for (var child : call.children) exclusive[stage.ordinal()] -= overlap(child, stage);
        }
        boolean partial = exclusive[1 - call.entryStage.ordinal()] > 0;
        for (Stage stage : Stage.values()) {
          long nanos = exclusive[stage.ordinal()];
          boolean entry = stage == call.entryStage;
          if (nanos == 0 && !entry) continue;
          var key =
              new MeasurementKey(
                  call.unitId, call.epoch, call.state, call.operation, stage, call.owners);
          var counter = expectedCounters.computeIfAbsent(key, ignored -> new ExpectedCounter());
          counter.nanos += nanos;
          if (entry) {
            counter.events++;
            if (call.failed) counter.failures++;
          }
          counter.partial |= partial;
        }
      }
      var expected = new HashMap<MeasurementKey, ConstraintProfileMeasurement>();
      expectedCounters.forEach(
          (key, counter) ->
              expected.put(
                  key,
                  new ConstraintProfileMeasurement(
                      key.stage,
                      key.epoch,
                      key.state,
                      key.operation,
                      key.owners,
                      counter.nanos,
                      counter.events,
                      counter.failures,
                      counter.partial)));
      var actual = new HashMap<MeasurementKey, ConstraintProfileMeasurement>();
      var profile = collecting.freeze(Outcome.FAILED);
      for (var unit : profile.units()) {
        for (var row : unit.measurements()) {
          actual.put(
              new MeasurementKey(
                  unit.unitId(),
                  row.ownershipEpoch(),
                  row.ownershipState(),
                  row.operation(),
                  row.stage(),
                  row.owners()),
              row);
        }
      }
      assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
      assertThat(profile.initialization().scoringWallNanos())
          .isEqualTo(
              spans.stream()
                  .filter(span -> span.stage == Stage.INITIALIZATION)
                  .mapToLong(span -> span.end - span.start)
                  .sum());
      assertThat(profile.search().scoringWallNanos())
          .isEqualTo(
              spans.stream()
                  .filter(span -> span.stage == Stage.SEARCH)
                  .mapToLong(span -> span.end - span.start)
                  .sum());
      assertThat(profile.combined().eventCount()).isEqualTo(calls.size());
      assertThat(profile.combined().failureCount())
          .isEqualTo(calls.stream().filter(call -> call.failed).count());
    }
  }

  private record WallInterval(long start, long end, Stage stage) {}

  private record MeasurementKey(
      long unitId,
      int epoch,
      OwnershipState state,
      Operation operation,
      Stage stage,
      List<ConstraintRef> owners) {}

  private static final class InvocationInterval {
    private final long unitId;
    private final int epoch;
    private final OwnershipState state;
    private final Operation operation;
    private final List<ConstraintRef> owners;
    private final Stage entryStage;
    private final long start;
    private final List<InvocationInterval> children = new ArrayList<>();
    private long end;
    private boolean failed;

    private InvocationInterval(
        long unitId,
        int epoch,
        OwnershipState state,
        Operation operation,
        List<ConstraintRef> owners,
        Stage entryStage,
        long start) {
      this.unitId = unitId;
      this.epoch = epoch;
      this.state = state;
      this.operation = operation;
      this.owners = owners;
      this.entryStage = entryStage;
      this.start = start;
    }
  }

  private static final class ExpectedCounter {
    private long nanos;
    private long events;
    private long failures;
    private boolean partial;
  }

  private void recurse(Unit unit, int depth) {
    invoke(
        unit,
        () -> {
          if (depth == 0) clock.incrementAndGet();
          else wall(Stage.SEARCH, () -> recurse(unit, depth - 1));
        });
  }

  private Unit unit() {
    return profiler.registerUnit(generation, "FILTER", Role.LIFECYCLE, List.of());
  }

  private void candidates(Map<Unit, List<ConstraintRef>> owners) {
    generation.resolveOwnership(OwnershipState.ENABLED_CANDIDATES, owners);
  }

  private static ConstraintProfileBucket bucket(ConstraintProfile profile, ConstraintRef owner) {
    return profile.combined().buckets().stream()
        .filter(bucket -> bucket.owners().equals(List.of(owner)))
        .findFirst()
        .orElseThrow();
  }

  private void invoke(Unit unit, Runnable body) {
    profiler.enterUnit(unit, Operation.INSERT);
    Throwable failure = null;
    try {
      body.run();
    } catch (RuntimeException | Error throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      profiler.exitUnit(failure);
    }
  }

  private void wall(Stage stage, Runnable body) {
    profiler.enterWall(stage);
    Throwable failure = null;
    try {
      body.run();
    } catch (RuntimeException | Error throwable) {
      failure = throwable;
      throw throwable;
    } finally {
      profiler.exitWall(failure);
    }
  }
}
