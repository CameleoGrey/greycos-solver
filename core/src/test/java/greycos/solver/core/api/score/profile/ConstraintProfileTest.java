package greycos.solver.core.api.score.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.api.score.profile.ConstraintProfile.Outcome;
import greycos.solver.core.api.score.profile.ConstraintProfile.OwnershipState;
import greycos.solver.core.api.score.profile.ConstraintProfile.Role;
import greycos.solver.core.api.score.profile.ConstraintProfile.Stage;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.junit.jupiter.api.Test;

class ConstraintProfileTest {

  private static final ConstraintRef A = ConstraintRef.of("A");
  private static final ConstraintRef B = ConstraintRef.of("B");

  @Test
  void acceptsWallTimeWithoutMeasuredEventsAndOptionalZeroRows() {
    var empty = emptySection(0L);
    var initialization = emptySection(17L);
    var search = emptySection(5L);
    var combined = emptySection(22L);
    var profile =
        new ConstraintProfile(
            1, Outcome.FAILED, initialization, search, combined, List.of(), List.of());
    assertThat(profile.initialization().coverage()).isZero();
    assertThat(empty.coverage()).isNull();

    var generation = generation(0L);
    var zeroRow =
        row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 0L, 0L, 0L, false);
    var withZeroRow =
        new ConstraintProfile(
            1,
            Outcome.COMPLETED,
            initialization,
            empty,
            initialization,
            List.of(generation),
            List.of(unit(0L, 0L, List.of(zeroRow))));
    var withoutZeroRow =
        new ConstraintProfile(
            1,
            Outcome.COMPLETED,
            initialization,
            empty,
            initialization,
            List.of(generation),
            List.of(unit(0L, 0L, List.of())));
    assertThat(withZeroRow.combined()).isEqualTo(withoutZeroRow.combined());

