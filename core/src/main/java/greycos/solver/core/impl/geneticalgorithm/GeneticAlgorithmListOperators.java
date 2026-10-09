package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;

/**
 * One representation-aware mutation group for the model's planning list variable. All construction
 * happens on detached arrays; the workspace validates recipient ranges before any notification.
 */
final class GeneticAlgorithmListOperators<Solution_> {

  private record Anchor(int value, int owner, int index) {}

  private final GeneticAlgorithmListModel<Solution_> model;
  private final int[] movableValues;
  private final double mutationRate;
  private final int tenure;
  private final boolean movable;
  private final ArrayDeque<Integer> recent = new ArrayDeque<>();
  private final HashSet<Integer> tabu = new HashSet<>();

  GeneticAlgorithmListOperators(
      GeneticAlgorithmListModel<Solution_> model, GeneticAlgorithmPhaseConfig config) {
    this.model = model;
    movableValues = model.movableValueIds();
    mutationRate =
        movableValues.length == 0
            ? 0.0
            : Math.min(config.getMutationRateMultiplier() / movableValues.length, 1.0);
    tenure = (int) Math.ceil(config.getTabuEntityRate() * movableValues.length);
    movable = hasAlternativeDestination(model.initialSnapshot(), true);
  }

  String name() {
    return model.name();
  }

  int ownerCount() {
    return model.ownerCount();
  }

  boolean hasMovableValues() {
    return movable;
  }

  Eligibility eligibility(GeneticAlgorithmListSnapshot lists) {
    return new Eligibility(lists);
  }

  /** One proposal's lazy eligibility checks; mutation and tabu state are never cached. */
  final class Eligibility {
    private final GeneticAlgorithmListSnapshot lists;
    private int checked;
    private int eligible;

    private Eligibility(GeneticAlgorithmListSnapshot lists) {
      this.lists = lists;
    }

    boolean isEligible(GeneticAlgorithmMutationType type) {
      int bit = 1 << type.ordinal();
      if ((checked & bit) == 0) {
        if (GeneticAlgorithmListOperators.this.isEligible(type, lists)) eligible |= bit;
        checked |= bit;
      }
      return (eligible & bit) != 0;
    }
  }

  private boolean isEligible(
      GeneticAlgorithmMutationType type, GeneticAlgorithmListSnapshot lists) {
    return switch (type) {
      case CHANGE -> hasAlternativeDestination(lists, true);
      case SWAP -> hasAssignedAnchors(lists, false);
      case SWAP_EDGES -> hasAssignedAnchors(lists, true);
      case SCRAMBLE -> hasEligibleOwner(lists, 3);
      case INSERTION -> hasAlternativeDestination(lists, false);
      case INVERSE -> hasEligibleOwner(lists, 2);
    };
  }

  private boolean hasAssignedAnchors(GeneticAlgorithmListSnapshot lists, boolean edgeStarts) {
    int count = 0;
    for (int owner = 0; owner < lists.ownerCount(); owner++) {
      if (model.ownerMovable(owner)) {
        count +=
            Math.max(0, lists.size(owner) - model.firstUnpinnedIndex(owner) - (edgeStarts ? 1 : 0));
        if (count >= 2) return true;
      }
    }
    return false;
  }

  private boolean hasEligibleOwner(GeneticAlgorithmListSnapshot lists, int minimumSuffixSize) {
    for (int owner = 0; owner < lists.ownerCount(); owner++) {
      if (model.ownerMovable(owner)
          && lists.size(owner) - model.firstUnpinnedIndex(owner) >= minimumSuffixSize) {
        return true;
      }
    }
    return false;
  }

  int[][] sampleSeed(RandomGenerator random) {
    var initial = model.initialSnapshot();
    var lists = new ArrayList<List<Integer>>(initial.ownerCount());
    for (int owner = 0; owner < initial.ownerCount(); owner++) {
      var list = new ArrayList<Integer>(initial.size(owner));
      for (int index = 0; index < initial.size(owner); index++) {
        list.add(initial.get(owner, index));
      }
      lists.add(list);
    }
    for (int owner = 0; owner < lists.size(); owner++) {
      if (model.ownerMovable(owner)) {
        var list = lists.get(owner);
        list.subList(model.firstUnpinnedIndex(owner), list.size()).clear();
      }
    }
    var shuffled = movableValues.clone();
    for (int i = shuffled.length - 1; i > 0; i--) {
      int other = random.nextInt(i + 1);
      int temporary = shuffled[i];
      shuffled[i] = shuffled[other];
      shuffled[other] = temporary;
    }
    for (int value : shuffled) {
      var owners = destinations(value, model.allowsUnassignedValues());
      if (owners.isEmpty()) {
        // A required movable value must have at least its initialized owner available.
        throw new IllegalStateException(
            "The geneticAlgorithm list value ("
                + model.value(value)
                + ") has no compatible movable owner for variable ("
                + model.name()
                + ").");
      }
      int owner = owners.get(random.nextInt(owners.size()));
      if (owner >= 0) {
        lists.get(owner).add(value);
      }
    }
    return toArrays(lists);
  }

