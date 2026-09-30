package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Set;

import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListAssignMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListUnassignMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.Move;

/** Per-placement ranking scratch. Distances are never compared across distance meter profiles. */
final class ConstructionHeuristicNearbyRanking {

  private ConstructionHeuristicNearbyRanking() {}

  record MeterProfile(
      ConstructionHeuristicNearbyProfile profile,
      NearbyDistanceMeter<Object, Object> meter,
      NearbyDistanceMeterContract contract) {

    MeterProfile(
        ConstructionHeuristicNearbyProfile profile, NearbyDistanceMeter<Object, Object> meter) {
      this(profile, meter, NearbyDistanceMeterContract.of(profile.distanceMeterClass()));
    }

    @SuppressWarnings("unchecked")
    static MeterProfile of(ConstructionHeuristicNearbyProfile profile) {
      return new MeterProfile(
          profile,
          (NearbyDistanceMeter<Object, Object>)
              ConfigUtils.newInstance(
                  () -> profile.provenance(),
                  "nearbyDistanceMeterClass",
                  profile.distanceMeterClass()));
    }
  }

  record LeafRanking<Solution_>(
      GenuineVariableDescriptor<Solution_> descriptor,
      List<MeterProfile> profiles,
      boolean queuedValue,
      boolean preserveCandidateOrder) {}

  static <Solution_>
      ConstructionHeuristicNearbyMoveSelector.OriginCandidates<Solution_> rankAdmittedComposite(
          List<Move<Solution_>> admittedMoves,
          List<LeafRanking<Solution_>> leaves,
          ScoreDirector<Solution_> scoreDirector) {
    admittedMoves =
        admittedMoves.stream().filter(move -> isAllowedByListRange(move, scoreDirector)).toList();
    var childPopulations = new ArrayList<List<Move<Solution_>>>(leaves.size());
    for (var ignored : leaves) {
      childPopulations.add(new ArrayList<>());
    }
    var flattenedCandidates = new ArrayList<List<Move<Solution_>>>(admittedMoves.size());
    for (var move : admittedMoves) {
      var flattened = new ArrayList<Move<Solution_>>();
      flatten(move, flattened);
      flattenedCandidates.add(flattened);
      for (var child : flattened) {
        var leafIndex = leafIndex(child, leaves);
        if (leafIndex >= 0) {
          childPopulations.get(leafIndex).add(child);
        }
      }
    }
    var rankMaps = new ArrayList<Map<CandidateIdentity, Integer>>(leaves.size());
    for (var i = 0; i < leaves.size(); i++) {
      var leaf = leaves.get(i);
      var origin =
          rank(
              childPopulations.get(i),
              leaf.descriptor(),
              leaf.profiles(),
              leaf.queuedValue(),
              scoreDirector);
      var ranks = new LinkedHashMap<CandidateIdentity, Integer>();
      var iterator = origin.rankedMoves();
      var nextRank = 0;
      while (iterator.hasNext()) {
        ranks.putIfAbsent(identity(iterator.next()), nextRank++);
      }
      var tails = origin.tailMoves();
      while (tails.hasNext()) {
        // Optional choices participate in tuples immediately after the first geographic choice.
        ranks.putIfAbsent(identity(tails.next()), Math.min(1, nextRank));
      }
      rankMaps.add(ranks);
    }
    var heap =
        new PriorityQueue<>(
            Comparator.comparingLong(RankedComposite<Solution_>::rankSum)
                .thenComparingInt(RankedComposite::sourceOrder));
    var tail = new ArrayList<Move<Solution_>>();
    var unique = new HashSet<List<CandidateIdentity>>();
    for (var i = 0; i < admittedMoves.size(); i++) {
      var move = admittedMoves.get(i);
      var children = flattenedCandidates.get(i);
      var identities = children.stream().map(ConstructionHeuristicNearbyRanking::identity).toList();
      if (!unique.add(identities)) {
        continue;
      }
      if (!isMeaningfulAssignment(move)) {
        tail.add(
            move instanceof SelectorBasedCompositeMove<?>
                ? SelectorBasedNoChangeMove.getInstance()
                : move);
        continue;
      }
      var rankSum = 0L;
      for (var child : children) {
        var leafIndex = leafIndex(child, leaves);
        if (leafIndex >= 0) {
          rankSum += rankMaps.get(leafIndex).getOrDefault(identity(child), 0);
        }
      }
      heap.add(new RankedComposite<>(move, rankSum, i));
    }
    Iterator<Move<Solution_>> ranked =
        new Iterator<>() {
          @Override
          public boolean hasNext() {
            return !heap.isEmpty();
          }

          @Override
          public Move<Solution_> next() {
            if (heap.isEmpty()) {
              throw new NoSuchElementException();
            }
            return heap.remove().move();
          }
        };
    var sourceOrders = new LinkedHashMap<List<CandidateIdentity>, Integer>();
    for (var i = 0; i < flattenedCandidates.size(); i++) {
      sourceOrders.putIfAbsent(
          flattenedCandidates.get(i).stream()
              .map(ConstructionHeuristicNearbyRanking::identity)
              .toList(),
          i);
    }
    Comparator<Move<Solution_>> sourceOrder =
        Comparator.comparingInt(
            move -> {
              var flattened = new ArrayList<Move<Solution_>>();
              flatten(move, flattened);
              return sourceOrders.getOrDefault(
                  flattened.stream().map(ConstructionHeuristicNearbyRanking::identity).toList(),
                  Integer.MAX_VALUE);
            });
    return new ConstructionHeuristicNearbyMoveSelector.OriginCandidates<>(
        ranked,
        tail.iterator(),
        true,
        sourceOrder,
        leaves.stream().anyMatch(LeafRanking::preserveCandidateOrder));
  }

