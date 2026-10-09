package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.impl.cotwin.valuerange.buildin.collection.ListValueRange;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmListOperatorsTest {

  @Test
  void crossoverUsesTheSameWeightForBasicsAndCompleteLists() {
    var model = model(4, new int[][] {{0, 1, 2}, {3}}, new int[] {1, 0}, true, true);
    var slots = slots(2);
    var fixed = slots.get(1);
    slots.set(
        1,
        new GeneticAlgorithmSlot<>(
            fixed.entity(), fixed.variableDescriptor(), fixed.valueRange(), false));
    var operators =
        new GeneticAlgorithmOperators<>(slots, model, new GeneticAlgorithmPhaseConfig().resolve());
    var first = new GeneticAlgorithmGenome(new Object[] {1, 7}, model.initialLists());
    var second = new GeneticAlgorithmGenome(new Object[] {9, 7}, new int[][] {{0, 3}, {2}});
    var random = random(new double[] {0.5, Math.nextDown(0.5)});

    var crossed = operators.cross(first, second, random);

    assertThat(crossed.crossed()).isTrue();
    assertThat(crossed.first().toArray()).containsExactly(9, 7);
    assertThat(crossed.second().toArray()).containsExactly(1, 7);
    assertLists(crossed.first().lists(), second.lists());
    assertLists(crossed.second().lists(), first.lists());
    assertThat(crossed.first().listSnapshot()).isSameAs(second.listSnapshot());
    assertThat(crossed.second().listSnapshot()).isSameAs(first.listSnapshot());
    assertLists(first.lists(), new int[][] {{0, 1, 2}, {3}});
    assertLists(second.lists(), new int[][] {{0, 3}, {2}});
    random.assertExhausted();

    var retainRandom = random(new double[] {0.5, 0.5});
    var retained = operators.cross(first, second, retainRandom);
    assertThat(retained.first()).isSameAs(first);
    assertThat(retained.second()).isSameAs(second);
    retainRandom.assertExhausted();
  }

  @Test
  void eligibilityCountsMatchEnumeratedAnchorsAcrossPinsAndEmptySuffixes() {
    var model = model(7, new int[][] {{0, 1, 2}, {3, 4}}, new int[] {1, 0}, true, true);
    var listOperators =
        new GeneticAlgorithmListOperators<>(model, new GeneticAlgorithmPhaseConfig().resolve());
    for (var lists :
        List.of(
            new int[][] {{0, 1, 2}, {3, 4}},
            new int[][] {{0}, {1, 2, 3, 4, 5, 6}},
            new int[][] {{0, 1}, {2}},
            new int[][] {{0}, {}},
            new int[][] {{0, 1, 2, 3}, {}})) {
      int anchors = 0;
      int edges = 0;
      boolean scramble = false;
      boolean inverse = false;
      for (int owner = 0; owner < lists.length; owner++) {
        if (!model.ownerMovable(owner)) continue;
        int suffix = lists[owner].length - model.firstUnpinnedIndex(owner);
        anchors += suffix;
        edges += Math.max(0, suffix - 1);
        scramble |= suffix >= 3;
        inverse |= suffix >= 2;
      }
      var eligibility = listOperators.eligibility(new GeneticAlgorithmListSnapshot(lists));
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.SWAP)).isEqualTo(anchors >= 2);
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.SWAP_EDGES))
          .isEqualTo(edges >= 2);
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.SCRAMBLE)).isEqualTo(scramble);
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.INVERSE)).isEqualTo(inverse);
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.INSERTION))
          .isEqualTo(anchors > 0);
      assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.CHANGE)).isTrue();
    }
  }

  @Test
  void repeatedEligibilityDoesNotRecheckRecipientRanges() {
    var model = model(2, new int[][] {{0}, {1}}, false);
    var listOperators =
        new GeneticAlgorithmListOperators<>(model, new GeneticAlgorithmPhaseConfig().resolve());
    var rangeChecks = new AtomicInteger();
    when(model.accepts(anyInt(), anyInt()))
        .thenAnswer(
            ignored -> {
              rangeChecks.incrementAndGet();
              return true;
            });
    var eligibility = listOperators.eligibility(model.initialSnapshot());
    assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.CHANGE)).isTrue();
    int checked = rangeChecks.get();
    assertThat(checked).isPositive();
    assertThat(eligibility.isEligible(GeneticAlgorithmMutationType.CHANGE)).isTrue();
    assertThat(rangeChecks.get()).isEqualTo(checked);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void primitiveRelocationMatchesBoxedReferenceThroughSequentialMultiChanges(boolean optional) {
    var initial = new int[][] {{0, 1, 2}, {3, 4}};
    var prefixes = new int[] {1, 0};
    var model = model(5, initial, prefixes, optional, true);
    var operators =
        new GeneticAlgorithmListOperators<>(
            model, new GeneticAlgorithmPhaseConfig().withMutationRateMultiplier(4.0).resolve());
    var actualRandom = new Random(37);
    var referenceRandom = new Random(37);
    var actual = model.initialLists();
    var reference = model.initialLists();
    for (int iteration = 0; iteration < 100; iteration++) {
      // Rate one selects all four movable values. A preceding relocation may move or unassign
      // the values selected later in this same mutation, and pinned value zero must stay put.
      actual = operators.mutate(GeneticAlgorithmMutationType.CHANGE, actual, actualRandom);
      reference =
          boxedRelocationReference(
              reference, prefixes, new int[] {1, 2, 3, 4}, optional, true, referenceRandom);
      assertLists(actual, reference);
      assertValid(actual, model);
      var assigned = new ArrayList<Integer>();
      for (int owner = 0; owner < reference.length; owner++) {
        for (int index = prefixes[owner]; index < reference[owner].length; index++) {
          assigned.add(reference[owner][index]);
        }
      }
      if (!assigned.isEmpty()) {
        actual = operators.mutate(GeneticAlgorithmMutationType.INSERTION, actual, actualRandom);
        reference =
            boxedRelocationReference(
                reference,
                prefixes,
                assigned.stream().mapToInt(Integer::intValue).toArray(),
                false,
                false,
                referenceRandom);
        assertLists(actual, reference);
        assertValid(actual, model);
      }
    }
    assertThat(actualRandom.nextLong()).isEqualTo(referenceRandom.nextLong());
    assertLists(model.initialLists(), initial);
  }

  private static int[][] boxedRelocationReference(
      int[][] input,
      int[] prefixes,
      int[] candidates,
      boolean allowUnassigned,
      boolean changeAll,
      Random random) {
    var lists = new ArrayList<List<Integer>>();
    for (var row : input) lists.add(new ArrayList<>(Arrays.stream(row).boxed().toList()));
    var available = new ArrayList<>(Arrays.stream(candidates).boxed().toList());
    int count = changeAll ? candidates.length : 1;
    if (changeAll) {
      // Keep the old Bernoulli sampling draws, including when probability is exactly one.
      for (int ignored : candidates) random.nextDouble();
    }
    var selected = new ArrayList<Integer>();
    for (int index = 0; index < count; index++) {
      Collections.swap(available, index, random.nextInt(index, available.size()));
      selected.add(available.get(index));
    }
    for (Integer value : selected) {
      // The boxed reference locates the value from the current lists, independently of the
      // primitive implementation's maintained owner array and arraycopy boundaries.
      for (var row : lists) row.remove(value);
      int owner = random.nextInt(lists.size() + (allowUnassigned ? 1 : 0));
      if (owner < lists.size()) {
        var row = lists.get(owner);
        row.add(random.nextInt(prefixes[owner], row.size() + 1), value);
      }
    }
    return lists.stream()
        .map(row -> row.stream().mapToInt(Integer::intValue).toArray())
        .toArray(int[][]::new);
  }

  @Test
  void changeSupportsTransferUnassignmentAndAssignmentWithoutLosingOtherValues() {
    var model = model(2, new int[][] {{0}, {}}, true);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.CHANGE));
    var initial = genome(model.initialLists());
    var unassignRandom = random(new double[] {0, 0, 0}, 0, 0, 2);
    var unassigned = operators.mutate(initial, unassignRandom).genome();
    assertLists(unassigned.lists(), new int[][] {{}, {}});
    unassignRandom.assertExhausted();

    var assignRandom = random(new double[] {0, 0, 0}, 0, 1, 1, 0);
    var assigned = operators.mutate(unassigned, assignRandom).genome();
    assertLists(assigned.lists(), new int[][] {{}, {1}});
    assignRandom.assertExhausted();

    var transferRandom = random(new double[] {0, 0, 0}, 0, 1, 0, 0);
    var transferred = operators.mutate(assigned, transferRandom).genome();
    assertLists(transferred.lists(), new int[][] {{1}, {}});
    transferRandom.assertExhausted();
    assertLists(initial.lists(), new int[][] {{0}, {}});
  }

  @Test
  void swapRotatesPositionsAcrossOwnersAndLeavesRangeRejectionToWorkspace() {
    var model = model(4, new int[][] {{0, 1}, {2, 3}}, false);
    when(model.accepts(anyInt(), anyInt()))
        .thenAnswer(
            invocation -> (int) invocation.getArgument(0) == (int) invocation.getArgument(1) / 2);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.SWAP));
    var random = random(new double[] {0, 0, 0, 0, 0}, 0, 0, 2);
    var result = operators.mutate(genome(model.initialLists()), random).genome();
    assertLists(result.lists(), new int[][] {{2, 1}, {0, 3}});
    assertThat(model.accepts(0, result.list(0)[0])).isFalse();
    assertThat(model.accepts(1, result.list(1)[0])).isFalse();
    random.assertExhausted();
  }

  @Test
  void edgeSwapUsesRealEdgesAndRetainsSequentialOverlapSemantics() {
    var model = model(6, new int[][] {{0, 1, 2, 3}, {4, 5}}, new int[] {1, 0}, false, true);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.SWAP_EDGES));
    var random = random(new double[] {0, 0, 0, 0}, 0, 0, 2);
    var result = operators.mutate(genome(model.initialLists()), random).genome();
    assertLists(result.lists(), new int[][] {{0, 4, 5, 3}, {1, 2}});
    random.assertExhausted();

    var overlap = model(3, new int[][] {{0, 1, 2}}, false);
    var overlapping =
        new GeneticAlgorithmOperators<>(
            List.of(), overlap, config(GeneticAlgorithmMutationType.SWAP_EDGES));
    var overlapRandom = random(new double[] {0, 0, 0}, 0, 0, 1);
    assertLists(
        overlapping.mutate(genome(overlap.initialLists()), overlapRandom).genome().lists(),
        new int[][] {{1, 2, 0}});
    overlapRandom.assertExhausted();

    var singletonOwners = model(3, new int[][] {{0}, {1}, {2}}, false);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new GeneticAlgorithmOperators<>(
                    List.of(), singletonOwners, config(GeneticAlgorithmMutationType.SWAP_EDGES)))
        .withMessageContaining("no enabled mutation eligible");
  }

  @Test
  void insertionTransfersOneAssignedValueAndInverseStaysWithinItsOwnerSuffix() {
    var model = model(5, new int[][] {{0, 1, 2, 3}, {4}}, new int[] {1, 0}, false, true);
    var insertion =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.INSERTION));
    var insertRandom = random(new double[] {0}, 0, 0, 1, 1);
    assertLists(
        insertion.mutate(genome(model.initialLists()), insertRandom).genome().lists(),
        new int[][] {{0, 2, 3}, {4, 1}});
    insertRandom.assertExhausted();

    var inverse =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.INVERSE));
    var inverseRandom = random(new double[] {0}, 0, 0, 0, 2);
    assertLists(
        inverse.mutate(genome(model.initialLists()), inverseRandom).genome().lists(),
        new int[][] {{0, 3, 2, 1}, {4}});
    inverseRandom.assertExhausted();
  }

  @Test
  void scrambleUsesAContiguousBoundedSuffixSegment() {
    var model = model(9, new int[][] {{0, 1, 2, 3, 4, 5, 6, 7, 8}}, new int[] {2}, false, true);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.SCRAMBLE));
    var random = random(new double[] {0}, 0, 0, 3, 1, 0, 0);
    assertLists(
        operators.mutate(genome(model.initialLists()), random).genome().lists(),
        new int[][] {{0, 1, 2, 4, 5, 3, 6, 7, 8}});
    random.assertExhausted();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void seedPreservesPinsAndUsesEveryRecipientsActualRange(boolean optional) {
    var model =
        model(
            8,
            new int[][] {{0, 1}, {2, 3, 4}, {5, 6, 7}},
            new int[] {0, 1, 0},
            optional,
            false,
            true,
            true);
    when(model.accepts(anyInt(), anyInt()))
        .thenAnswer(
            invocation -> {
              int owner = invocation.getArgument(0);
              int value = invocation.getArgument(1);
              return owner == 0 ? value < 2 : owner == 1 ? value >= 2 && value <= 4 : value >= 5;
            });
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.CHANGE));
    var initial = genome(model.initialLists());
    var random = new Random(19);
    var signatures = new HashSet<String>();
    for (int attempt = 0; attempt < 100; attempt++) {
      var seed = operators.sampleSeed(initial, random);
      assertValid(seed.lists(), model);
      signatures.add(Arrays.deepToString(seed.lists()));
    }
    assertThat(signatures.size()).isGreaterThan(1);
    assertLists(initial.lists(), model.initialLists());
  }

  @ParameterizedTest
  @EnumSource(GeneticAlgorithmMutationType.class)
  void repeatedMutationsPreserveMembershipAndPinsAsTopologyAndTabuChange(
      GeneticAlgorithmMutationType type) {
    for (boolean optional : new boolean[] {false, true}) {
      var model =
          model(
              12,
              new int[][] {{0, 1}, {2, 3, 4, 5, 6, 7, 8}, {9, 10, 11}},
              new int[] {0, 1, 0},
              optional,
              false,
              true,
              true);
      var operators =
          new GeneticAlgorithmOperators<>(
              List.of(),
              model,
              config(type).withMutationRateMultiplier(5.0).withTabuEntityRate(1.0).resolve());
      var current = genome(model.initialLists());
      var random = new Random(37);
      for (int attempt = 0; attempt < 200; attempt++) {
        var previous = current;
        var previousLists = previous.lists();
        current = operators.mutate(current, random).genome();
        assertValid(current.lists(), model);
        assertLists(previous.lists(), previousLists);
      }
    }
  }

  @Test
  void dynamicEmptyPortfolioSkipsMutationWithoutRandomDraws() {
    var model = model(3, new int[][] {{0, 1}, {2}, {}}, false);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.INVERSE));
    var later = genome(new int[][] {{0}, {1}, {2}});
    var random = mock(RandomGenerator.class);
    var mutation = operators.mutate(later, random);
    assertThat(mutation.genome()).isSameAs(later);
    assertThat(mutation.type()).isNull();
    assertThat(mutation.group()).isNull();
    verifyNoInteractions(random);

    var initiallyIneligible = model(3, later.lists(), false);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new GeneticAlgorithmOperators<>(
                    List.of(), initiallyIneligible, config(GeneticAlgorithmMutationType.INVERSE)))
        .withMessageContaining("no enabled mutation eligible");
  }

  @Test
  void mixedGroupSelectionRetainsListsForBasicMutationAndBasicsForListMutation() {
    var model = model(2, new int[][] {{0, 1}, {}}, false);
    var operators =
        new GeneticAlgorithmOperators<>(
            slots(2), model, config(GeneticAlgorithmMutationType.CHANGE));
    var initial = new GeneticAlgorithmGenome(new Object[] {0, 1}, model.initialLists());
    var basicRandom = random(new double[] {0, 0, 0}, 0, 0).withLongs(9);
    var basic = operators.mutate(initial, basicRandom);
    assertThat(basic.genome().toArray()).containsExactly(9, 1);
    assertLists(basic.genome().lists(), initial.lists());
    assertThat(basic.group()).isNotEqualTo(model.name());
    basicRandom.assertExhausted();

    var listRandom = random(new double[] {0, 0, 0}, 1, 0, 1, 0);
    var list = operators.mutate(initial, listRandom);
    assertThat(list.genome().toArray()).containsExactly(0, 1);
    assertLists(list.genome().lists(), new int[][] {{1}, {0}});
    assertThat(list.group()).isEqualTo(model.name());
    listRandom.assertExhausted();
  }

  @Test
  void mixedPortfolioUsesAnEligibleBasicGroupWhenListTopologyBecomesIneligible() {
    var model = model(3, new int[][] {{0, 1}, {2}, {}}, false);
    var operators =
        new GeneticAlgorithmOperators<>(
            slots(2), model, config(GeneticAlgorithmMutationType.INVERSE));
    var later = new GeneticAlgorithmGenome(new Object[] {0, 1}, new int[][] {{0}, {1}, {2}});
    var random = random(new double[] {0}, 0, 0, 1);
    var mutation = operators.mutate(later, random);
    assertThat(mutation.genome().toArray()).containsExactly(1, 0);
    assertLists(mutation.genome().lists(), later.lists());
    random.assertExhausted();
  }

  @Test
  void listOperatorWeightsRenormalizeWhenSegmentsDisappear() {
    var model = model(3, new int[][] {{0, 1}, {2}, {}}, false);
    var config =
        new GeneticAlgorithmPhaseConfig()
            .withMutationOperators(
                new GeneticAlgorithmMutationOperatorConfig()
                    .withType(GeneticAlgorithmMutationType.CHANGE)
                    .withProbability(0.2),
                new GeneticAlgorithmMutationOperatorConfig()
                    .withType(GeneticAlgorithmMutationType.INVERSE)
                    .withProbability(0.8))
            .resolve();
    var operators = new GeneticAlgorithmOperators<>(List.of(), model, config);
    var later = genome(new int[][] {{0}, {1}, {2}});
    var random = random(new double[] {Math.nextDown(1.0), 0, 0, 0}, 0, 0, 1, 1);
    var mutation = operators.mutate(later, random);
    assertThat(mutation.type()).isEqualTo(GeneticAlgorithmMutationType.CHANGE);
    assertLists(mutation.genome().lists(), new int[][] {{}, {1, 0}, {2}});
    random.assertExhausted();
  }

  @Test
  void tabuFollowsAValueWhenItsOwnerChanges() {
    var model = model(3, new int[][] {{0, 1, 2}, {}}, false);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(),
            model,
            config(GeneticAlgorithmMutationType.INSERTION).withTabuEntityRate(0.3).resolve());
    var firstRandom = random(new double[] {0}, 0, 0, 1, 0);
    var first = operators.mutate(genome(model.initialLists()), firstRandom).genome();
    assertLists(first.lists(), new int[][] {{1, 2}, {0}});
    firstRandom.assertExhausted();
    // ID 0 moved to the final assigned position. It remains tabu there; ID 2 is selected instead.
    var secondRandom = random(new double[] {0}, 0, 1, 1, 0);
    var second = operators.mutate(first, secondRandom).genome();
    assertLists(second.lists(), new int[][] {{1}, {2, 0}});
    secondRandom.assertExhausted();
  }

  @Test
  void pinnedOwnersAndUnassignedValuesWithoutDestinationsHaveNoMovableAssignments() {
    var model = model(3, new int[][] {{0}, {1}}, new int[] {0, 0}, true, false, false);
    var operators =
        new GeneticAlgorithmOperators<>(
            List.of(), model, config(GeneticAlgorithmMutationType.CHANGE));
    assertThat(operators.hasMovableSlots()).isFalse();
    assertThat(operators.sampleSeed(genome(model.initialLists()), new Random(0)))
        .isEqualTo(genome(model.initialLists()));
    var random = mock(RandomGenerator.class);
    assertThat(operators.mutate(genome(model.initialLists()), random).type()).isNull();
    verifyNoInteractions(random);
  }

  private static void assertValid(int[][] lists, GeneticAlgorithmListModel<?> model) {
    var seen = new HashSet<Integer>();
    var initial = model.initialLists();
    assertThat(lists.length).isEqualTo(model.ownerCount());
    for (int owner = 0; owner < lists.length; owner++) {
      if (!model.ownerMovable(owner)) {
        assertThat(lists[owner]).containsExactly(initial[owner]);
      }
      int pinned = model.firstUnpinnedIndex(owner);
      assertThat(lists[owner].length).isGreaterThanOrEqualTo(pinned);
      assertThat(Arrays.copyOf(lists[owner], pinned))
          .containsExactly(Arrays.copyOf(initial[owner], pinned));
      for (int value : lists[owner]) {
        assertThat(value).isBetween(0, model.valueCount() - 1);
        assertThat(seen.add(value)).as("unique value %s", value).isTrue();
        assertThat(model.accepts(owner, value)).as("owner %s accepts %s", owner, value).isTrue();
      }
    }
    if (!model.allowsUnassignedValues()) {
      assertThat(seen).hasSize(model.valueCount());
    }
  }

  private static void assertLists(int[][] actual, int[][] expected) {
    assertThat(actual.length).isEqualTo(expected.length);
    for (int owner = 0; owner < expected.length; owner++) {
      assertThat(actual[owner]).as("owner %s", owner).containsExactly(expected[owner]);
    }
  }

  private static GeneticAlgorithmGenome genome(int[][] lists) {
    return new GeneticAlgorithmGenome(new Object[0], lists);
  }

  private static GeneticAlgorithmPhaseConfig config(GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmPhaseConfig()
        .withMutationOperators(
            new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(1.0))
        .resolve();
  }

  private static List<GeneticAlgorithmSlot<TestdataSolution>> slots(int count) {
    var slots = new ArrayList<GeneticAlgorithmSlot<TestdataSolution>>();
    var descriptor = TestdataEntity.buildVariableDescriptorForValue();
    var range =
        new ListValueRange<Object>(IntStream.range(0, 10).mapToObj(i -> (Object) i).toList());
    for (int i = 0; i < count; i++) {
      slots.add(new GeneticAlgorithmSlot<>(new TestdataEntity("e" + i), descriptor, range, true));
    }
    return slots;
  }

  private static GeneticAlgorithmListModel<TestdataSolution> model(
      int valueCount, int[][] lists, boolean optional) {
    var movable = new boolean[lists.length];
    Arrays.fill(movable, true);
    return model(valueCount, lists, new int[lists.length], optional, movable);
  }

  @SuppressWarnings("unchecked")
  private static GeneticAlgorithmListModel<TestdataSolution> model(
      int valueCount, int[][] lists, int[] pinnedPrefixes, boolean optional, boolean... movable) {
    var model = (GeneticAlgorithmListModel<TestdataSolution>) mock(GeneticAlgorithmListModel.class);
    // A single flag applies to every owner; explicit arrays can describe pinned owners
    // individually.
    when(model.ownerCount()).thenReturn(lists.length);
    when(model.valueCount()).thenReturn(valueCount);
    when(model.name()).thenReturn("Owner.values");
    when(model.initialLists())
        .thenAnswer(ignored -> Arrays.stream(lists).map(int[]::clone).toArray(int[][]::new));
    when(model.initialSnapshot()).thenReturn(new GeneticAlgorithmListSnapshot(lists));
    when(model.allowsUnassignedValues()).thenReturn(optional);
    when(model.accepts(anyInt(), anyInt())).thenReturn(true);
    when(model.value(anyInt())).thenAnswer(invocation -> "v" + invocation.getArgument(0));
    var fixedValues = new HashSet<Integer>();
    for (int owner = 0; owner < lists.length; owner++) {
      boolean ownerMovable = movable.length == 1 ? movable[0] : movable[owner];
      when(model.ownerMovable(owner)).thenReturn(ownerMovable);
      when(model.firstUnpinnedIndex(owner)).thenReturn(pinnedPrefixes[owner]);
      int fixedCount = ownerMovable ? pinnedPrefixes[owner] : lists[owner].length;
      for (int i = 0; i < fixedCount; i++) {
        fixedValues.add(lists[owner][i]);
      }
    }
    var movableIds =
        IntStream.range(0, valueCount).filter(id -> !fixedValues.contains(id)).toArray();
    when(model.movableValueIds()).thenAnswer(ignored -> movableIds.clone());
    return model;
  }

  private static ScriptedRandom random(double[] doubles, int... integers) {
    return new ScriptedRandom(doubles, integers);
  }

  private static final class ScriptedRandom implements RandomGenerator {
    private final ArrayDeque<Double> doubles = new ArrayDeque<>();
    private final ArrayDeque<Integer> integers = new ArrayDeque<>();
    private final ArrayDeque<Long> longs = new ArrayDeque<>();

    private ScriptedRandom(double[] doubles, int[] integers) {
      Arrays.stream(doubles).forEach(this.doubles::addLast);
      Arrays.stream(integers).forEach(this.integers::addLast);
    }

    private ScriptedRandom withLongs(long... values) {
      Arrays.stream(values).forEach(longs::addLast);
      return this;
    }

    @Override
    public double nextDouble() {
      double value = doubles.removeFirst();
      assertThat(value).isGreaterThanOrEqualTo(0).isLessThan(1);
      return value;
    }

    @Override
    public int nextInt(int bound) {
      return nextInt(0, bound);
    }

    @Override
    public int nextInt(int origin, int bound) {
      int value = integers.removeFirst();
      assertThat(value).isGreaterThanOrEqualTo(origin).isLessThan(bound);
      return value;
    }

    @Override
    public long nextLong(long bound) {
      long value = longs.removeFirst();
      assertThat(value).isGreaterThanOrEqualTo(0).isLessThan(bound);
      return value;
    }

    @Override
    public long nextLong() {
      throw new AssertionError("Unexpected unbounded random draw.");
    }

    private void assertExhausted() {
      assertThat(doubles).isEmpty();
      assertThat(integers).isEmpty();
      assertThat(longs).isEmpty();
    }
  }
}