  int[][] mutate(GeneticAlgorithmMutationType type, int[][] lists, RandomGenerator random) {
    // The caller passes an owned snapshot; never retain it in tabu or other operator state.
    switch (type) {
      case CHANGE -> {
        var anchors = new ArrayList<Anchor>(movableValues.length);
        for (int value : movableValues) {
          anchors.add(new Anchor(value, -1, -1));
        }
        var selected = selectAnchors(anchors, changeCount(anchors.size(), 1, random), random);
        var ownership = ownership(lists);
        for (var anchor : selected) {
          relocate(lists, ownership, anchor.value(), model.allowsUnassignedValues(), random);
        }
      }
      case SWAP -> {
        var anchors = assignedAnchors(lists, false);
        var selected = selectAnchors(anchors, changeCount(anchors.size(), 2, random), random);
        for (int i = 0; i < selected.size(); i++) {
          var target = selected.get(i);
          lists[target.owner()][target.index()] = selected.get((i + 1) % selected.size()).value();
        }
      }
      case SWAP_EDGES -> {
        var anchors = assignedAnchors(lists, true);
        var selected = selectAnchors(anchors, changeCount(anchors.size(), 2, random), random);
        // Match the basic operator's sequential overlap behavior, using actual within-owner edges.
        for (int i = 0; i < selected.size() - 1; i++) {
          var first = selected.get((i + 1) % selected.size());
          var second = selected.get((i + 2) % selected.size());
          swap(lists, first.owner(), first.index(), second.owner(), second.index());
          swap(lists, first.owner(), first.index() + 1, second.owner(), second.index() + 1);
        }
      }
      case SCRAMBLE -> {
        var owners = eligibleOwners(lists, 3);
        int owner = owners.get(random.nextInt(owners.size()));
        int suffixSize = lists[owner].length - model.firstUnpinnedIndex(owner);
        int length = random.nextInt(3, Math.min(suffixSize, 6) + 1);
        var starts = anchors(lists, owner, lists[owner].length - length + 1);
        int start = selectAnchors(starts, 1, random).getFirst().index();
        for (int i = length - 1; i > 0; i--) {
          swap(lists, owner, start + i, owner, start + random.nextInt(i + 1));
        }
      }
      case INSERTION -> {
        var selected = selectAnchors(assignedAnchors(lists, false), 1, random).getFirst();
        relocate(lists, ownership(lists), selected.value(), false, random);
      }
      case INVERSE -> {
        var owners = eligibleOwners(lists, 2);
        int owner = owners.get(random.nextInt(owners.size()));
        var selected = selectAnchors(anchors(lists, owner, lists[owner].length), 2, random);
        int left = Math.min(selected.getFirst().index(), selected.getLast().index());
        int right = Math.max(selected.getFirst().index(), selected.getLast().index());
        while (left < right) {
          swap(lists, owner, left++, owner, right--);
        }
      }
    }
    return lists;
  }

  private boolean hasAlternativeDestination(
      GeneticAlgorithmListSnapshot lists, boolean allowMembershipChange) {
    var ownership = ownership(lists);
    for (int value : movableValues) {
      int current = ownership[value];
      if (current < 0 && !allowMembershipChange) {
        continue;
      }
      if (current >= 0) {
        if (allowMembershipChange && model.allowsUnassignedValues()) {
          return true;
        }
        if (lists.size(current) - model.firstUnpinnedIndex(current) > 1) {
          return true;
        }
      }
      for (int owner = 0; owner < model.ownerCount(); owner++) {
        if (owner != current && model.ownerMovable(owner) && model.accepts(owner, value)) {
          return true;
        }
      }
    }
    return false;
  }

  private List<Integer> eligibleOwners(int[][] lists, int minimumSuffixSize) {
    var owners = new ArrayList<Integer>();
    for (int owner = 0; owner < lists.length; owner++) {
      if (model.ownerMovable(owner)
          && lists[owner].length - model.firstUnpinnedIndex(owner) >= minimumSuffixSize) {
        owners.add(owner);
      }
    }
    return owners;
  }

