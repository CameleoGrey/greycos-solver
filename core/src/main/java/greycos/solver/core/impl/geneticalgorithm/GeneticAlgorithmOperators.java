package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;

import org.jspecify.annotations.Nullable;

/**
 * The serial GreyJack mutation portfolio. All values are discrete assignments; legality of a
 * permutation in recipient-specific ranges is checked by the workspace before materialization.
 */
public final class GeneticAlgorithmOperators<Solution_> {

  private static final double MINIMUM_RANK_FRACTION = 0.000001;

  public record Mutation(
      GeneticAlgorithmGenome genome,
      @Nullable GeneticAlgorithmMutationType type,
      @Nullable String group) {}

  public record Pair(
      GeneticAlgorithmGenome first, GeneticAlgorithmGenome second, boolean crossed) {}

  private record GroupKey(Class<?> entityClass, BasicVariableDescriptor<?> variableDescriptor) {}

  private static final class Group {
    private final String name;
    private final int[] slots;
    private final double mutationRate;
    private final int tenure;
    private final ArrayDeque<Integer> recent = new ArrayDeque<>();
    private final HashSet<Integer> members = new HashSet<>();

    private Group(String name, List<Integer> slots, double multiplier, double tabuRate) {
      this.name = name;
      this.slots = slots.stream().mapToInt(Integer::intValue).toArray();
      this.mutationRate = Math.min(multiplier / slots.size(), 1.0);
      this.tenure = (int) Math.ceil(tabuRate * slots.size());
    }
  }

  private record EligibleOperator(
      GeneticAlgorithmMutationType type, double weight, List<Group> groups) {}

  private final List<GeneticAlgorithmSlot<Solution_>> slots;
  private final @Nullable GeneticAlgorithmListOperators<Solution_> listOperators;
  private final List<EligibleOperator> operators;
  private final double totalWeight;
  private final double crossoverProbability;
  private final boolean movableSlots;

  public GeneticAlgorithmOperators(
      List<GeneticAlgorithmSlot<Solution_>> slots, GeneticAlgorithmPhaseConfig resolvedConfig) {
    this(slots, null, resolvedConfig);
  }

  public GeneticAlgorithmOperators(
      List<GeneticAlgorithmSlot<Solution_>> slots,
      @Nullable GeneticAlgorithmListModel<Solution_> listModel,
      GeneticAlgorithmPhaseConfig resolvedConfig) {
    this.slots = List.copyOf(slots);
    listOperators =
        listModel == null ? null : new GeneticAlgorithmListOperators<>(listModel, resolvedConfig);
    crossoverProbability = resolvedConfig.getCrossoverProbability();
    var groupedSlots = new LinkedHashMap<GroupKey, List<Integer>>();
    for (int i = 0; i < slots.size(); i++) {
      var slot = slots.get(i);
      if (slot.movable()) {
        var descriptor = slot.variableDescriptor();
        var key = new GroupKey(descriptor.getEntityDescriptor().getEntityClass(), descriptor);
        groupedSlots.computeIfAbsent(key, ignored -> new ArrayList<>()).add(i);
      }
    }
    movableSlots =
        !groupedSlots.isEmpty() || (listOperators != null && listOperators.hasMovableValues());
    var groups = new ArrayList<Group>(groupedSlots.size());
    groupedSlots.forEach(
        (key, ids) ->
            groups.add(
                new Group(
                    key.entityClass().getName() + "." + key.variableDescriptor().getVariableName(),
                    ids,
                    resolvedConfig.getMutationRateMultiplier(),
                    resolvedConfig.getTabuEntityRate())));
    var weights =
        new EnumMap<GeneticAlgorithmMutationType, Double>(GeneticAlgorithmMutationType.class);
    for (var config : resolvedConfig.getMutationOperatorConfigList()) {
      weights.put(config.getType(), config.getProbability());
    }
    var eligibleOperators = new ArrayList<EligibleOperator>();
    for (var type : GeneticAlgorithmMutationType.values()) {
      double weight = weights.getOrDefault(type, 0.0);
      if (weight == 0.0) {
        continue;
      }
      var eligibleGroups =
          groups.stream().filter(group -> group.slots.length >= minimum(type)).toList();
      if (!eligibleGroups.isEmpty() || listOperators != null) {
        eligibleOperators.add(new EligibleOperator(type, weight, eligibleGroups));
      }
    }
    operators = List.copyOf(eligibleOperators);
    totalWeight = operators.stream().mapToDouble(EligibleOperator::weight).sum();
    if (movableSlots
        && (listOperators == null
            ? operators.isEmpty()
            : eligibleOperators(listModel.initialLists()).isEmpty())) {
      throw new IllegalArgumentException(
          "The geneticAlgorithm mutationOperatorConfigList ("
              + resolvedConfig.getMutationOperatorConfigList()
              + ") has no enabled mutation eligible for the movable entity/variable groups. "
              + "Check the configured mutation probabilities, group sizes, and pinned or singleton-range assignments.");
    }
  }