    var zeroBucket = new ConstraintProfileBucket(List.of(A), 0L, 0L, 0L, false, null, null);
    var withZeroBucket =
        new ConstraintProfileSection(0L, 0L, 0L, 0L, 0L, null, List.of(zeroBucket));
    new ConstraintProfile(1, Outcome.COMPLETED, withZeroBucket, empty, empty, List.of(), List.of());
  }

  @Test
  void canonicalizesAndDefensivelyCopiesEveryNestedCollection() {
    var owners = new ArrayList<>(List.of(B, A, B));
    var row = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, owners, 8L, 2L, 1L, false);
    var bucket = new ConstraintProfileBucket(owners, 8L, 2L, 1L, false, 1.0, 4.0);
    var buckets = new ArrayList<>(List.of(bucket));
    var section = new ConstraintProfileSection(10L, 8L, 2L, 2L, 1L, 0.8, buckets);
    var epochs =
        new ArrayList<>(List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.ACTIVE)));
    var activities =
        new ArrayList<>(
            List.of(
                new ConstraintProfileConstraintActivity(B, Activity.ACTIVE),
                new ConstraintProfileConstraintActivity(A, Activity.ACTIVE)));
    var generation = new ConstraintProfileGeneration(0L, true, epochs, activities);
    var locations =
        new ArrayList<>(
            List.of(
                new ConstraintProfileSourceLocation("Z", "method", null),
                new ConstraintProfileSourceLocation("A", "method", 3)));
    var rows = new ArrayList<>(List.of(row));
    var unit = new ConstraintProfileUnit(0L, 0L, "FILTER", Role.LIFECYCLE, locations, rows);
    var generations = new ArrayList<>(List.of(generation));
    var units = new ArrayList<>(List.of(unit));
    var profile =
        new ConstraintProfile(
            1, Outcome.COMPLETED, section, emptySection(0L), section, generations, units);

    owners.clear();
    buckets.clear();
    epochs.clear();
    activities.clear();
    locations.clear();
    rows.clear();
    generations.clear();
    units.clear();
    assertThat(profile.generations()).containsExactly(generation);
    assertThat(profile.units()).containsExactly(unit);
    assertThat(generation.constraints())
        .extracting(ConstraintProfileConstraintActivity::constraintRef)
        .containsExactly(A, B);
    assertThat(row.owners()).containsExactly(A, B);
    assertThat(bucket.owners()).containsExactly(A, B);
    assertThat(unit.sourceLocations())
        .extracting(ConstraintProfileSourceLocation::className)
        .containsExactly("A", "Z");
    for (List<?> values :
        List.of(
            profile.generations(),
            profile.units(),
            section.buckets(),
            bucket.owners(),
            generation.ownershipEpochs(),
            generation.constraints(),
            unit.sourceLocations(),
            unit.measurements(),
            row.owners())) {
      assertThatThrownBy(values::clear).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void preservesCandidateOwnersAndDistinctActiveOwners() {
    var generation =
        new ConstraintProfileGeneration(
            0L,
            true,
            List.of(
                new ConstraintProfileOwnershipEpoch(1, OwnershipState.ACTIVE),
                new ConstraintProfileOwnershipEpoch(0, OwnershipState.ENABLED_CANDIDATES)),
            List.of(
                new ConstraintProfileConstraintActivity(B, Activity.INACTIVE),
                new ConstraintProfileConstraintActivity(A, Activity.ACTIVE)));
    var initialization =
        new ConstraintProfileSection(
            12L,
            10L,
            2L,
            1L,
            0L,
            10.0 / 12,
            List.of(new ConstraintProfileBucket(List.of(A, B), 10L, 1L, 0L, false, 1.0, 10.0)));
    var search =
        new ConstraintProfileSection(
            8L,
            5L,
            3L,
            1L,
            0L,
            5.0 / 8,
            List.of(new ConstraintProfileBucket(List.of(A), 5L, 1L, 0L, false, 1.0, 5.0)));
    var combined =
        new ConstraintProfileSection(
            20L,
            15L,
            5L,
            2L,
            0L,
            0.75,
            List.of(
                new ConstraintProfileBucket(List.of(A), 5L, 1L, 0L, false, 5.0 / 15, 5.0),
                new ConstraintProfileBucket(List.of(A, B), 10L, 1L, 0L, false, 10.0 / 15, 10.0)));
    var unit =
        unit(
            0L,
            0L,
            List.of(
                row(Stage.SEARCH, 1, OwnershipState.ACTIVE, List.of(A), 5L, 1L, 0L, false),
                row(
                    Stage.INITIALIZATION,
                    0,
                    OwnershipState.ENABLED_CANDIDATES,
                    List.of(A, B),
                    10L,
                    1L,
                    0L,
                    false)));
    var profile =
        new ConstraintProfile(
            1,
            Outcome.COMPLETED,
            initialization,
            search,
            combined,
            List.of(generation),
            List.of(unit));
    assertThat(profile.combined().buckets())
        .extracting(ConstraintProfileBucket::owners)
        .containsExactly(List.of(A, B), List.of(A));
    assertThat(unit.measurements())
        .extracting(ConstraintProfileMeasurement::stage)
        .containsExactly(Stage.INITIALIZATION, Stage.SEARCH);
    assertThat(generation.ownershipEpochs())
        .extracting(ConstraintProfileOwnershipEpoch::ownershipEpoch)
        .containsExactly(0, 1);
  }

  @Test
  void preservesHistoricalOwnersAcrossFailureAndGenerationChanges() {
    var first = generation(0L);
    var second =
        new ConstraintProfileGeneration(
            1L,
            false,
            List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.ENABLED_CANDIDATES)),
            List.of(new ConstraintProfileConstraintActivity(A, Activity.UNRESOLVED)));
    var section = singleSection(2, 2, false);
    var profile =
        new ConstraintProfile(
            1,
            Outcome.FAILED,
            section,
            emptySection(0),
            section,
            List.of(first, second),
            List.of(
                unit(
                    0,
                    0,
                    List.of(
                        row(
                            Stage.INITIALIZATION,
                            0,
                            OwnershipState.ACTIVE,
                            List.of(A),
                            1,
                            1,
                            0,
                            false))),
                unit(
                    1,
                    1,
                    List.of(
                        row(
                            Stage.INITIALIZATION,
                            0,
                            OwnershipState.ENABLED_CANDIDATES,
                            List.of(A),
                            1,
                            1,
                            0,
                            false)))));
    assertThat(profile.generations().getFirst().constraints().getFirst().activity())
        .isEqualTo(Activity.ACTIVE);
    assertThat(profile.generations().getLast().constraints().getFirst().activity())
        .isEqualTo(Activity.UNRESOLVED);
    assertThat(profile.combined().eventCount()).isEqualTo(2);
  }

  @Test
  void recombinesPartialDurationsAndEntryCountsWithCompleteCalls() {
    var initialization =
        new ConstraintProfileSection(
            4L,
            3L,
            1L,
            1L,
            1L,
            0.75,
            List.of(new ConstraintProfileBucket(List.of(A), 3L, 1L, 1L, true, 1.0, null)));
    // Five nanoseconds from the split call and two from a complete call; its average remains
    // unavailable.
    var search =
        new ConstraintProfileSection(
            8L,
            7L,
            1L,
            1L,
            0L,
            0.875,
            List.of(new ConstraintProfileBucket(List.of(A), 7L, 1L, 0L, true, 1.0, null)));
    var combined =
        new ConstraintProfileSection(
            12L,
            10L,
            2L,
            2L,
            1L,
            10.0 / 12,
            List.of(new ConstraintProfileBucket(List.of(A), 10L, 2L, 1L, false, 1.0, 5.0)));
    var rows =
        List.of(
            row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 3L, 1L, 1L, true),
            row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), 7L, 1L, 0L, true));
    var profile =
        new ConstraintProfile(
            1,
            Outcome.FAILED,
            initialization,
            search,
            combined,
            List.of(generation(0L)),
            List.of(unit(0L, 0L, rows)));
    assertThat(profile.initialization().buckets().getFirst().averageNanosPerCall()).isNull();
    assertThat(profile.search().buckets().getFirst().averageNanosPerCall()).isNull();
    assertThat(profile.combined().buckets().getFirst().averageNanosPerCall()).isEqualTo(5.0);
    assertThat(profile.combined().buckets().getFirst().partialInvocations()).isFalse();
  }

  @Test
  void acceptsSplitTimingRowWithNoCallsAndUnattributedUnresolvedWork() {
    var initialization =
        new ConstraintProfileSection(
            3L,
            3L,
            0L,
            1L,
            0L,
            1.0,
            List.of(new ConstraintProfileBucket(List.of(), 3L, 1L, 0L, true, 1.0, null)));
    var search =
        new ConstraintProfileSection(
            5L,
            5L,
            0L,
            0L,
            0L,
            1.0,
            List.of(new ConstraintProfileBucket(List.of(), 5L, 0L, 0L, true, 1.0, null)));
    var combined =
        new ConstraintProfileSection(
            8L,
            8L,
            0L,
            1L,
            0L,
            1.0,
            List.of(new ConstraintProfileBucket(List.of(), 8L, 1L, 0L, false, 1.0, 8.0)));
    var generation =
        new ConstraintProfileGeneration(
            0L,
            false,
            List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.UNRESOLVED)),
            List.of(new ConstraintProfileConstraintActivity(A, Activity.UNRESOLVED)));
    var unit =
        unit(
            0L,
            0L,
            List.of(
                row(
                    Stage.INITIALIZATION,
                    0,
                    OwnershipState.UNRESOLVED,
                    List.of(),
                    3L,
                    1L,
                    0L,
                    true),
                row(Stage.SEARCH, 0, OwnershipState.UNRESOLVED, List.of(), 5L, 0L, 0L, true)));
    new ConstraintProfile(
        1, Outcome.FAILED, initialization, search, combined, List.of(generation), List.of(unit));
  }

  @Test
  void sortsBucketsByTimeThenOwnerListAndLocationsByKnownLine() {
    var buckets =
        List.of(
            new ConstraintProfileBucket(List.of(B), 10L, 1L, 0L, false, 0.25, 10.0),
            new ConstraintProfileBucket(List.of(A, B), 10L, 1L, 0L, false, 0.25, 10.0),
            new ConstraintProfileBucket(List.of(A), 10L, 1L, 0L, false, 0.25, 10.0),
            new ConstraintProfileBucket(List.of(), 10L, 1L, 0L, false, 0.25, 10.0));
    var section = new ConstraintProfileSection(40L, 40L, 0L, 4L, 0L, 1.0, buckets);
    assertThat(section.buckets())
        .extracting(ConstraintProfileBucket::owners)
        .containsExactly(List.of(), List.of(A), List.of(A, B), List.of(B));
    var unit =
        new ConstraintProfileUnit(
            0L,
            0L,
            "NODE",
            Role.PROPAGATOR,
            List.of(
                new ConstraintProfileSourceLocation("A", "m", null),
                new ConstraintProfileSourceLocation("A", "m", 20),
                new ConstraintProfileSourceLocation("A", "m", 3)),
            List.of());
    assertThat(unit.sourceLocations())
        .extracting(ConstraintProfileSourceLocation::lineNumber)
        .containsExactly(3, 20, null);
  }

  @Test
  void sortsGenerationAndRunLocalUnitIdsIndependently() {
    var empty = emptySection(0L);
    var profile =
        new ConstraintProfile(
            1,
            Outcome.COMPLETED,
            empty,
            empty,
            empty,
            List.of(generation(9L), generation(2L)),
            List.of(unit(2L, 10L, List.of()), unit(9L, 7L, List.of())));
    assertThat(profile.generations())
        .extracting(ConstraintProfileGeneration::generationId)
        .containsExactly(2L, 9L);
    assertThat(profile.units()).extracting(ConstraintProfileUnit::unitId).containsExactly(7L, 10L);
  }

  @Test
  void rejectsInvalidScalarValuesAndUnknownsRepresentedAsNumbers() {
    assertThatThrownBy(() -> new ConstraintProfileOwnershipEpoch(-1, OwnershipState.ACTIVE))
        .hasMessageContaining("ownershipEpoch (-1)");
    assertThatThrownBy(() -> new ConstraintProfileSourceLocation("A", "m", -1))
        .hasMessageContaining("lineNumber (-1)");
    assertThatThrownBy(() -> new ConstraintProfileSourceLocation(" ", "m", null))
        .hasMessageContaining("className");
    assertThatThrownBy(
            () -> row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), -1, 1, 0, false))
        .hasMessageContaining("exclusiveNanos (-1)");
    assertThatThrownBy(
            () -> row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), 1, 1, 2, false))
        .hasMessageContaining("failureCount (2)");
    assertThatThrownBy(
            () -> row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), 1, 0, 0, false))
        .hasMessageContaining("partialInvocations");
    assertThatThrownBy(
            () -> row(Stage.SEARCH, 0, OwnershipState.UNRESOLVED, List.of(A), 1, 1, 0, false))
        .hasMessageContaining("must be empty");
    assertThatThrownBy(
            () -> new ConstraintProfileBucket(List.of(A), 1, 1, 0, false, Double.NaN, 1.0))
        .hasMessageContaining("measuredShare");
    assertThatThrownBy(
            () ->
                new ConstraintProfileBucket(
                    List.of(A), 1, 1, 0, false, 1.0, Double.POSITIVE_INFINITY))
        .hasMessageContaining("averageNanosPerCall");
    assertThatThrownBy(() -> new ConstraintProfileBucket(List.of(A), 1, 1, 0, true, 1.0, 1.0))
        .hasMessageContaining("derived value (null)");
    assertThatThrownBy(() -> new ConstraintProfileBucket(List.of(), 0, 0, 0, false, null, 0.0))
        .hasMessageContaining("derived value (null)");
    assertThatThrownBy(() -> new ConstraintProfileSection(0, 0, 0, 0, 0, 0.0, List.of()))
        .hasMessageContaining("coverage");
    assertThatThrownBy(() -> new ConstraintProfileSection(1, 2, 0, 0, 0, 1.0, List.of()))
        .hasMessageContaining("partition scoringWallNanos");
    assertThatThrownBy(() -> new ConstraintProfileSection(3, 0, 2, 0, 0, 0.0, List.of()))
        .hasMessageContaining("partition scoringWallNanos");
  }

  @Test
  void rejectsNullComponentsAndElements() {
    assertThatThrownBy(() -> new ConstraintProfileConstraintActivity(null, Activity.ACTIVE))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("constraintRef");
    assertThatThrownBy(() -> new ConstraintProfileConstraintActivity(A, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("activity");
    assertThatThrownBy(() -> new ConstraintProfileGeneration(0L, false, null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("ownershipEpochs");
    assertThatThrownBy(
            () -> new ConstraintProfileBucket(Arrays.asList(A, null), 0, 0, 0, false, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("owners element");
    assertThatThrownBy(() -> new ConstraintProfileUnit(0L, 0L, "NODE", null, List.of(), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("role");
  }

  @Test
  void rejectsDuplicateMetadataAndMeasurementKeys() {
    var epoch = new ConstraintProfileOwnershipEpoch(0, OwnershipState.ACTIVE);
    var activity = new ConstraintProfileConstraintActivity(A, Activity.ACTIVE);
    assertThatThrownBy(
            () ->
                new ConstraintProfileGeneration(0, true, List.of(epoch, epoch), List.of(activity)))
        .hasMessageContaining("ownershipEpoch (0) must be unique");
    assertThatThrownBy(
            () ->
                new ConstraintProfileGeneration(
                    0, true, List.of(epoch), List.of(activity, activity)))
        .hasMessageContaining("constraintRef (A) must be unique");
    var row = row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), 1, 1, 0, false);
    assertThatThrownBy(() -> unit(0, 0, List.of(row, row))).hasMessageContaining("measurement key");
    var differentOwners =
        row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(B), 1, 1, 0, false);
    assertThatThrownBy(() -> unit(0, 0, List.of(row, differentOwners)))
        .hasMessageContaining("inconsistent ownership");
    var empty = emptySection(0);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    empty,
                    empty,
                    empty,
                    List.of(generation(0), generation(0)),
                    List.of()))
        .hasMessageContaining("generationId (0) must be unique");
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    empty,
                    empty,
                    empty,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of()), unit(0, 0, List.of()))))
        .hasMessageContaining("unitId (0) must be unique");
  }

  @Test
  void rejectsInconsistentDerivedFieldsAndBucketConservation() {
    var bucket = new ConstraintProfileBucket(List.of(A), 1, 1, 0, false, 1.0, 1.0);
    assertThatThrownBy(() -> new ConstraintProfileSection(2, 1, 1, 1, 0, 1.0, List.of(bucket)))
        .hasMessageContaining("coverage");
    assertThatThrownBy(() -> new ConstraintProfileSection(2, 2, 0, 1, 0, 1.0, List.of(bucket)))
        .hasMessageContaining("measuredShare");
    assertThatThrownBy(() -> new ConstraintProfileSection(1, 1, 0, 2, 0, 1.0, List.of(bucket)))
        .hasMessageContaining("eventCount=2");
    assertThatThrownBy(() -> new ConstraintProfileSection(1, 1, 0, 1, 0, 1.0, List.of()))
        .hasMessageContaining("do not match measured totals");
    assertThatThrownBy(
            () -> new ConstraintProfileSection(1, 1, 0, 1, 0, 1.0, List.of(bucket, bucket)))
        .hasMessageContaining("bucket owners");
  }

  @Test
  void rejectsMissingAndMismatchedGenerationEpochAndOwners() {
    var empty = emptySection(0);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    empty,
                    empty,
                    empty,
                    List.of(),
                    List.of(unit(7, 3, List.of()))))
        .hasMessageContaining("missing generationId (7)");
    var section = singleSection(1, 1, false);
    var missingEpoch =
        row(Stage.INITIALIZATION, 4, OwnershipState.ACTIVE, List.of(A), 1, 1, 0, false);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(missingEpoch)))))
        .hasMessageContaining("ownershipEpoch (4)")
        .hasMessageContaining("declares (null)");
    var wrongState =
        row(Stage.INITIALIZATION, 0, OwnershipState.ENABLED_CANDIDATES, List.of(A), 1, 1, 0, false);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(wrongState)))))
        .hasMessageContaining("ownershipState (ENABLED_CANDIDATES)");
    for (var activity : Activity.values()) {
      if (activity == Activity.ACTIVE) {
        continue;
      }
      var generation =
          new ConstraintProfileGeneration(
              0,
              true,
              List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.ACTIVE)),
              List.of(new ConstraintProfileConstraintActivity(A, activity)));
      var row = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 1, 1, 0, false);
      assertThatThrownBy(
              () ->
                  new ConstraintProfile(
                      1,
                      Outcome.COMPLETED,
                      section,
                      empty,
                      section,
                      List.of(generation),
                      List.of(unit(0, 0, List.of(row)))))
          .hasMessageContaining("incompatible activity (" + activity + ")");
    }
    var generationWithoutOwners =
        new ConstraintProfileGeneration(
            0,
            true,
            List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.ACTIVE)),
            List.of());
    var row = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 1, 1, 0, false);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generationWithoutOwners),
                    List.of(unit(0, 0, List.of(row)))))
        .hasMessageContaining("owner (A)")
        .hasMessageContaining("activity (null)");
  }

  @Test
  void rejectsRowAndSectionDisagreementAndIncompleteRecombinedInvocations() {
    var empty = emptySection(0);
    var section = singleSection(2, 1, false);
    var row = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 1, 1, 0, false);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(row)))))
        .hasMessageContaining("initialization bucket owners [A] counters");
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generation(0)),
                    List.of()))
        .hasMessageContaining("do not match measured owners");
    var partialRow = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 2, 1, 0, true);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    section,
                    empty,
                    section,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(partialRow)))))
        .hasMessageContaining("partialInvocations");
    var noEntry = row(Stage.SEARCH, 0, OwnershipState.ACTIVE, List.of(A), 2, 0, 0, true);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.FAILED,
                    empty,
                    singleSection(2, 0, true),
                    section,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(noEntry)))))
        .hasMessageContaining("without an entered invocation");
  }

  @Test
  void rejectsCombinedDisagreementAndPartialCombinedBuckets() {
    var empty = emptySection(0);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    emptySection(2),
                    emptySection(3),
                    emptySection(4),
                    List.of(),
                    List.of()))
        .hasMessageContaining("combined scoringWallNanos (4)");
    var partial = singleSection(2, 1, true);
    var partialRow = row(Stage.INITIALIZATION, 0, OwnershipState.ACTIVE, List.of(A), 2, 1, 0, true);
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    partial,
                    empty,
                    partial,
                    List.of(generation(0)),
                    List.of(unit(0, 0, List.of(partialRow)))))
        .hasMessageContaining("combined bucket owners ([A])");
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    2, Outcome.COMPLETED, empty, empty, empty, List.of(), List.of()))
        .hasMessageContaining("schemaVersion (2)");
  }

  @Test
  void rejectsOverflowWithoutClampingAndPreservesLongPrecision() {
    var first =
        new ConstraintProfileBucket(
            List.of(A), Long.MAX_VALUE, 1, 0, false, 1.0, (double) Long.MAX_VALUE);
    var second = new ConstraintProfileBucket(List.of(B), 1, 1, 0, false, 1.0 / Long.MAX_VALUE, 1.0);
    assertThatThrownBy(
            () ->
                new ConstraintProfileSection(
                    Long.MAX_VALUE, Long.MAX_VALUE, 0, 2, 0, 1.0, List.of(first, second)))
        .hasMessageContaining("exclusiveNanos")
        .hasMessageContaining("exceeds the long range");
    assertThatThrownBy(
            () ->
                new ConstraintProfile(
                    1,
                    Outcome.COMPLETED,
                    emptySection(Long.MAX_VALUE),
                    emptySection(1),
                    emptySection(0),
                    List.of(),
                    List.of()))
        .hasMessageContaining("scoringWallNanos")
        .hasMessageContaining("exceeds the long range");
    long beyondDoublePrecision = (1L << 54) + 1L;
    var row =
        row(
            Stage.INITIALIZATION,
            0,
            OwnershipState.ACTIVE,
            List.of(A),
            beyondDoublePrecision,
            1,
            0,
            false);
    var section = singleSection(beyondDoublePrecision, 1, false);
    var profile =
        new ConstraintProfile(
            1,
            Outcome.COMPLETED,
            section,
            emptySection(0),
            section,
            List.of(generation(Long.MAX_VALUE)),
            List.of(unit(Long.MAX_VALUE, Long.MAX_VALUE, List.of(row))));
    assertThat(profile.initialization().measuredNanos()).isEqualTo(beyondDoublePrecision);
    assertThat(profile.units().getFirst().unitId()).isEqualTo(Long.MAX_VALUE);
  }

  private static ConstraintProfileSection emptySection(long wall) {
    return new ConstraintProfileSection(wall, 0, wall, 0, 0, wall == 0 ? null : 0.0, List.of());
  }

  private static ConstraintProfileSection singleSection(long nanos, long events, boolean partial) {
    return new ConstraintProfileSection(
        nanos,
        nanos,
        0,
        events,
        0,
        nanos == 0 ? null : 1.0,
        List.of(
            new ConstraintProfileBucket(
                List.of(A),
                nanos,
                events,
                0,
                partial,
                nanos == 0 ? null : 1.0,
                partial || events == 0 ? null : (double) nanos / events)));
  }

  private static ConstraintProfileGeneration generation(long id) {
    return new ConstraintProfileGeneration(
        id,
        true,
        List.of(new ConstraintProfileOwnershipEpoch(0, OwnershipState.ACTIVE)),
        List.of(new ConstraintProfileConstraintActivity(A, Activity.ACTIVE)));
  }

  private static ConstraintProfileUnit unit(
      long generation, long unit, List<ConstraintProfileMeasurement> rows) {
    return new ConstraintProfileUnit(generation, unit, "FILTER", Role.LIFECYCLE, List.of(), rows);
  }

  private static ConstraintProfileMeasurement row(
      Stage stage,
      int epoch,
      OwnershipState state,
      List<ConstraintRef> owners,
      long nanos,
      long events,
      long failures,
      boolean partial) {
    return new ConstraintProfileMeasurement(
        stage, epoch, state, Operation.INSERT, owners, nanos, events, failures, partial);
  }
}