  private static <Solution_> void flatten(Move<Solution_> move, List<Move<Solution_>> children) {
    if (move instanceof SelectorBasedCompositeMove<Solution_> composite) {
      for (var child : composite.getMoves()) {
        flatten(child, children);
      }
    } else {
      children.add(move);
    }
  }

  private static int leafIndex(Move<?> move, List<? extends LeafRanking<?>> leaves) {
    var moveDescriptor = variableDescriptor(move);
    for (var i = 0; i < leaves.size(); i++) {
      if (leaves.get(i).descriptor() == moveDescriptor) {
        return i;
      }
    }
    return -1;
  }

  private record RankedComposite<Solution_>(Move<Solution_> move, long rankSum, int sourceOrder) {}

  static <Solution_> ConstructionHeuristicNearbyMoveSelector.OriginCandidates<Solution_> rank(
      List<Move<Solution_>> allowedMoves,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      List<MeterProfile> profiles,
      boolean queuedValue,
      ScoreDirector<Solution_> scoreDirector) {
    return rank(allowedMoves, variableDescriptor, profiles, queuedValue, scoreDirector, false);
  }

  static <Solution_> ConstructionHeuristicNearbyMoveSelector.OriginCandidates<Solution_> rank(
      List<Move<Solution_>> allowedMoves,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      List<MeterProfile> profiles,
      boolean queuedValue,
      ScoreDirector<Solution_> scoreDirector,
      boolean preserveCandidateOrder) {
    var uniqueMoves = new LinkedHashMap<CandidateIdentity, Move<Solution_>>();
    for (var move : allowedMoves) {
      if (isAllowedByListRange(move, scoreDirector)) {
        uniqueMoves.putIfAbsent(identity(move), move);
      }
    }
    var candidates = new ArrayList<Move<Solution_>>();
    var tail = new ArrayList<Move<Solution_>>();
    for (var move : uniqueMoves.values()) {
      (isMeaningfulAssignment(move) ? candidates : tail).add(move);
    }
    var covered = Collections.newSetFromMap(new IdentityHashMap<Move<Solution_>, Boolean>());
    var candidateOrders = new IdentityHashMap<Move<Solution_>, Integer>();
    for (var i = 0; i < candidates.size(); i++) {
      candidateOrders.put(candidates.get(i), i);
    }
    var streams = new ArrayList<Iterator<Move<Solution_>>>();
    for (var profile : profiles) {
      if (queuedValue
          && !variableDescriptor.isListVariable()
          && profile.profile().argumentShape()
              == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY) {
        streams.add(
            rankQueuedValueEntityNeighbors(
                candidates, variableDescriptor, profile, scoreDirector, covered));
        continue;
      }
      var groups =
          groupsForProfile(candidates, variableDescriptor, profile, queuedValue, scoreDirector);
      if (!groups.isEmpty()) {
        var anchors = new ArrayList<RankedAnchor<Solution_>>(groups.size());
        for (var group : groups.values()) {
          if (!profile.contract().accepts(group.origin, group.destination)) {
            continue;
          }
          var distance =
              nearbyDistance(profile, variableDescriptor, group.origin, group.destination);
          var sourceOrder = group.moves.stream().mapToInt(candidateOrders::get).min().orElseThrow();
          anchors.add(new RankedAnchor<>(distance, sourceOrder, group.moves));
          covered.addAll(group.moves);
        }
        Iterator<Move<Solution_>> stream = new AnchorIterator<>(new PriorityQueue<>(anchors));
        // Each profile ranks unique assignments. Repeated entity anchors must not consume
        // additional round-robin turns before another profile's next candidate is considered.
        if (profile.profile().argumentShape()
            == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY) {
          stream = new UniqueCandidateIterator<>(stream);
        }
        streams.add(stream);
      }
    }
    // Candidates without an assigned anchor are still legal during partial initialization.
    // A single deterministic seed stream keeps these reachable without invoking a meter with
    // an argument outside its declared contract.
    var seeds = candidates.stream().filter(move -> !covered.contains(move)).iterator();
    if (seeds.hasNext()) {
      streams.add(seeds);
    }
    var sourceOrders = new LinkedHashMap<CandidateIdentity, Integer>();
    var nextSourceOrder = 0;
    for (var entry : uniqueMoves.entrySet()) {
      sourceOrders.put(entry.getKey(), nextSourceOrder++);
    }
    return new ConstructionHeuristicNearbyMoveSelector.OriginCandidates<>(
        new RoundRobinIterator<>(streams),
        tail.iterator(),
        true,
        Comparator.comparingInt(
            move -> sourceOrders.getOrDefault(identity(move), Integer.MAX_VALUE)),
        preserveCandidateOrder,
        null,
        move -> sourceOrders.containsKey(identity(move)),
        move -> List.of(sourceOrders.getOrDefault(identity(move), Integer.MAX_VALUE)),
        null);
  }

