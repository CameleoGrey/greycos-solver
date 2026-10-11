package greycos.solver.core.testdata.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfileBucket;
import greycos.solver.core.api.score.profile.ConstraintProfileConstraintActivity;
import greycos.solver.core.api.score.profile.ConstraintProfileGeneration;
import greycos.solver.core.api.score.profile.ConstraintProfileMeasurement;
import greycos.solver.core.api.score.profile.ConstraintProfileOwnershipEpoch;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;
import greycos.solver.core.api.score.profile.ConstraintProfileSourceLocation;
import greycos.solver.core.api.score.profile.ConstraintProfileUnit;
import greycos.solver.core.api.score.stream.ConstraintRef;

/** Shared schema fixtures for the Jackson 2 and Jackson 3 adapters. All counters are synthetic. */
public final class ConstraintProfileTestData {

  public static final long GENERATION_ID = 4_294_967_296L;
  public static final long UNIT_ID = 8_589_934_592L;
  private static final ConstraintRef A = ConstraintRef.of("A's constraint");
  private static final ConstraintRef B = ConstraintRef.of("B constraint");
  private static final List<ConstraintRef> SHARED = List.of(A, B);

  private ConstraintProfileTestData() {}

  public static ConstraintProfile empty() {
    return profile(ConstraintProfile.Outcome.COMPLETED, 0, 0, List.of(), List.of());
  }

