package greycos.solver.core.impl.bavet.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.api.score.profile.ConstraintProfile.Outcome;
import greycos.solver.core.api.score.profile.ConstraintProfile.OwnershipState;
import greycos.solver.core.api.score.profile.ConstraintProfile.Role;
import greycos.solver.core.api.score.profile.ConstraintProfile.Stage;
import greycos.solver.core.api.score.profile.ConstraintProfileBucket;
import greycos.solver.core.api.score.profile.ConstraintProfileConstraintActivity;
import greycos.solver.core.api.score.profile.ConstraintProfileGeneration;
import greycos.solver.core.api.score.profile.ConstraintProfileMeasurement;
import greycos.solver.core.api.score.profile.ConstraintProfileOwnershipEpoch;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;
import greycos.solver.core.api.score.profile.ConstraintProfileSourceLocation;
import greycos.solver.core.api.score.profile.ConstraintProfileUnit;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A thread-confined collector owned by one admitted solve. Factories never retain this object. Unit
 * and wall stacks are separate: wall frames partition engine time, while each elapsed interval is
 * charged only to the top unit frame. This is equivalent to subtracting directly nested measured
 * intervals from their parent. All event counters are allocated before use.
 */
@NullMarked
public final class DefaultConstraintProfiler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConstraintProfiler.class);
  private static final int OPERATION_COUNT = Operation.values().length;
  private static final Stage[] STAGES = Stage.values();
  private static final Operation[] OPERATIONS = Operation.values();

  private final LongSupplier clock;
  private final List<Generation> generations = new ArrayList<>();
  private final List<Unit> units = new ArrayList<>();
  private final Map<List<ConstraintRef>, List<ConstraintRef>> ownerSets = new HashMap<>();
  private final long[] wallNanos = new long[STAGES.length];
  private @Nullable Stage[] wallStack = new Stage[8];
  private Frame[] unitStack = new Frame[8];
  private int wallDepth;
  private int unitDepth;
  private int activeStage = -1;
  private long lastTimestamp;
  private boolean frozen;
  private @Nullable Throwable recordingFailure;

  public DefaultConstraintProfiler() {
    this(System::nanoTime);
  }

  /** Injectable monotonic clock; tests advance it explicitly without sleeping. */
  public DefaultConstraintProfiler(LongSupplier clock) {
    this.clock = Objects.requireNonNull(clock);
    for (int i = 0; i < unitStack.length; i++) unitStack[i] = new Frame();
  }

  public Generation newGeneration(Collection<ConstraintRef> constraints) {
    checkMutable();
    var generation = new Generation(this, generations.size() + 1L, constraints);
    generations.add(generation);
    return generation;
  }

  public Unit registerUnit(
      Generation generation, String kind, Role role, Collection<ConstraintNodeLocation> locations) {
    checkMutable();
    requireOwnGeneration(generation);
    if (generation.epoch != 0) {
      throw new IllegalStateException(
          "Register profiling units before resolving ownership (generation="
              + generation.generationId
              + ", ownershipEpoch="
              + generation.epoch
              + ").");
    }
    var sourceLocations =
        locations.stream()
            .filter(location -> !location.className().equals("<unknown>"))
            .map(
                location ->
                    new ConstraintProfileSourceLocation(
                        location.className(),
                        location.methodName(),
                        location.lineNumber() < 0 ? null : location.lineNumber()))
            .toList();
    var unit = new Unit(generation, units.size() + 1L, kind, role, sourceLocations);
    units.add(unit);
    generation.units.add(unit);
    return unit;
  }

  public void enterWall(Stage stage) {
    checkMutable();
    Objects.requireNonNull(stage);
    pushWall(stage);
  }

  /** Suspends score-work time while existing logging executes inside an engine entry point. */
  public void enterExcluded() {
    checkMutable();
    pushWall(null);
  }

  public void exitExcluded(@Nullable Throwable failure) {
    exitWall(failure);
  }

  private void pushWall(@Nullable Stage stage) {
    // Nested boundaries in the same section do not change the measured union. Keep their
    // logical stack entries, but defer the clock checkpoint until a unit or section boundary.
    if (wallDepth == 0 || wallStack[wallDepth - 1] != stage) {
      advanceClock();
    }
    if (wallDepth == wallStack.length) {
      growWallStack();
    }
    wallStack[wallDepth++] = stage;
    activeStage = stage == null ? -1 : stage.ordinal();
  }

  /** Completes a wall boundary without replacing a delegate failure. */
  public void exitWall(@Nullable Throwable failure) {
    try {
      if (wallDepth == 0) throw new IllegalStateException("No scoring wall boundary is open.");
      try {
        if (wallDepth == 1 || wallStack[wallDepth - 1] != wallStack[wallDepth - 2]) {
          advanceClock();
        }
      } finally {
        wallStack[--wallDepth] = null;
        Stage stage = wallDepth == 0 ? null : wallStack[wallDepth - 1];
        activeStage = stage == null ? -1 : stage.ordinal();
      }
    } catch (RuntimeException | Error recordingException) {
      recordingFailed(recordingException, failure);
    }
  }

  public void enterUnit(Unit unit, Operation operation) {
    checkMutable();
    if (activeStage < 0) {
      throw unitOutsideWall(unit);
    }
    if (unit.generation.profiler != this) {
      throw foreignUnit(unit);
    }
    advanceClock();
    if (unitDepth == unitStack.length) {
      growUnitStack();
    }
    Frame frame = unitStack[unitDepth++];
    Counter[] rows = unit.current.rows;
    int operationIndex = operation.ordinal();
    frame.entryStage = activeStage;
    // These rows retain the entry ownership epoch even if activation changes unit.current.
    frame.entryCounter = rows[activeStage * OPERATION_COUNT + operationIndex];
    frame.otherCounter = rows[(1 - activeStage) * OPERATION_COUNT + operationIndex];
    frame.entryCounter.events++;
  }

  /** Completes an invocation; a failed child and its failed parent each count as a failure. */
  public void exitUnit(@Nullable Throwable failure) {
    try {
      if (unitDepth == 0) throw new IllegalStateException("No profiling invocation is open.");
      Frame frame = unitStack[unitDepth - 1];
      try {
        advanceClock();
        if (failure != null) frame.entryCounter.failures++;
      } finally {
        frame.entryCounter = null;
        frame.otherCounter = null;
        unitDepth--;
      }
    } catch (RuntimeException | Error recordingException) {
      recordingFailed(recordingException, failure);
    }
  }

  private void advanceClock() {
    long now = clock.getAsLong();
    if (wallDepth > 0) {
      long elapsed = now - lastTimestamp;
      if (elapsed < 0) {
        throw clockMovedBackwards(now);
      }
      if (activeStage >= 0) {
        wallNanos[activeStage] = Math.addExact(wallNanos[activeStage], elapsed);
        if (unitDepth > 0) {
          Frame frame = unitStack[unitDepth - 1];
          if (frame.entryStage == activeStage) {
            frame.entryCounter.nanos += elapsed;
          } else if (elapsed > 0) {
            frame.otherCounter.nanos += elapsed;
            frame.entryCounter.partial = true;
            frame.otherCounter.partial = true;
          }
        }
      }
    }
    lastTimestamp = now;
  }

  private void growWallStack() {
    wallStack = java.util.Arrays.copyOf(wallStack, wallDepth * 2);
  }

  private void growUnitStack() {
    int oldLength = unitStack.length;
    unitStack = java.util.Arrays.copyOf(unitStack, oldLength * 2);
    for (int i = oldLength; i < unitStack.length; i++) unitStack[i] = new Frame();
  }

  private static IllegalStateException unitOutsideWall(Unit unit) {
    return new IllegalStateException(
        "Profiling unit (" + unit.unitId + ") entered outside scoring wall time.");
  }

  private static IllegalArgumentException foreignUnit(Unit unit) {
    return new IllegalArgumentException(
        "Profiling unit (" + unit.unitId + ") belongs to another solve.");
  }

  private IllegalStateException clockMovedBackwards(long now) {
    return new IllegalStateException(
        "Profiling clock moved backwards (previous=" + lastTimestamp + ", current=" + now + ").");
  }

  private void recordingFailed(Throwable recordingException, @Nullable Throwable failure) {
    if (recordingFailure == null) recordingFailure = recordingException;
    if (failure == null) {
      if (recordingException instanceof RuntimeException exception) throw exception;
      throw (Error) recordingException;
    }
    if (recordingException != failure) failure.addSuppressed(recordingException);
  }

  private void checkMutable() {
    if (frozen) throw new IllegalStateException("The constraint profile has already been frozen.");
  }

  private void requireOwnGeneration(Generation generation) {
    if (generation.profiler != this) {
      throw new IllegalArgumentException(
          "Generation (" + generation.generationId + ") belongs to another solve.");
    }
  }

  private List<ConstraintRef> internOwners(Collection<ConstraintRef> owners) {
    List<ConstraintRef> key = owners.stream().distinct().sorted().toList();
    return ownerSets.computeIfAbsent(key, ignored -> key);
  }

  /** Freezes once, after every score director and algorithm helper has stopped adding work. */
  public ConstraintProfile freeze(Outcome outcome) {
    checkMutable();
    frozen = true;
    if (recordingFailure != null) {
      throw new IllegalStateException(
          "Unable to publish constraint profile after an accounting failure.", recordingFailure);
    }
    if (wallDepth != 0 || unitDepth != 0) {
      throw new IllegalStateException(
          "Cannot freeze profile with open boundaries (wallDepth="
              + wallDepth
              + ", unitDepth="
              + unitDepth
              + ").");
    }
    List<ConstraintProfileUnit> frozenUnits = new ArrayList<>(units.size());
    Map<List<ConstraintRef>, Counter> initializationBuckets = new HashMap<>();
    Map<List<ConstraintRef>, Counter> searchBuckets = new HashMap<>();
    Map<List<ConstraintRef>, Counter> combinedBuckets = new HashMap<>();
    for (Unit unit : units) {
      List<ConstraintProfileMeasurement> measurements = new ArrayList<>();
      for (EpochCounters epoch : unit.epochs) {
        for (Stage stage : STAGES) {
          for (Operation operation : OPERATIONS) {
            Counter counter = epoch.rows[stage.ordinal() * OPERATION_COUNT + operation.ordinal()];
            if (counter.nanos == 0 && counter.events == 0 && counter.failures == 0) continue;
            measurements.add(
                new ConstraintProfileMeasurement(
                    stage,
                    epoch.epoch,
                    epoch.state,
                    operation,
                    epoch.owners,
                    counter.nanos,
                    counter.events,
                    counter.failures,
                    counter.partial));
            var sectionBuckets =
                stage == Stage.INITIALIZATION ? initializationBuckets : searchBuckets;
            sectionBuckets.computeIfAbsent(epoch.owners, ignored -> new Counter()).add(counter);
            combinedBuckets.computeIfAbsent(epoch.owners, ignored -> new Counter()).add(counter);
          }
        }
      }
      frozenUnits.add(
          new ConstraintProfileUnit(
              unit.generation.generationId,
              unit.unitId,
              unit.kind,
              unit.role,
              unit.locations,
              measurements));
    }
    var frozenGenerations = generations.stream().map(Generation::freeze).toList();
    return new ConstraintProfile(
        1,
        outcome,
        freezeSection(wallNanos[0], initializationBuckets, false),
        freezeSection(wallNanos[1], searchBuckets, false),
        freezeSection(Math.addExact(wallNanos[0], wallNanos[1]), combinedBuckets, true),
        frozenGenerations,
        frozenUnits);
  }

  private static ConstraintProfileSection freezeSection(
      long wall, Map<List<ConstraintRef>, Counter> counters, boolean combined) {
    long measured = 0;
    long events = 0;
    long failures = 0;
    for (Counter counter : counters.values()) {
      measured = Math.addExact(measured, counter.nanos);
      events = Math.addExact(events, counter.events);
      failures = Math.addExact(failures, counter.failures);
    }
    if (measured > wall) {
      throw new IllegalStateException(
          "Measured profiling time (" + measured + ") exceeds scoring wall time (" + wall + ").");
    }
    List<ConstraintProfileBucket> buckets = new ArrayList<>(counters.size());
    for (var entry : counters.entrySet()) {
      Counter counter = entry.getValue();
      boolean partial = !combined && counter.partial;
      buckets.add(
          new ConstraintProfileBucket(
              entry.getKey(),
              counter.nanos,
              counter.events,
              counter.failures,
              partial,
              measured == 0 ? null : (double) counter.nanos / measured,
              partial || counter.events == 0 ? null : (double) counter.nanos / counter.events));
    }
    return new ConstraintProfileSection(
        wall,
        measured,
        wall - measured,
        events,
        failures,
        wall == 0 ? null : (double) measured / wall,
        buckets);
  }

  public void report(ConstraintProfile profile) {
    ConstraintProfileRenderer.report(profile, LOGGER);
  }

  /** Detached generation state: no working solution, stream, or node is retained. */
  public static final class Generation {
    private final DefaultConstraintProfiler profiler;
    private final long generationId;
    private final List<Unit> units = new ArrayList<>();
    private final Map<ConstraintRef, Activity> activities = new LinkedHashMap<>();
    private final List<ConstraintProfileOwnershipEpoch> epochs = new ArrayList<>();
    private Stage stage = Stage.INITIALIZATION;
    private OwnershipState ownershipState = OwnershipState.UNRESOLVED;
    private int epoch;

    private Generation(
        DefaultConstraintProfiler profiler,
        long generationId,
        Collection<ConstraintRef> constraints) {
      this.profiler = profiler;
      this.generationId = generationId;
      constraints.stream()
          .distinct()
          .sorted()
          .forEach(ref -> activities.put(ref, Activity.UNRESOLVED));
      epochs.add(new ConstraintProfileOwnershipEpoch(0, OwnershipState.UNRESOLVED));
    }

    public long generationId() {
      return generationId;
    }

    public Stage stage() {
      return stage;
    }

    public void completeInitialization() {
      profiler.checkMutable();
      stage = Stage.SEARCH;
    }

    public void setConstraintActivity(ConstraintRef ref, Activity activity) {
      profiler.checkMutable();
      if (!activities.containsKey(ref)) {
        throw new IllegalArgumentException(
            "Constraint (" + ref + ") is not declared in generation (" + generationId + ").");
      }
      activities.put(ref, Objects.requireNonNull(activity));
    }

    /** Installs a complete new epoch only after all ownership traversal has succeeded. */
    public void resolveOwnership(
        OwnershipState state, Map<Unit, ? extends Collection<ConstraintRef>> ownersByUnit) {
      profiler.checkMutable();
      Objects.requireNonNull(state);
      var nextCounters = new ArrayList<EpochCounters>(units.size());
      for (Unit unit : ownersByUnit.keySet()) {
        if (unit.generation != this) {
          throw new IllegalArgumentException(
              "Unit (" + unit.unitId + ") does not belong to generation (" + generationId + ").");
        }
      }
      for (Unit unit : units) {
        Collection<ConstraintRef> owners = ownersByUnit.get(unit);
        var interned = profiler.internOwners(owners == null ? List.of() : owners);
        for (ConstraintRef owner : interned) {
          if (!activities.containsKey(owner)) {
            throw new IllegalArgumentException(
                "Owner (" + owner + ") is not declared in generation (" + generationId + ").");
          }
        }
        if (state == OwnershipState.UNRESOLVED && !interned.isEmpty()) {
          throw new IllegalArgumentException(
              "Unresolved owners must be empty (generation=" + generationId + ").");
        }
        nextCounters.add(new EpochCounters(epoch + 1, state, interned));
      }
      epoch++;
      ownershipState = state;
      epochs.add(new ConstraintProfileOwnershipEpoch(epoch, state));
      for (int i = 0; i < units.size(); i++) {
        Unit unit = units.get(i);
        unit.current = nextCounters.get(i);
        unit.epochs.add(unit.current);
      }
    }

    private ConstraintProfileGeneration freeze() {
      var constraints =
          activities.entrySet().stream()
              .map(
                  entry ->
                      new ConstraintProfileConstraintActivity(entry.getKey(), entry.getValue()))
              .toList();
      return new ConstraintProfileGeneration(
          generationId, stage == Stage.SEARCH, epochs, constraints);
    }
  }

  /** A prebound counter handle, shared by all invocations of a single instrumentation boundary. */
  public static final class Unit {
    private final Generation generation;
    private final long unitId;
    private final String kind;
    private final Role role;
    private final List<ConstraintProfileSourceLocation> locations;
    private final List<EpochCounters> epochs = new ArrayList<>();
    private EpochCounters current;

    private Unit(
        Generation generation,
        long unitId,
        String kind,
        Role role,
        List<ConstraintProfileSourceLocation> locations) {
      this.generation = generation;
      this.unitId = unitId;
      this.kind = Objects.requireNonNull(kind);
      this.role = Objects.requireNonNull(role);
      this.locations = List.copyOf(locations);
      this.current = new EpochCounters(generation.epoch, generation.ownershipState, List.of());
      epochs.add(current);
    }

    public long unitId() {
      return unitId;
    }

    public Generation generation() {
      return generation;
    }
  }

  private static final class EpochCounters {
    final int epoch;
    final OwnershipState state;
    final List<ConstraintRef> owners;
    final Counter[] rows = new Counter[STAGES.length * OPERATION_COUNT];

    EpochCounters(int epoch, OwnershipState state, List<ConstraintRef> owners) {
      this.epoch = epoch;
      this.state = state;
      this.owners = owners;
      for (int i = 0; i < rows.length; i++) rows[i] = new Counter();
    }
  }

  private static final class Counter {
    long nanos;
    long events;
    long failures;
    boolean partial;

    void add(Counter other) {
      nanos = Math.addExact(nanos, other.nanos);
      events = Math.addExact(events, other.events);
      failures = Math.addExact(failures, other.failures);
      partial |= other.partial;
    }
  }

  private static final class Frame {
    @Nullable Counter entryCounter;
    @Nullable Counter otherCounter;
    int entryStage;
  }
}