  private static <Solution_> Iterator<Move<Solution_>> rankQueuedValueEntityNeighbors(
      List<Move<Solution_>> candidates,
      GenuineVariableDescriptor<Solution_> descriptor,
      MeterProfile profile,
      ScoreDirector<Solution_> scoreDirector,
      Set<Move<Solution_>> covered) {
    var minima = new ArrayList<CandidateMinimum<Solution_>>(candidates.size());
    var valueCandidates = new IdentityHashMap<Object, List<CandidateMinimum<Solution_>>>();
    for (var i = 0; i < candidates.size(); i++) {
      var move = (ChangeMove<Solution_>) candidates.get(i);
      var minimum = new CandidateMinimum<Solution_>(candidates.get(i), i);
      minima.add(minimum);
      valueCandidates
          .computeIfAbsent(move.getToPlanningValue(), ignored -> new ArrayList<>())
          .add(minimum);
    }
    var seenNeighbors = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    for (var neighbor :
        descriptor.getEntityDescriptor().extractEntities(scoreDirector.getWorkingSolution())) {
      var value = descriptor.getValue(neighbor);
      var matching = value == null ? null : valueCandidates.get(value);
      if (matching == null || !seenNeighbors.add(neighbor)) {
        continue;
      }
      for (var minimum : matching) {
        var entity = ((ChangeMove<Solution_>) minimum.move).getEntity();
        if (!profile.contract().accepts(entity, neighbor)) {
          continue;
        }
        var distance = nearbyDistance(profile, descriptor, entity, neighbor);
        if (!minimum.hasDistance || Double.compare(distance, minimum.distance) < 0) {
          minimum.distance = distance;
          minimum.hasDistance = true;
        }
      }
    }
    // Every scalar entity/neighbor group contains one distinct assignment. Its first occurrence
    // is ordered by minimum distance, then candidate source ordinal. Retain that occurrence only,
    // instead of a quadratic number of groups when many entities share the queued value.
    var heap = new PriorityQueue<RankedAnchor<Solution_>>();
    for (var minimum : minima) {
      if (minimum.hasDistance) {
        covered.add(minimum.move);
        heap.add(new RankedAnchor<>(minimum.distance, minimum.sourceOrder, List.of(minimum.move)));
      }
    }
    return new AnchorIterator<>(heap);
  }

