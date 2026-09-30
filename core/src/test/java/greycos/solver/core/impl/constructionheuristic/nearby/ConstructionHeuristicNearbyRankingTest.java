package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyRanking.MeterProfile;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListAssignMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class ConstructionHeuristicNearbyRankingTest {

  @Test
  void directMeterKeepsDeclaredDirectionAndDistinctCandidateIdentities() {
    var descriptor = scalarDescriptor();
    var entity = new Object();
    var first = new EqualValue(3);
    var second = new EqualValue(1);
    var third = new EqualValue(1);
    var calls = new AtomicInteger();
    var profile =
        profile(
            descriptor,
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) -> {
              assertThat(origin).isSameAs(entity);
              calls.incrementAndGet();
              return ((EqualValue) destination).distance;
            });
    var firstMove = new SelectorBasedChangeMove<>(descriptor, entity, first);
    var secondMove = new SelectorBasedChangeMove<>(descriptor, entity, second);
    var thirdMove = new SelectorBasedChangeMove<>(descriptor, entity, third);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(firstMove, secondMove, thirdMove, secondMove),
            descriptor,
            List.of(profile),
            false,
            mockDirector());

    assertThat(drain(origin.rankedMoves()))
        .extracting(move -> ((ChangeMove<?>) move).getToPlanningValue())
        .satisfiesExactly(
            value -> assertThat(value).isSameAs(second),
            value -> assertThat(value).isSameAs(third),
            value -> assertThat(value).isSameAs(first));
    assertThat(calls).hasValue(3);
    assertThat(origin.tailMoves()).isExhausted();
  }

  @Test
  void profilesRoundRobinWithoutComparingTheirDistanceScales() {
    var descriptor = scalarDescriptor();
    var entity = new Object();
    var values = List.of(1, 2, 3, 4);
    var moves =
        values.stream()
            .<Move<Object>>map(value -> new SelectorBasedChangeMove<>(descriptor, entity, value))
            .toList();
    var ascending =
        profile(
            descriptor, ArgumentShape.ENTITY_VALUE, (origin, destination) -> (Integer) destination);
    var descending =
        profile(
            descriptor,
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) -> 1_000_000.0 - (Integer) destination);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            moves, descriptor, List.of(ascending, descending), false, mockDirector());

    assertThat(drain(origin.rankedMoves()))
        .containsExactly(moves.get(0), moves.get(3), moves.get(1), moves.get(2));
  }

  @Test
  void queuedValueStillCallsEntityThenValue() {
    var descriptor = scalarDescriptor();
    var value = new Object();
    var firstEntity = new Object();
    var secondEntity = new Object();
    var profile =
        profile(
            descriptor,
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) -> {
              assertThat(destination).isSameAs(value);
              return origin == secondEntity ? 0 : 1;
            });
    var first = new SelectorBasedChangeMove<>(descriptor, firstEntity, value);
    var second = new SelectorBasedChangeMove<>(descriptor, secondEntity, value);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(first, second), descriptor, List.of(profile), true, mockDirector());

    assertThat(drain(origin.rankedMoves())).containsExactly(second, first);
  }

  @Test
  void entityNeighborsOnlyRankAssignedValuesAdmittedByConstruction() {
    var descriptor = scalarDescriptor();
    EntityDescriptor<Object> entityDescriptor = mock(EntityDescriptor.class);
    when(descriptor.getEntityDescriptor()).thenReturn(entityDescriptor);
    var originEntity = new Object();
    var allowedValue = new Object();
    var seedValue = new Object();
    var excludedValue = new Object();
    var allowedNeighbor = new Object();
    var excludedNeighbor = new Object();
    var solution = new Object();
    var scoreDirector = mockDirector();
    when(scoreDirector.getWorkingSolution()).thenReturn(solution);
    when(entityDescriptor.extractEntities(solution))
        .thenReturn(List.of(excludedNeighbor, allowedNeighbor));
    when(descriptor.getValue(allowedNeighbor)).thenReturn(allowedValue);
    when(descriptor.getValue(excludedNeighbor)).thenReturn(excludedValue);
    var calls = new AtomicInteger();
    var profile =
        profile(
            descriptor,
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) -> {
              assertThat(origin).isSameAs(originEntity);
              assertThat(destination).isSameAs(allowedNeighbor);
              calls.incrementAndGet();
              return 0;
            });
    var seed = new SelectorBasedChangeMove<>(descriptor, originEntity, seedValue);
    var assigned = new SelectorBasedChangeMove<>(descriptor, originEntity, allowedValue);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(seed, assigned), descriptor, List.of(profile), false, scoreDirector);

    assertThat(drain(origin.rankedMoves())).containsExactly(assigned, seed);
    assertThat(calls).hasValue(1);
  }

  @Test
  void queuedValueEntityRankingMatchesEagerUniqueAnchorOrder() {
    var random = new Random(137L);
    for (var trial = 0; trial < 25; trial++) {
      var neighbors = new ArrayList<Object>();
      for (var i = 0; i < 17; i++) {
        neighbors.add(new IndexedEntity(i));
      }
      var fixture = neighborFixture(neighbors);
      var value = new Object();
      neighbors.forEach(neighbor -> fixture.values().put(neighbor, value));
      var moves = new ArrayList<Move<Object>>();
      for (var i = 0; i < 13; i++) {
        moves.add(new SelectorBasedChangeMove<>(fixture.descriptor(), new IndexedEntity(i), value));
      }
      Collections.shuffle(moves, random);
      var distances = new double[13][17];
      var specialDistances =
          new double[] {-0.0, 0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
      for (var row : distances) {
        for (var i = 0; i < row.length; i++) {
          row[i] =
              random.nextBoolean() ? random.nextInt(9) - 4 : specialDistances[random.nextInt(4)];
        }
      }
      var calls = new AtomicInteger();
      var meter =
          profile(
              fixture.descriptor(),
              ArgumentShape.ENTITY_ENTITY,
              (origin, destination) -> {
                calls.incrementAndGet();
                return distances[((IndexedEntity) origin).index()][
                    ((IndexedEntity) destination).index()];
              });
      // An eager reference orders every anchor occurrence before dropping duplicate assignments.
      var reference = new ArrayList<ReferenceOccurrence>();
      for (var neighbor : neighbors) {
        for (var i = 0; i < moves.size(); i++) {
          var entity = (IndexedEntity) ((ChangeMove<?>) moves.get(i)).getEntity();
          reference.add(
              new ReferenceOccurrence(
                  moves.get(i), i, distances[entity.index()][((IndexedEntity) neighbor).index()]));
        }
      }
      reference.sort(
          Comparator.comparingDouble(ReferenceOccurrence::distance)
              .thenComparingInt(ReferenceOccurrence::sourceOrder));
      var expected = new ArrayList<Move<Object>>();
      var seen = Collections.newSetFromMap(new IdentityHashMap<Move<Object>, Boolean>());
      reference.forEach(
          occurrence -> {
            if (seen.add(occurrence.move())) {
              expected.add(occurrence.move());
            }
          });
      var admitted = new ArrayList<>(moves);
      admitted.add(moves.getFirst());
      var ranked =
          ConstructionHeuristicNearbyRanking.rank(
              admitted, fixture.descriptor(), List.of(meter), true, fixture.director());

      assertThat(drain(ranked.rankedMoves())).containsExactlyElementsOf(expected);
      assertThat(calls).hasValue(13 * 17);
      assertThat(ranked.tailMoves()).isExhausted();
    }
  }

  @Test
  void queuedValueEntityRankingKeepsSignedZeroInfinityAndSourceTies() {
    var neighbor = new IndexedEntity(0);
    var fixture = neighborFixture(List.of(neighbor));
    var value = new Object();
    fixture.values().put(neighbor, value);
    var distances =
        new double[] {Double.POSITIVE_INFINITY, 0.0, -0.0, Double.NEGATIVE_INFINITY, -0.0};
    var moves = new ArrayList<Move<Object>>();
    for (var i = 0; i < distances.length; i++) {
      moves.add(new SelectorBasedChangeMove<>(fixture.descriptor(), new IndexedEntity(i), value));
    }
    var meter =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) -> distances[((IndexedEntity) origin).index()]);

    var ranked =
        ConstructionHeuristicNearbyRanking.rank(
            moves, fixture.descriptor(), List.of(meter), true, fixture.director());

    assertThat(drain(ranked.rankedMoves()))
        .containsExactly(moves.get(3), moves.get(2), moves.get(4), moves.get(1), moves.get(0));
  }

  @Test
  void queuedValueProfilesInterleaveUniqueAssignments() {
    assertEntityProfilesInterleaveUniqueAssignments(true);
  }

  @Test
  void queuedEntityProfilesInterleaveUniqueAssignments() {
    assertEntityProfilesInterleaveUniqueAssignments(false);
  }

  private static void assertEntityProfilesInterleaveUniqueAssignments(boolean queuedValue) {
    var neighbors = new ArrayList<Object>();
    for (var i = 0; i < (queuedValue ? 2 : 5); i++) {
      neighbors.add(new IndexedEntity(i));
    }
    var fixture = neighborFixture(neighbors);
    var sharedValue = new Object();
    var sharedEntity = new Object();
    var moves = new ArrayList<Move<Object>>();
    for (var i = 0; i < 4; i++) {
      moves.add(
          new SelectorBasedChangeMove<>(
              fixture.descriptor(),
              queuedValue ? new IndexedEntity(i) : sharedEntity,
              queuedValue ? sharedValue : new IndexedEntity(i)));
    }
    for (var i = 0; i < neighbors.size(); i++) {
      fixture
          .values()
          .put(
              neighbors.get(i),
              queuedValue
                  ? sharedValue
                  : ((ChangeMove<?>) moves.get(Math.max(0, i - 1))).getToPlanningValue());
    }
    var swap =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) ->
                queuedValue
                    ? ((IndexedEntity) origin).index() * 2 + ((IndexedEntity) destination).index()
                    : ((IndexedEntity) destination).index());
    var order = new int[] {2, 3, 0, 1};
    var change =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) ->
                1_000_000 + order[((IndexedEntity) (queuedValue ? origin : destination)).index()]);

    var ranked =
        ConstructionHeuristicNearbyRanking.rank(
            moves, fixture.descriptor(), List.of(swap, change), queuedValue, fixture.director());

    assertThat(drain(ranked.rankedMoves()))
        .containsExactly(moves.get(0), moves.get(2), moves.get(1), moves.get(3));
  }

  @Test
  void queuedValueTypedEntityMeterKeepsUnsupportedSeedsAndOptionalTail() {
    var supportedNeighbor = new IndexedEntity(0);
    var unsupportedNeighbor = new Object();
    var fixture = neighborFixture(List.of(unsupportedNeighbor, supportedNeighbor));
    var value = new Object();
    fixture.values().put(supportedNeighbor, value);
    fixture.values().put(unsupportedNeighbor, value);
    var seeded =
        new SelectorBasedChangeMove<>(fixture.descriptor(), new IndexedEntity(1), new Object());
    var unsupported = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), value);
    var supported =
        new SelectorBasedChangeMove<>(fixture.descriptor(), new IndexedEntity(2), value);
    var optional = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), null);
    var meter =
        MeterProfile.of(
            new ConstructionHeuristicNearbyProfile(
                fixture.descriptor(),
                TypedEntityMeter.class,
                ArgumentShape.ENTITY_ENTITY,
                "typed swap"));
    TypedEntityMeter.calls.set(0);

    var ranked =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(seeded, optional, unsupported, supported, optional),
            fixture.descriptor(),
            List.of(meter),
            true,
            fixture.director());

    assertThat(drain(ranked.rankedMoves())).containsExactly(supported, seeded, unsupported);
    assertThat(drain(ranked.tailMoves())).containsExactly(optional);
    assertThat(TypedEntityMeter.calls).hasValue(1);
  }

  @Test
  void queuedValueEntityRankingRebuildsChangedAnchorAssignments() {
    var neighbor = new Object();
    var fixture = neighborFixture(List.of(neighbor));
    var firstValue = new Object();
    var secondValue = new Object();
    var first = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), firstValue);
    var second = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), secondValue);
    var meter =
        profile(fixture.descriptor(), ArgumentShape.ENTITY_ENTITY, (origin, destination) -> 0);
    fixture.values().put(neighbor, firstValue);
    var firstRanking =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(first, second), fixture.descriptor(), List.of(meter), true, fixture.director());
    fixture.values().put(neighbor, secondValue);
    var secondRanking =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(first, second), fixture.descriptor(), List.of(meter), true, fixture.director());

    assertThat(drain(firstRanking.rankedMoves())).containsExactly(first, second);
    assertThat(drain(secondRanking.rankedMoves())).containsExactly(second, first);
  }

  @Test
  void queuedValueEntityRankingEvaluatesRepeatedAnchorIdentityOnce() {
    var neighbor = new Object();
    var fixture = neighborFixture(List.of(neighbor, neighbor));
    var value = new Object();
    fixture.values().put(neighbor, value);
    var first = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), value);
    var second = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), value);
    var calls = new AtomicInteger();
    var meter =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) -> {
              calls.incrementAndGet();
              return 0;
            });

    var ranked =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(first, second), fixture.descriptor(), List.of(meter), true, fixture.director());

    assertThat(drain(ranked.rankedMoves())).containsExactly(first, second);
    assertThat(calls).hasValue(2);
  }

  @Test
  void queuedValueMinimumDoesNotHideLaterProviderFailureOrNaN() {
    var firstNeighbor = new Object();
    var secondNeighbor = new Object();
    var fixture = neighborFixture(List.of(firstNeighbor, secondNeighbor));
    when(fixture.descriptor().getSimpleEntityAndVariableName()).thenReturn("Entity.value");
    var value = new Object();
    fixture.values().put(firstNeighbor, value);
    fixture.values().put(secondNeighbor, value);
    var move = new SelectorBasedChangeMove<>(fixture.descriptor(), new Object(), value);
    var calls = new AtomicInteger();
    var cause = new IllegalArgumentException("later provider failure");
    var failing =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) -> {
              calls.incrementAndGet();
              if (destination == secondNeighbor) {
                throw cause;
              }
              return Double.NEGATIVE_INFINITY;
            });
    assertThatThrownBy(
            () ->
                ConstructionHeuristicNearbyRanking.rank(
                    List.of(move),
                    fixture.descriptor(),
                    List.of(failing),
                    true,
                    fixture.director()))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause)
        .hasMessageContaining("Entity.value")
        .hasMessageContaining("unit test");
    assertThat(calls).hasValue(2);
    calls.set(0);
    var nan =
        profile(
            fixture.descriptor(),
            ArgumentShape.ENTITY_ENTITY,
            (origin, destination) -> {
              calls.incrementAndGet();
              return destination == secondNeighbor ? Double.NaN : Double.NEGATIVE_INFINITY;
            });
    assertThatThrownBy(
            () ->
                ConstructionHeuristicNearbyRanking.rank(
                    List.of(move), fixture.descriptor(), List.of(nan), true, fixture.director()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NaN")
        .hasMessageContaining("unit test");
    assertThat(calls).hasValue(2);
  }

  @Test
  void valueNeighborsShareStartAndAdjacentAnchorAndSeedEmptyLists() {
    ListVariableDescriptor<Object> descriptor = mock(ListVariableDescriptor.class);
    when(descriptor.isListVariable()).thenReturn(true);
    when(descriptor.canExtractValueRangeFromSolution()).thenReturn(true);
    var populatedEntity = new Object();
    var emptyEntity = new Object();
    var assignedValue = new Object();
    var sourceValue = new Object();
    when(descriptor.getListSize(populatedEntity)).thenReturn(1);
    when(descriptor.getElement(populatedEntity, 0)).thenReturn(assignedValue);
    var calls = new AtomicInteger();
    var profile =
        profile(
            descriptor,
            ArgumentShape.VALUE_VALUE,
            (origin, destination) -> {
              assertThat(origin).isSameAs(sourceValue);
              assertThat(destination).isSameAs(assignedValue);
              calls.incrementAndGet();
              return 1;
            });
    var start = new SelectorBasedListAssignMove<>(descriptor, sourceValue, populatedEntity, 0);
    var after = new SelectorBasedListAssignMove<>(descriptor, sourceValue, populatedEntity, 1);
    var empty = new SelectorBasedListAssignMove<>(descriptor, sourceValue, emptyEntity, 0);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(start, empty, after), descriptor, List.of(profile), false, mockDirector());

    assertThat(drain(origin.rankedMoves())).containsExactly(start, empty, after);
    assertThat(calls).hasValue(1);
  }

  @Test
  void equalDistanceListAnchorsRetainTheirGroupedInsertionOrder() {
    ListVariableDescriptor<Object> descriptor = mock(ListVariableDescriptor.class);
    when(descriptor.isListVariable()).thenReturn(true);
    when(descriptor.canExtractValueRangeFromSolution()).thenReturn(true);
    var firstEntity = new Object();
    var secondEntity = new Object();
    when(descriptor.getListSize(firstEntity)).thenReturn(1);
    when(descriptor.getListSize(secondEntity)).thenReturn(1);
    when(descriptor.getElement(firstEntity, 0)).thenReturn(new Object());
    when(descriptor.getElement(secondEntity, 0)).thenReturn(new Object());
    var value = new Object();
    var firstStart = new SelectorBasedListAssignMove<>(descriptor, value, firstEntity, 0);
    var secondStart = new SelectorBasedListAssignMove<>(descriptor, value, secondEntity, 0);
    var firstEnd = new SelectorBasedListAssignMove<>(descriptor, value, firstEntity, 1);
    var meter = profile(descriptor, ArgumentShape.VALUE_VALUE, (origin, destination) -> 0);

    var ranked =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(firstStart, secondStart, firstEnd),
            descriptor,
            List.of(meter),
            false,
            mockDirector());

    assertThat(drain(ranked.rankedMoves())).containsExactly(firstStart, firstEnd, secondStart);
  }

  @Test
  void optionalNullAndNoChangeAreEmittedOnlyInTail() {
    var descriptor = scalarDescriptor();
    var entity = new Object();
    var assigned = new SelectorBasedChangeMove<>(descriptor, entity, 1);
    var optional = new SelectorBasedChangeMove<>(descriptor, entity, null);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            Arrays.asList(optional, assigned, optional),
            descriptor,
            List.of(profile(descriptor, ArgumentShape.ENTITY_VALUE, (a, b) -> 0)),
            false,
            mockDirector());

    assertThat(drain(origin.rankedMoves())).containsExactly(assigned);
    assertThat(drain(origin.tailMoves())).containsExactly(optional);
    assertThat(
            ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(
                SelectorBasedCompositeMove.buildMove(assigned, optional)))
        .isTrue();
    assertThat(
            ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(
                SelectorBasedCompositeMove.buildMove(
                    optional, SelectorBasedNoChangeMove.getInstance())))
        .isFalse();
  }

  @Test
  void sourceOrderIsRestoredWithinEachProgressivelyLargerNeighborhood() {
    var descriptor = scalarDescriptor();
    var entity = new Object();
    var moves =
        java.util.stream.IntStream.rangeClosed(1, 10)
            .<Move<Object>>mapToObj(
                value -> new SelectorBasedChangeMove<>(descriptor, entity, value))
            .toList();
    var profile =
        profile(
            descriptor,
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) -> -(Integer) destination);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            moves, descriptor, List.of(profile), false, mockDirector(), true);
    var neighborhoods =
        ConstructionHeuristicNearbyRanking.neighborhoodOrdered(
            origin.rankedMoves(), 3, origin.sourceOrder());

    assertThat(drain(neighborhoods))
        .extracting(move -> ((ChangeMove<?>) move).getToPlanningValue())
        .containsExactly(8, 9, 10, 5, 6, 7, 1, 2, 3, 4);
  }

  @Test
  void admittedUnionKeepsDifferentVariablesWithSameEntityAndValue() {
    var firstDescriptor = scalarDescriptor();
    var secondDescriptor = scalarDescriptor();
    var entity = new Object();
    var value = new Object();
    var first = new SelectorBasedChangeMove<>(firstDescriptor, entity, value);
    var second = new SelectorBasedChangeMove<>(secondDescriptor, entity, value);
    var origins =
        ConstructionHeuristicNearbyRanking.rankAdmittedComposite(
            List.of(first, second),
            List.of(
                new ConstructionHeuristicNearbyRanking.LeafRanking<>(
                    firstDescriptor,
                    List.of(profile(firstDescriptor, ArgumentShape.ENTITY_VALUE, (a, b) -> 0)),
                    false,
                    false),
                new ConstructionHeuristicNearbyRanking.LeafRanking<>(
                    secondDescriptor,
                    List.of(profile(secondDescriptor, ArgumentShape.ENTITY_VALUE, (a, b) -> 0)),
                    false,
                    false)),
            mockDirector());

    assertThat(drain(origins.rankedMoves())).containsExactly(first, second);
  }

  @SuppressWarnings("unchecked")
  @Test
  void listEntityValueRangesAreAppliedBeforeDistanceRanking() {
    ListVariableDescriptor<Object> descriptor = mock(ListVariableDescriptor.class);
    when(descriptor.isListVariable()).thenReturn(true);
    var allowedEntity = new Object();
    var forbiddenEntity = new Object();
    var sourceValue = new Object();
    VariableDescriptorAwareScoreDirector<Object> scoreDirector =
        mock(VariableDescriptorAwareScoreDirector.class);
    ValueRangeManager<Object> ranges = mock(ValueRangeManager.class);
    when(scoreDirector.getValueRangeManager()).thenReturn(ranges);
    ValueRange<Object> allowedRange = mock(ValueRange.class);
    ValueRange<Object> forbiddenRange = mock(ValueRange.class);
    when(ranges.<Object, Object>getFromEntity(null, allowedEntity)).thenReturn(allowedRange);
    when(ranges.<Object, Object>getFromEntity(null, forbiddenEntity)).thenReturn(forbiddenRange);
    when(allowedRange.contains(sourceValue)).thenReturn(true);
    var calls = new AtomicInteger();
    var profile =
        profile(
            descriptor,
            ArgumentShape.VALUE_DESTINATION,
            (origin, destination) -> {
              assertThat(destination).isSameAs(allowedEntity);
              calls.incrementAndGet();
              return 0;
            });
    var allowed = new SelectorBasedListAssignMove<>(descriptor, sourceValue, allowedEntity, 0);
    var forbidden = new SelectorBasedListAssignMove<>(descriptor, sourceValue, forbiddenEntity, 0);
    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(forbidden, allowed), descriptor, List.of(profile), false, scoreDirector);

    assertThat(drain(origin.rankedMoves())).containsExactly(allowed);
    assertThat(calls).hasValue(1);
  }

  @Test
  void unsupportedTypedCandidatesUseSeedStreamWithoutCallingProvider() {
    var descriptor = scalarDescriptor();
    var entity = new Object();
    var unsupportedValue = new Object();
    var firstValue = new EqualValue(1);
    var secondValue = new EqualValue(2);
    var unsupported = new SelectorBasedChangeMove<>(descriptor, entity, unsupportedValue);
    var first = new SelectorBasedChangeMove<>(descriptor, entity, firstValue);
    var second = new SelectorBasedChangeMove<>(descriptor, entity, secondValue);
    var profile =
        MeterProfile.of(
            new ConstructionHeuristicNearbyProfile(
                descriptor,
                TypedValueMeter.class,
                ArgumentShape.ENTITY_VALUE,
                "filtered local search"));
    TypedValueMeter.calls.set(0);

    var origin =
        ConstructionHeuristicNearbyRanking.rank(
            List.of(unsupported, second, first),
            descriptor,
            List.of(profile),
            false,
            mockDirector());

    assertThat(drain(origin.rankedMoves())).containsExactly(first, unsupported, second);
    assertThat(TypedValueMeter.calls).hasValue(2);
  }

  @Test
  void providerFailureReportsItsContractContextAndOriginalCause() {
    var descriptor = scalarDescriptor();
    when(descriptor.getSimpleEntityAndVariableName()).thenReturn("Entity.value");
    var entity = new Object();
    var value = new Object();
    var cause = new IllegalArgumentException("provider failure");
    var profile =
        profile(
            descriptor,
            ArgumentShape.ENTITY_VALUE,
            (origin, destination) -> {
              throw cause;
            });
    var move = new SelectorBasedChangeMove<>(descriptor, entity, value);

    assertThatThrownBy(
            () ->
                ConstructionHeuristicNearbyRanking.rank(
                    List.of(move), descriptor, List.of(profile), false, mockDirector()))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(cause)
        .hasMessageContaining("Entity.value")
        .hasMessageContaining("unit test")
        .hasMessageContaining(TestMeter.class.getName())
        .hasMessageContaining(Object.class.getName());
  }

  @Test
  void composedCandidateIdentityUsesAtomicVariableAndObjectIdentities() {
    var descriptor = scalarDescriptor();
    var otherDescriptor = scalarDescriptor();
    var entity = new Object();
    var firstValue = new Object();
    var secondValue = new Object();
    var first = new SelectorBasedChangeMove<>(descriptor, entity, firstValue);
    var second = new SelectorBasedChangeMove<>(descriptor, entity, secondValue);
    var empty = SelectorBasedNoChangeMove.<Object>getInstance();
    var flat = SelectorBasedCompositeMove.buildMove(first, second, empty);
    var nested =
        SelectorBasedCompositeMove.buildMove(
            first,
            SelectorBasedCompositeMove.buildMove(
                new SelectorBasedChangeMove<>(descriptor, entity, secondValue), empty));
    var differentVariable =
        SelectorBasedCompositeMove.buildMove(
            first, new SelectorBasedChangeMove<>(otherDescriptor, entity, secondValue), empty);

    assertThat(ConstructionHeuristicNearbyRanking.identity(flat))
        .isEqualTo(ConstructionHeuristicNearbyRanking.identity(nested))
        .isNotEqualTo(ConstructionHeuristicNearbyRanking.identity(differentVariable));
  }

  private static MeterProfile profile(
      GenuineVariableDescriptor<?> descriptor,
      ArgumentShape shape,
      NearbyDistanceMeter<Object, Object> meter) {
    return new MeterProfile(
        new ConstructionHeuristicNearbyProfile(descriptor, TestMeter.class, shape, "unit test"),
        meter);
  }

  private static NeighborFixture neighborFixture(List<Object> neighbors) {
    var descriptor = scalarDescriptor();
    EntityDescriptor<Object> entityDescriptor = mock(EntityDescriptor.class);
    when(descriptor.getEntityDescriptor()).thenReturn(entityDescriptor);
    var director = mockDirector();
    var solution = new Object();
    when(director.getWorkingSolution()).thenReturn(solution);
    when(entityDescriptor.extractEntities(solution)).thenReturn(neighbors);
    var values = new IdentityHashMap<Object, Object>();
    when(descriptor.getValue(any()))
        .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
    return new NeighborFixture(descriptor, director, values);
  }

  private record NeighborFixture(
      GenuineVariableDescriptor<Object> descriptor,
      ScoreDirector<Object> director,
      Map<Object, Object> values) {}

  private record IndexedEntity(int index) {}

  private record ReferenceOccurrence(Move<Object> move, int sourceOrder, double distance) {}

  public static final class TypedEntityMeter
      implements NearbyDistanceMeter<IndexedEntity, IndexedEntity> {
    private static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(IndexedEntity origin, IndexedEntity destination) {
      calls.incrementAndGet();
      return Math.abs(origin.index() - destination.index());
    }
  }

  @SuppressWarnings("unchecked")
  private static GenuineVariableDescriptor<Object> scalarDescriptor() {
    return mock(GenuineVariableDescriptor.class);
  }

  @SuppressWarnings("unchecked")
  private static ScoreDirector<Object> mockDirector() {
    return mock(ScoreDirector.class);
  }

  private static <T> List<T> drain(java.util.Iterator<T> iterator) {
    var result = new ArrayList<T>();
    iterator.forEachRemaining(result::add);
    return result;
  }

  public static final class TestMeter implements NearbyDistanceMeter<Object, Object> {
    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      return 0;
    }
  }

  public static final class TypedValueMeter implements NearbyDistanceMeter<Object, EqualValue> {
    private static final AtomicInteger calls = new AtomicInteger();

    @Override
    public double getNearbyDistance(Object origin, EqualValue destination) {
      calls.incrementAndGet();
      return destination.distance;
    }
  }

  private static final class EqualValue {
    private final int distance;

    private EqualValue(int distance) {
      this.distance = distance;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EqualValue;
    }

    @Override
    public int hashCode() {
      return 0;
    }
  }
}