  public boolean hasMovableSlots() {
    return movableSlots;
  }

  public GeneticAlgorithmGenome sampleSeed(GeneticAlgorithmGenome initial, RandomGenerator random) {
    checkSize(initial);
    var values = initial.toArray();
    for (int i = 0; i < slots.size(); i++) {
      var slot = slots.get(i);
      if (slot.movable()) {
        values[i] = slot.valueRange().get(random.nextLong(slot.valueRange().getSize()));
      }
    }
    return listOperators == null
        ? new GeneticAlgorithmGenome(values)
        : new GeneticAlgorithmGenome(values, listOperators.sampleSeed(random));
  }

  /** Selects a uniformly sampled member of a randomly sized best prefix or worst suffix. */
  public static int selectRankedIndex(
      int size, double pBestRate, boolean best, RandomGenerator random) {
    if (size <= 0
        || !Double.isFinite(pBestRate)
        || pBestRate <= MINIMUM_RANK_FRACTION
        || pBestRate > 1.0) {
      throw new IllegalArgumentException(
          "The ranked selection size ("
              + size
              + ") must be positive and pBestRate ("
              + pBestRate
              + ") must be finite and in (0.000001, 1].");
    }
    double fraction = random.nextDouble(MINIMUM_RANK_FRACTION, pBestRate);
    int prefixSize = Math.min(size, (int) Math.ceil(fraction * size));
    return best ? random.nextInt(prefixSize) : random.nextInt(size - prefixSize, size);
  }

  /**
   * The source uses one shared weight for every assignment, rounded to zero or one. A zero weight
   * exchanges all movable assignments; a weight of one retains them, including exactly 0.5.
   */
  public Pair cross(
      GeneticAlgorithmGenome first, GeneticAlgorithmGenome second, RandomGenerator random) {
    checkSize(first);
    checkSize(second);
    if (random.nextDouble() > crossoverProbability) {
      return new Pair(first, second, false);
    }
    if (random.nextDouble() >= 0.5) {
      return new Pair(first, second, true);
    }
    var firstValues = first.toArray();
    var secondValues = second.toArray();
    for (int i = 0; i < slots.size(); i++) {
      if (slots.get(i).movable()) {
        firstValues[i] = second.value(i);
        secondValues[i] = first.value(i);
      }
    }
    return listOperators == null
        ? new Pair(
            new GeneticAlgorithmGenome(firstValues), new GeneticAlgorithmGenome(secondValues), true)
        : new Pair(
            new GeneticAlgorithmGenome(firstValues, second.lists()),
            new GeneticAlgorithmGenome(secondValues, first.lists()),
            true);
  }

  public Mutation mutate(GeneticAlgorithmGenome parent, RandomGenerator random) {
    checkSize(parent);
    EligibleOperator operator;
    Group group;
    if (listOperators == null) {
      if (operators.isEmpty()) {
        throw new IllegalStateException(
            "The geneticAlgorithm has no movable assignments to mutate.");
      }
      operator = selectOperator(random.nextDouble());
      group = operator.groups().get(random.nextInt(operator.groups().size()));
    } else {
      var lists = parent.lists();
      var eligible = eligibleOperators(lists);
      if (eligible.isEmpty()) {
        // Crossover may still have changed the candidate; normal evaluation determines its outcome.
        return new Mutation(parent, null, null);
      }
      double weight = eligible.stream().mapToDouble(EligibleOperator::weight).sum();
      operator = selectOperator(eligible, weight, random.nextDouble());
      boolean listEligible = listOperators.isEligible(operator.type(), lists);
      int selectedGroup = random.nextInt(operator.groups().size() + (listEligible ? 1 : 0));
      if (selectedGroup == operator.groups().size()) {
        return new Mutation(
            new GeneticAlgorithmGenome(
                parent.toArray(), listOperators.mutate(operator.type(), lists, random)),
            operator.type(),
            listOperators.name());
      }
      group = operator.groups().get(selectedGroup);
    }
    var values = parent.toArray();
    int count = group.slots.length;
    switch (operator.type()) {
      case CHANGE, SWAP -> {
        var positions =
            selectPositions(
                group, changeCount(group, count, minimum(operator.type()), random), count, random);
        for (int i = 0; i < positions.length; i++) {
          int id = group.slots[positions[i]];
          if (operator.type() == GeneticAlgorithmMutationType.CHANGE) {
            var range = slots.get(id).valueRange();
            values[id] = range.get(random.nextLong(range.getSize()));
          } else {
            values[id] = parent.value(group.slots[positions[(i + 1) % positions.length]]);
          }
        }
      }
      case SWAP_EDGES -> {
        var starts =
            selectPositions(group, changeCount(group, count - 1, 2, random), count - 1, random);
        // Rotate the edges left, then sequentially exchange adjacent edges. Overlapping edges
        // deliberately read the current scratch values rather than the original parent.
        for (int i = 0; i < starts.length - 1; i++) {
          int first = starts[(i + 1) % starts.length];
          int second = starts[(i + 2) % starts.length];
          swap(values, group.slots[first], group.slots[second]);
          swap(values, group.slots[first + 1], group.slots[second + 1]);
        }
      }
      case SCRAMBLE -> {
        int length = random.nextInt(3, Math.min(count, 6) + 1);
        int start = selectPositions(group, 1, count - length + 1, random)[0];
        for (int i = length - 1; i > 0; i--) {
          swap(values, group.slots[start + i], group.slots[start + random.nextInt(i + 1)]);
        }
      }
      case INSERTION, INVERSE -> {
        var positions = selectPositions(group, 2, count, random);
        int first = positions[0];
        int second = positions[1];
        int left = Math.min(first, second);
        int right = Math.max(first, second);
        for (int i = left; i <= right; i++) {
          int source;
          if (operator.type() == GeneticAlgorithmMutationType.INVERSE) {
            source = right - (i - left);
          } else if (first < second) {
            source = i == right ? left : i + 1;
          } else {
            source = i == left ? right : i - 1;
          }
          values[group.slots[i]] = parent.value(group.slots[source]);
        }
      }
    }
    return new Mutation(
        listOperators == null
            ? new GeneticAlgorithmGenome(values)
            : new GeneticAlgorithmGenome(values, parent.lists()),
        operator.type(),
        group.name);
  }