  private static double nearbyDistance(
      MeterProfile profile,
      GenuineVariableDescriptor<?> descriptor,
      Object origin,
      Object destination) {
    // An asymmetric meter always sees exactly the declared origin and destination types.
    double distance;
    try {
      distance = profile.meter().getNearbyDistance(origin, destination);
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "The nearbyDistanceMeterClass (%s) from (%s) failed for construction variable (%s), origin (%s; type %s), and destination (%s; type %s)."
              .formatted(
                  profile.profile().distanceMeterClass().getName(),
                  profile.profile().provenance(),
                  descriptor.getSimpleEntityAndVariableName(),
                  origin,
                  typeName(origin),
                  destination,
                  typeName(destination)),
          e);
    }
    if (Double.isNaN(distance)) {
      throw new IllegalArgumentException(
          "The nearbyDistanceMeterClass (%s) from (%s) returned NaN for origin (%s) and destination (%s)."
              .formatted(
                  profile.profile().distanceMeterClass().getName(),
                  profile.profile().provenance(),
                  origin,
                  destination));
    }
    return distance;
  }

  private static final class CandidateMinimum<Solution_> {
    private final Move<Solution_> move;
    private final int sourceOrder;
    private boolean hasDistance;
    private double distance;

    private CandidateMinimum(Move<Solution_> move, int sourceOrder) {
      this.move = move;
      this.sourceOrder = sourceOrder;
    }
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }

  /** Selects a nearby prefix first, then restores the configured order inside that neighborhood. */
  static <Solution_> Iterator<Move<Solution_>> neighborhoodOrdered(
      Iterator<Move<Solution_>> rankedMoves,
      int initialSize,
      Comparator<Move<Solution_>> sourceOrder) {
    return neighborhoodOrdered(rankedMoves, initialSize, sourceOrder, null);
  }

  static <Solution_> Iterator<Move<Solution_>> neighborhoodOrdered(
      Iterator<Move<Solution_>> rankedMoves,
      int initialSize,
      Comparator<Move<Solution_>> sourceOrder,
      java.util.function.Consumer<List<Move<Solution_>>> sorter) {
    return neighborhoodOrdered(rankedMoves, initialSize, sourceOrder, sorter, null);
  }

  static <Solution_> Iterator<Move<Solution_>> neighborhoodOrdered(
      Iterator<Move<Solution_>> rankedMoves,
      int initialSize,
      Comparator<Move<Solution_>> sourceOrder,
      java.util.function.Consumer<List<Move<Solution_>>> sorter,
      java.util.function.Function<Move<Solution_>, List<Integer>> sourceRanks) {
    return new Iterator<>() {
      private final Map<Move<Solution_>, List<Integer>> selectedRanks = new IdentityHashMap<>();
      private final PriorityQueue<Move<Solution_>> neighborhood =
          sourceRanks != null
              ? new PriorityQueue<>(
                  (left, right) ->
                      compareRankVectors(selectedRanks.get(left), selectedRanks.get(right)))
              : sourceOrder == null ? null : new PriorityQueue<>(sourceOrder);
      private Iterator<Move<Solution_>> current = Collections.emptyIterator();
      private long nextNeighborhoodSize = initialSize;
      private boolean firstNeighborhood = true;

      @Override
      public boolean hasNext() {
        if (!current.hasNext()) {
          var selected = new ArrayList<Move<Solution_>>();
          for (long i = 0; i < nextNeighborhoodSize && rankedMoves.hasNext(); i++) {
            var move = rankedMoves.next();
            if (sourceRanks != null) {
              selectedRanks.put(move, List.copyOf(sourceRanks.apply(move)));
            }
            if (neighborhood == null) {
              selected.add(move);
            } else {
              neighborhood.add(move);
            }
          }
          if (neighborhood != null) {
            while (!neighborhood.isEmpty()) {
              selected.add(neighborhood.remove());
            }
          }
          if (sorter != null) {
            sorter.accept(selected);
          }
          selectedRanks.clear();
          current = selected.iterator();
          if (firstNeighborhood) {
            firstNeighborhood = false;
          } else {
            nextNeighborhoodSize =
                nextNeighborhoodSize > Long.MAX_VALUE / 2
                    ? Long.MAX_VALUE
                    : nextNeighborhoodSize * 2;
          }
        }
        return current.hasNext();
      }

      @Override
      public Move<Solution_> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return current.next();
      }
    };
  }

  private static int compareRankVectors(List<Integer> left, List<Integer> right) {
    for (var i = 0; i < Math.min(left.size(), right.size()); i++) {
      var result = Integer.compare(left.get(i), right.get(i));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

  private static <Solution_> Map<AnchorIdentity, AnchorGroup<Solution_>> groupsForProfile(
      List<Move<Solution_>> candidates,
      GenuineVariableDescriptor<Solution_> descriptor,
      MeterProfile meterProfile,
      boolean queuedValue,
      ScoreDirector<Solution_> scoreDirector) {
    var groups = new LinkedHashMap<AnchorIdentity, AnchorGroup<Solution_>>();
    var shape = meterProfile.profile().argumentShape();
    if (!descriptor.isListVariable()) {
      if (shape == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_VALUE) {
        for (var candidate : candidates) {
          var move = (ChangeMove<Solution_>) candidate;
          add(groups, move.getEntity(), move.getToPlanningValue(), move);
        }
      } else if (shape == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY) {
        // Map neighboring assigned entities back to the CH-admitted value population. The local
        // search entity selector and its restrictions are deliberately not reused here.
        var valueMoves = new IdentityHashMap<Object, List<Move<Solution_>>>();
        for (var candidate : candidates) {
          var move = (ChangeMove<Solution_>) candidate;
          valueMoves
              .computeIfAbsent(move.getToPlanningValue(), ignored -> new ArrayList<>())
              .add(move);
        }
        for (var neighbor :
            descriptor.getEntityDescriptor().extractEntities(scoreDirector.getWorkingSolution())) {
          var value = descriptor.getValue(neighbor);
          var moves = value == null ? null : valueMoves.get(value);
          if (moves != null) {
            for (var candidate : moves) {
              var move = (ChangeMove<Solution_>) candidate;
              add(groups, move.getEntity(), neighbor, candidate);
            }
          }
        }
      }
      return groups;
    }
    var listDescriptor = (ListVariableDescriptor<Solution_>) descriptor;
    for (var candidate : candidates) {
      var value = movedValue(candidate);
      var entity = destinationEntity(candidate);
      var index = destinationIndex(candidate);
      Object anchor;
      if (shape == ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_DESTINATION) {
        anchor =
            index == listDescriptor.getFirstUnpinnedIndex(entity)
                ? entity
                : listDescriptor.getElement(entity, index - 1);
      } else if (shape == ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_VALUE) {
        var listSize = listDescriptor.getListSize(entity);
        if (listSize == 0) {
          continue; // An empty list has no value anchor; it enters the deterministic seed stream.
        }
        // The first insertion slot is represented by its adjacent assigned value. With pinning,
        // that may be the first movable value or the final pinned value at the end of the list.
        var adjacentIndex = index > 0 ? index - 1 : 0;
        anchor = listDescriptor.getElement(entity, adjacentIndex);
      } else {
        continue;
      }
      if (anchor != null) {
        add(groups, value, anchor, candidate);
      }
    }
    return groups;
  }

  private static <Solution_> void add(
      Map<AnchorIdentity, AnchorGroup<Solution_>> groups,
      Object origin,
      Object destination,
      Move<Solution_> candidate) {
    var key = new AnchorIdentity(origin, destination);
    groups
        .computeIfAbsent(key, ignored -> new AnchorGroup<>(origin, destination))
        .moves
        .add(candidate);
  }

  static boolean isMeaningfulAssignment(Move<?> move) {
    if (move instanceof ChangeMove<?> changeMove) {
      return changeMove.getToPlanningValue() != null;
    } else if (move instanceof ListAssignMove<?> || move instanceof ListChangeMove<?>) {
      return true;
    } else if (move instanceof SelectorBasedCompositeMove<?> compositeMove) {
      for (var child : compositeMove.getMoves()) {
        if (isMeaningfulAssignment(child)) {
          return true;
        }
      }
      return false;
    }
    return !(move instanceof SelectorBasedNoChangeMove<?> || move instanceof ListUnassignMove<?>);
  }

  private static <Solution_> boolean isAllowedByListRange(
      Move<Solution_> move, ScoreDirector<Solution_> scoreDirector) {
    if (move instanceof SelectorBasedCompositeMove<Solution_> composite) {
      for (var child : composite.getMoves()) {
        if (!isAllowedByListRange(child, scoreDirector)) {
          return false;
        }
      }
      return true;
    }
    if (move instanceof ListAssignMove<?> || move instanceof ListChangeMove<?>) {
      @SuppressWarnings("unchecked")
      var descriptor = (ListVariableDescriptor<Solution_>) variableDescriptor(move);
      if (!descriptor.canExtractValueRangeFromSolution()) {
        var valueRangeManager =
            ((VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector)
                .getValueRangeManager();
        return valueRangeManager
            .getFromEntity(descriptor.getValueRangeDescriptor(), destinationEntity(move))
            .contains(movedValue(move));
      }
    }
    return true;
  }

  static Object movedValue(Move<?> move) {
    if (move instanceof ListAssignMove<?> assignMove) {
      return assignMove.getMovedValue();
    } else if (move instanceof ListChangeMove<?> changeMove) {
      return changeMove.getMovedValue();
    } else if (move instanceof ListUnassignMove<?> unassignMove) {
      return unassignMove.getMovedValue();
    }
    return null;
  }

  static Object destinationEntity(Move<?> move) {
    if (move instanceof ListAssignMove<?> assignMove) {
      return assignMove.getDestinationEntity();
    }
    return ((ListChangeMove<?>) move).getDestinationEntity();
  }

  static int destinationIndex(Move<?> move) {
    if (move instanceof ListAssignMove<?> assignMove) {
      return assignMove.getDestinationIndex();
    }
    return ((ListChangeMove<?>) move).getDestinationIndex();
  }

  static CandidateIdentity identity(Move<?> move) {
    if (move instanceof SelectorBasedCompositeMove<?>) {
      var children = new ArrayList<CandidateIdentity>();
      collectLeafIdentities(move, children);
      return new CandidateIdentity(children);
    } else if (move instanceof ChangeMove<?> changeMove) {
      return new CandidateIdentity(
          changeMove.getVariableDescriptor(),
          changeMove.getEntity(),
          changeMove.getToPlanningValue(),
          -1);
    } else if (move instanceof ListAssignMove<?> || move instanceof ListChangeMove<?>) {
      return new CandidateIdentity(
          variableDescriptor(move),
          movedValue(move),
          destinationEntity(move),
          destinationIndex(move));
    } else if (move instanceof ListUnassignMove<?> unassignMove) {
      return new CandidateIdentity(
          unassignMove.getVariableDescriptor(), unassignMove.getMovedValue(), null, -1);
    }
    return new CandidateIdentity(null, move, null, -1);
  }

  private static void collectLeafIdentities(Move<?> move, List<CandidateIdentity> children) {
    if (move instanceof SelectorBasedCompositeMove<?> composite) {
      for (var child : composite.getMoves()) {
        collectLeafIdentities(child, children);
      }
    } else {
      children.add(identity(move));
    }
  }

  static GenuineVariableDescriptor<?> variableDescriptor(Move<?> move) {
    if (move instanceof ChangeMove<?> change) {
      return change.getVariableDescriptor();
    } else if (move instanceof ListAssignMove<?> assign) {
      return assign.getVariableDescriptor();
    } else if (move instanceof ListChangeMove<?> change) {
      return change.getVariableDescriptor();
    } else if (move instanceof ListUnassignMove<?> unassign) {
      return unassign.getVariableDescriptor();
    }
    return null;
  }

  static final class CandidateIdentity {
    private final Object variableDescriptor;
    private final Object first;
    private final Object second;
    private final int index;
    private final List<CandidateIdentity> children;

    CandidateIdentity(Object variableDescriptor, Object first, Object second, int index) {
      this.variableDescriptor = variableDescriptor;
      this.first = first;
      this.second = second;
      this.index = index;
      children = null;
    }

    private CandidateIdentity(List<CandidateIdentity> children) {
      variableDescriptor = null;
      first = null;
      second = null;
      index = -1;
      this.children = List.copyOf(children);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CandidateIdentity identity)) {
        return false;
      }
      if (children != null || identity.children != null) {
        return children != null && children.equals(identity.children);
      }
      return variableDescriptor == identity.variableDescriptor
          && first == identity.first
          && second == identity.second
          && index == identity.index;
    }

    @Override
    public int hashCode() {
      return children != null
          ? children.hashCode()
          : ((31 * System.identityHashCode(variableDescriptor) + System.identityHashCode(first))
                          * 31
                      + System.identityHashCode(second))
                  * 31
              + index;
    }
  }

  private static final class AnchorIdentity {
    private final Object origin;
    private final Object destination;

    private AnchorIdentity(Object origin, Object destination) {
      this.origin = origin;
      this.destination = destination;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof AnchorIdentity identity
          && origin == identity.origin
          && destination == identity.destination;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(origin) + System.identityHashCode(destination);
    }
  }

  private static final class AnchorGroup<Solution_> {
    private final Object origin;
    private final Object destination;
    private final List<Move<Solution_>> moves = new ArrayList<>();

    private AnchorGroup(Object origin, Object destination) {
      this.origin = origin;
      this.destination = destination;
    }
  }

  private record RankedAnchor<Solution_>(
      double distance, int sourceOrder, List<Move<Solution_>> moves)
      implements Comparable<RankedAnchor<Solution_>> {
    @Override
    public int compareTo(RankedAnchor<Solution_> other) {
      var result = Double.compare(distance, other.distance);
      return result != 0 ? result : Integer.compare(sourceOrder, other.sourceOrder);
    }
  }

  private static final class AnchorIterator<Solution_> implements Iterator<Move<Solution_>> {
    private final PriorityQueue<RankedAnchor<Solution_>> heap;
    private Iterator<Move<Solution_>> current = Collections.emptyIterator();

    private AnchorIterator(PriorityQueue<RankedAnchor<Solution_>> heap) {
      this.heap = heap;
    }

    @Override
    public boolean hasNext() {
      return current.hasNext() || !heap.isEmpty();
    }

    @Override
    public Move<Solution_> next() {
      if (!current.hasNext()) {
        if (heap.isEmpty()) {
          throw new NoSuchElementException();
        }
        current = heap.remove().moves().iterator();
      }
      return current.next();
    }
  }

  private static final class RoundRobinIterator<Solution_> implements Iterator<Move<Solution_>> {
    private final List<Iterator<Move<Solution_>>> streams;
    private final Set<CandidateIdentity> emitted = new HashSet<>();
    private int nextStream;
    private Move<Solution_> upcoming;

    private RoundRobinIterator(List<Iterator<Move<Solution_>>> streams) {
      this.streams = streams;
    }

    @Override
    public boolean hasNext() {
      if (upcoming != null) {
        return true;
      }
      while (!streams.isEmpty()) {
        var stream = streams.get(nextStream);
        if (!stream.hasNext()) {
          streams.remove(nextStream);
          if (nextStream == streams.size()) {
            nextStream = 0;
          }
          continue;
        }
        var move = stream.next();
        nextStream = (nextStream + 1) % streams.size();
        if (emitted.add(identity(move))) {
          upcoming = move;
          return true;
        }
      }
      return false;
    }

    @Override
    public Move<Solution_> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      var result = upcoming;
      upcoming = null;
      return result;
    }
  }

  private static final class UniqueCandidateIterator<Solution_>
      implements Iterator<Move<Solution_>> {
    private final Iterator<Move<Solution_>> delegate;
    private final Set<CandidateIdentity> emitted = new HashSet<>();
    private Move<Solution_> upcoming;

    private UniqueCandidateIterator(Iterator<Move<Solution_>> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
      while (upcoming == null && delegate.hasNext()) {
        var candidate = delegate.next();
        if (emitted.add(identity(candidate))) {
          upcoming = candidate;
        }
      }
      return upcoming != null;
    }

    @Override
    public Move<Solution_> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      var result = upcoming;
      upcoming = null;
      return result;
    }
  }
}