  private List<Anchor> assignedAnchors(int[][] lists, boolean edgeStarts) {
    var result = new ArrayList<Anchor>();
    for (int owner = 0; owner < lists.length; owner++) {
      if (model.ownerMovable(owner)) {
        result.addAll(anchors(lists, owner, lists[owner].length - (edgeStarts ? 1 : 0)));
      }
    }
    return result;
  }

  private List<Anchor> anchors(int[][] lists, int owner, int rightEnd) {
    var result = new ArrayList<Anchor>();
    for (int index = model.firstUnpinnedIndex(owner); index < rightEnd; index++) {
      result.add(new Anchor(lists[owner][index], owner, index));
    }
    return result;
  }

  private List<Anchor> selectAnchors(List<Anchor> anchors, int count, RandomGenerator random) {
    var available = new ArrayList<Anchor>(anchors.size());
    while (true) {
      available.clear();
      for (var anchor : anchors) {
        if (!tabu.contains(anchor.value())) {
          available.add(anchor);
        }
      }
      if (available.size() >= count) {
        break;
      }
      // Expire by canonical value identity, even if an old anchor now has another owner/index.
      tabu.remove(recent.removeLast());
    }
    var selected = new ArrayList<Anchor>(count);
    for (int i = 0; i < count; i++) {
      int other = random.nextInt(i, available.size());
      var anchor = available.get(other);
      selected.add(anchor);
      available.set(other, available.get(i));
      available.set(i, anchor);
    }
    if (tenure > 0) {
      for (var anchor : selected) {
        tabu.add(anchor.value());
        recent.addFirst(anchor.value());
      }
      while (recent.size() > tenure) {
        tabu.remove(recent.removeLast());
      }
    }
    return selected;
  }

  private int changeCount(int capacity, int minimum, RandomGenerator random) {
    int selected = 0;
    for (int i = 0; i < capacity; i++) {
      if (random.nextDouble() < mutationRate) {
        selected++;
      }
    }
    return Math.min(capacity, Math.max(selected, minimum));
  }

  private void relocate(
      int[][] lists, int[] ownership, int value, boolean allowUnassigned, RandomGenerator random) {
    int oldOwner = ownership[value];
    if (oldOwner >= 0) {
      var oldList = lists[oldOwner];
      for (int index = 0; index < oldList.length; index++) {
        if (oldList[index] == value) {
          var shortened = new int[oldList.length - 1];
          System.arraycopy(oldList, 0, shortened, 0, index);
          System.arraycopy(oldList, index + 1, shortened, index, oldList.length - index - 1);
          lists[oldOwner] = shortened;
          break;
        }
      }
    }
    var owners = destinations(value, allowUnassigned);
    int owner = owners.get(random.nextInt(owners.size()));
    if (owner >= 0) {
      var list = lists[owner];
      int index = random.nextInt(model.firstUnpinnedIndex(owner), list.length + 1);
      var extended = new int[list.length + 1];
      System.arraycopy(list, 0, extended, 0, index);
      extended[index] = value;
      System.arraycopy(list, index, extended, index + 1, list.length - index);
      lists[owner] = extended;
    }
    ownership[value] = owner;
  }

  private List<Integer> destinations(int value, boolean allowUnassigned) {
    var owners = new ArrayList<Integer>();
    for (int owner = 0; owner < model.ownerCount(); owner++) {
      if (model.ownerMovable(owner) && model.accepts(owner, value)) {
        owners.add(owner);
      }
    }
    if (allowUnassigned) {
      owners.add(-1);
    }
    return owners;
  }

  private int[] ownership(int[][] lists) {
    var ownership = new int[model.valueCount()];
    Arrays.fill(ownership, -1);
    for (int owner = 0; owner < lists.length; owner++) {
      for (int value : lists[owner]) {
        ownership[value] = owner;
      }
    }
    return ownership;
  }

  private int[] ownership(GeneticAlgorithmListSnapshot lists) {
    var ownership = new int[model.valueCount()];
    Arrays.fill(ownership, -1);
    for (int owner = 0; owner < lists.ownerCount(); owner++) {
      for (int index = 0; index < lists.size(owner); index++) {
        ownership[lists.get(owner, index)] = owner;
      }
    }
    return ownership;
  }

  private static int[][] toArrays(List<List<Integer>> lists) {
    return lists.stream()
        .map(list -> list.stream().mapToInt(Integer::intValue).toArray())
        .toArray(int[][]::new);
  }

  private static void swap(int[][] lists, int firstOwner, int first, int secondOwner, int second) {
    int temporary = lists[firstOwner][first];
    lists[firstOwner][first] = lists[secondOwner][second];
    lists[secondOwner][second] = temporary;
  }
}