  private List<EligibleOperator> eligibleOperators(int[][] lists) {
    return operators.stream()
        .filter(
            operator ->
                !operator.groups().isEmpty() || listOperators.isEligible(operator.type(), lists))
        .toList();
  }

  private EligibleOperator selectOperator(double draw) {
    return selectOperator(operators, totalWeight, draw);
  }

  private static EligibleOperator selectOperator(
      List<EligibleOperator> operators, double totalWeight, double draw) {
    double remaining = draw * totalWeight;
    for (var operator : operators) {
      if (remaining < operator.weight()) {
        return operator;
      }
      remaining -= operator.weight();
    }
    return operators.getLast(); // Floating-point accumulation at the upper boundary.
  }

  private static int minimum(GeneticAlgorithmMutationType type) {
    return switch (type) {
      case CHANGE -> 1;
      case SWAP, INSERTION, INVERSE -> 2;
      case SWAP_EDGES, SCRAMBLE -> 3;
    };
  }

  private static int changeCount(Group group, int capacity, int minimum, RandomGenerator random) {
    int selected = 0;
    for (int i = 0; i < capacity; i++) {
      if (random.nextDouble() < group.mutationRate) {
        selected++;
      }
    }
    return Math.min(capacity, Math.max(selected, minimum));
  }

  private static int[] selectPositions(
      Group group, int count, int rightEnd, RandomGenerator random) {
    var available = new ArrayList<Integer>(rightEnd);
    for (int i = 0; i < rightEnd; i++) {
      if (!group.members.contains(i)) {
        available.add(i);
      }
    }
    while (available.size() < count) {
      int oldest = group.recent.removeLast();
      group.members.remove(oldest);
      if (oldest < rightEnd) {
        available.add(oldest);
      }
    }
    var selected = new int[count];
    for (int i = 0; i < count; i++) {
      int other = random.nextInt(i, available.size());
      selected[i] = available.get(other);
      available.set(other, available.get(i));
      available.set(i, selected[i]);
    }
    if (group.tenure > 0) {
      for (int position : selected) {
        group.members.add(position);
        group.recent.addFirst(position);
      }
      while (group.recent.size() > group.tenure) {
        group.members.remove(group.recent.removeLast());
      }
    }
    return selected;
  }

  private static void swap(Object[] values, int first, int second) {
    var temporary = values[first];
    values[first] = values[second];
    values[second] = temporary;
  }

  private void checkSize(GeneticAlgorithmGenome genome) {
    if (genome.size() != slots.size()) {
      throw new IllegalArgumentException(
          "The geneticAlgorithm genome size ("
              + genome.size()
              + ") must equal the assignment slot count ("
              + slots.size()
              + ").");
    }
    int ownerCount = listOperators == null ? 0 : listOperators.ownerCount();
    if (genome.listCount() != ownerCount) {
      throw new IllegalArgumentException(
          "The geneticAlgorithm genome list count ("
              + genome.listCount()
              + ") must equal the owner count ("
              + ownerCount
              + ").");
    }
  }
}