  public static ConstraintProfile populated() {
    return profile(
        ConstraintProfile.Outcome.COMPLETED,
        100,
        100,
        List.of(activeGeneration()),
        List.of(
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID,
                "Root \"quoted\"\\path\nnext",
                ConstraintProfile.Role.ROOT,
                List.of(
                    new ConstraintProfileSourceLocation("example.Provider", "known", 42),
                    new ConstraintProfileSourceLocation(
                        "example.Provider\\generated", "unknown\"\n\t", null)),
                List.of(
                    row(
                        ConstraintProfile.Stage.INITIALIZATION,
                        0,
                        ConstraintProfile.OwnershipState.ENABLED_CANDIDATES,
                        ConstraintProfile.Operation.INSERT,
                        SHARED,
                        30,
                        3,
                        0,
                        false),
                    row(
                        ConstraintProfile.Stage.SEARCH,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.UPDATE,
                        List.of(A),
                        20,
                        2,
                        0,
                        false))),
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID + 1,
                "Deferred",
                ConstraintProfile.Role.PREPARATION,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.INITIALIZATION,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.PREPARE,
                        List.of(A),
                        10,
                        1,
                        0,
                        false))),
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID + 2,
                "Join",
                ConstraintProfile.Role.PROPAGATOR,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.SEARCH,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.RETRACT,
                        SHARED,
                        30,
                        3,
                        0,
                        false))),
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID + 3,
                "Input",
                ConstraintProfile.Role.LEFT_INPUT,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.INITIALIZATION,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.INSERT,
                        List.of(),
                        10,
                        1,
                        0,
                        false)))));
  }

  public static ConstraintProfile failedPartial() {
    return profile(
        ConstraintProfile.Outcome.FAILED,
        20,
        20,
        List.of(
            activeGeneration(),
            new ConstraintProfileGeneration(
                GENERATION_ID + 1,
                false,
                List.of(
                    new ConstraintProfileOwnershipEpoch(
                        0, ConstraintProfile.OwnershipState.UNRESOLVED)),
                List.of(
                    new ConstraintProfileConstraintActivity(
                        A, ConstraintProfile.Activity.UNRESOLVED)))),
        List.of(
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID,
                "Join",
                ConstraintProfile.Role.RIGHT_INPUT,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.INITIALIZATION,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.UPDATE,
                        List.of(A),
                        8,
                        0,
                        0,
                        true),
                    row(
                        ConstraintProfile.Stage.SEARCH,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.UPDATE,
                        List.of(A),
                        12,
                        1,
                        1,
                        true))),
            new ConstraintProfileUnit(
                GENERATION_ID + 1,
                UNIT_ID + 1,
                "Filter",
                ConstraintProfile.Role.LIFECYCLE,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.INITIALIZATION,
                        0,
                        ConstraintProfile.OwnershipState.UNRESOLVED,
                        ConstraintProfile.Operation.INSERT,
                        List.of(),
                        4,
                        1,
                        1,
                        false)))));
  }

  public static ConstraintProfile zeroDuration() {
    return profile(
        ConstraintProfile.Outcome.TERMINATED_EARLY,
        0,
        0,
        List.of(activeGeneration()),
        List.of(
            new ConstraintProfileUnit(
                GENERATION_ID,
                UNIT_ID,
                "Update",
                ConstraintProfile.Role.ROOT,
                List.of(),
                List.of(
                    row(
                        ConstraintProfile.Stage.SEARCH,
                        1,
                        ConstraintProfile.OwnershipState.ACTIVE,
                        ConstraintProfile.Operation.UPDATE,
                        List.of(A),
                        0,
                        3_000_000_001L,
                        0,
                        false)))));
  }

  public static String expectedJson(String fixture) throws IOException {
    var resource = "/profile/" + fixture + ".json";
    try (var input = ConstraintProfileTestData.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException(
            "The profile fixture resource (" + resource + ") is missing.");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static ConstraintProfileGeneration activeGeneration() {
    return new ConstraintProfileGeneration(
        GENERATION_ID,
        true,
        List.of(
            new ConstraintProfileOwnershipEpoch(
                0, ConstraintProfile.OwnershipState.ENABLED_CANDIDATES),
            new ConstraintProfileOwnershipEpoch(1, ConstraintProfile.OwnershipState.ACTIVE)),
        List.of(
            new ConstraintProfileConstraintActivity(A, ConstraintProfile.Activity.ACTIVE),
            new ConstraintProfileConstraintActivity(B, ConstraintProfile.Activity.ACTIVE),
            new ConstraintProfileConstraintActivity(
                ConstraintRef.of("Disabled"), ConstraintProfile.Activity.DISABLED_WEIGHT),
            new ConstraintProfileConstraintActivity(
                ConstraintRef.of("Inactive"), ConstraintProfile.Activity.INACTIVE)));
  }

  private static ConstraintProfileMeasurement row(
      ConstraintProfile.Stage stage,
      int epoch,
      ConstraintProfile.OwnershipState state,
      ConstraintProfile.Operation operation,
      List<ConstraintRef> owners,
      long nanos,
      long events,
      long failures,
      boolean partial) {
    return new ConstraintProfileMeasurement(
        stage, epoch, state, operation, owners, nanos, events, failures, partial);
  }

  private static ConstraintProfile profile(
      ConstraintProfile.Outcome outcome,
      long initializationWall,
      long searchWall,
      List<ConstraintProfileGeneration> generations,
      List<ConstraintProfileUnit> units) {
    var initialization = new ArrayList<ConstraintProfileMeasurement>();
    var search = new ArrayList<ConstraintProfileMeasurement>();
    var combined = new ArrayList<ConstraintProfileMeasurement>();
    for (var unit : units) {
      for (var row : unit.measurements()) {
        (row.stage() == ConstraintProfile.Stage.INITIALIZATION ? initialization : search).add(row);
        combined.add(row);
      }
    }
    return new ConstraintProfile(
        1,
        outcome,
        section(initializationWall, initialization, false),
        section(searchWall, search, false),
        section(initializationWall + searchWall, combined, true),
        generations,
        units);
  }

  private static ConstraintProfileSection section(
      long wall, List<ConstraintProfileMeasurement> rows, boolean combined) {
    Map<List<ConstraintRef>, long[]> totals = new LinkedHashMap<>();
    long nanos = 0;
    long events = 0;
    long failures = 0;
    for (var row : rows) {
      var bucket = totals.computeIfAbsent(row.owners(), ignored -> new long[4]);
      bucket[0] += row.exclusiveNanos();
      bucket[1] += row.eventCount();
      bucket[2] += row.failureCount();
      bucket[3] |= row.partialInvocations() && !combined ? 1 : 0;
      nanos += row.exclusiveNanos();
      events += row.eventCount();
      failures += row.failureCount();
    }
    var buckets = new ArrayList<ConstraintProfileBucket>();
    for (var entry : totals.entrySet()) {
      var counters = entry.getValue();
      buckets.add(
          new ConstraintProfileBucket(
              entry.getKey(),
              counters[0],
              counters[1],
              counters[2],
              counters[3] != 0,
              nanos == 0 ? null : (double) counters[0] / nanos,
              counters[1] == 0 || counters[3] != 0 ? null : (double) counters[0] / counters[1]));
    }
    return new ConstraintProfileSection(
        wall,
        nanos,
        wall - nanos,
        events,
        failures,
        wall == 0 ? null : (double) nanos / wall,
        buckets);
  }
}
