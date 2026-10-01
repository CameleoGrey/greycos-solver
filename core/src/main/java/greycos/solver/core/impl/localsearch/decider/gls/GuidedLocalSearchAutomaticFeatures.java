package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Incremental decision features. No business scoring backend is consulted. */
final class GuidedLocalSearchAutomaticFeatures<Solution_> {

  private final InnerScoreDirector<Solution_, ?> director;
  private final GuidedLocalSearchIdentityRegistry<Solution_> identities;
  private final Consumer<Object> add;
  private final Consumer<Object> remove;
  private final Map<Object, List<BasicEntry>> basicEntries = new IdentityHashMap<>();
  private final Map<Object, ElementEntry> elements = new IdentityHashMap<>();
  private final Map<Object, EmptyListEntry> owners = new IdentityHashMap<>();
  private final ArrayList<Entry> dirty = new ArrayList<>();
  private final Map<Object, Entry> featureSources = new HashMap<>();
  private final boolean ownershipEnabled;
  private final ListVariableDescriptor<Solution_> listDescriptor;
  private final ListVariableState<Solution_, Object, Object> listState;

  GuidedLocalSearchAutomaticFeatures(
      InnerScoreDirector<Solution_, ?> director,
      GuidedLocalSearchIdentityRegistry<Solution_> identities,
      Consumer<Object> add,
      Consumer<Object> remove,
      boolean ownershipEnabled) {
    this.ownershipEnabled = ownershipEnabled;
    this.director = director;
    this.identities = identities;
    this.add = add;
    this.remove = remove;
    listDescriptor = director.getSolutionDescriptor().getListVariableDescriptor();
    listState = listDescriptor == null ? null : director.getListVariableState(listDescriptor);
  }

  void reset() {
    basicEntries.clear();
    elements.clear();
    owners.clear();
    dirty.clear();
    featureSources.clear();
    director
        .getSolutionDescriptor()
        .visitAllEntities(
            director.getWorkingSolution(),
            entity -> {
              var descriptor =
                  director.getSolutionDescriptor().findEntityDescriptorOrFail(entity.getClass());
              for (var variable : descriptor.getGenuineVariableDescriptorList()) {
                if (variable instanceof BasicVariableDescriptor<Solution_> basic) {
                  var entry = new BasicEntry(entity, basic);
                  basicEntries.computeIfAbsent(entity, ignored -> new ArrayList<>()).add(entry);
                  mark(entry);
                }
              }
            });
    if (listDescriptor != null) {
      listDescriptor
          .getEntityDescriptor()
          .visitAllEntities(
              director.getWorkingSolution(),
              entity -> {
                var entry = new EmptyListEntry(entity);
                owners.put(entity, entry);
                mark(entry);
                listDescriptor.getValue(entity).forEach(this::markElement);
              });
      var iterator =
          director
              .getValueRangeManager()
              .getFromSolution(listDescriptor.getValueRangeDescriptor())
              .createOriginalIterator();
      while (iterator.hasNext()) {
        markElement(iterator.next());
      }
    }
  }

  void afterVariableChanged(Object entity, String variableName) {
    var entries = basicEntries.get(entity);
    if (entries != null) {
      for (var entry : entries) {
        if (entry.descriptor.getVariableName().equals(variableName)) {
          mark(entry);
        }
      }
    }
  }

  void markElement(Object element) {
    if (listDescriptor != null) {
      mark(elements.computeIfAbsent(element, ElementEntry::new));
    }
  }

  void markOwner(Object owner) {
    var entry = owners.get(owner);
    if (entry != null) {
      mark(entry);
    }
  }

  private void mark(Entry entry) {
    if (!entry.dirty) {
      entry.dirty = true;
      dirty.add(entry);
    }
  }

  void flush() {
    for (var entry : dirty) {
      var updated = entry.features();
      for (var previous : entry.emitted) {
        if (!updated.contains(previous)) {
          featureSources.remove(previous);
          remove.accept(previous);
        }
      }
      for (var current : updated) {
        if (!entry.emitted.contains(current)) {
          featureSources.put(current, entry);
          add.accept(current);
        }
      }
      entry.emitted = updated;
      entry.dirty = false;
    }
    dirty.clear();
  }

  /** Reads genuine assignments and list contents; never uses emitted features or list shadows. */
  void extract(Consumer<Object> consumer) {
    var solution = director.getWorkingSolution();
    director
        .getSolutionDescriptor()
        .visitAllEntities(
            solution,
            entity -> {
              var descriptor =
                  director.getSolutionDescriptor().findEntityDescriptorOrFail(entity.getClass());
              if (descriptor.isMovable(solution, entity)) {
                for (var variable : descriptor.getGenuineVariableDescriptorList()) {
                  if (variable instanceof BasicVariableDescriptor<Solution_> basic) {
                    consumer.accept(basicKey(entity, basic));
                  }
                }
              }
            });
    if (listDescriptor == null) {
      return;
    }
    Set<Object> assigned = Collections.newSetFromMap(new IdentityHashMap<>());
    listDescriptor
        .getEntityDescriptor()
        .visitAllEntities(
            solution,
            owner -> {
              var values = listDescriptor.getValue(owner);
              assigned.addAll(values);
              if (!isMovable(owner)) {
                return;
              }
              if (values.isEmpty()) {
                consumer.accept(emptyKey(owner));
              }
              for (int i = 0; i < values.size(); i++) {
                elementFeatures(
                        values.get(i),
                        owner,
                        i == 0 ? null : values.get(i - 1),
                        i + 1 == values.size() ? null : values.get(i + 1),
                        listDescriptor.isElementPinned(solution, owner, i))
                    .forEach(consumer);
              }
            });
    var iterator =
        director
            .getValueRangeManager()
            .getFromSolution(listDescriptor.getValueRangeDescriptor())
            .createOriginalIterator();
    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    while (iterator.hasNext()) {
      var value = iterator.next();
      if (!assigned.contains(value) && seen.add(value)) {
        elementFeatures(value, null, null, null, false).forEach(consumer);
      }
    }
  }

  private Object basicKey(Object entity, BasicVariableDescriptor<Solution_> variable) {
    return new Feature(
        Kind.ASSIGNMENT,
        variableId(variable),
        identities.token(entity),
        identities.token(variable.getValue(entity)));
  }

  private Object emptyKey(Object owner) {
    var identity = identities.token(owner);
    return new Feature(
        Kind.ARC,
        variableId(listDescriptor),
        new Boundary(identity, false),
        new Boundary(identity, true));
  }

  private List<Object> elementFeatures(
      Object value, Object owner, Object previous, Object next, boolean pinned) {
    var variable = variableId(listDescriptor);
    var valueId = identities.token(value);
    if (owner == null) {
      return ownershipEnabled
          ? List.of(new Feature(Kind.OWNERSHIP, variable, valueId, identities.token(null)))
          : List.of();
    }
    if (!isMovable(owner)) {
      return List.of();
    }
    var ownerId = identities.token(owner);
    var result = new ArrayList<Object>(3);
    if (!pinned) {
      if (ownershipEnabled) result.add(new Feature(Kind.OWNERSHIP, variable, valueId, ownerId));
      result.add(
          new Feature(
              Kind.ARC,
              variable,
              previous == null ? new Boundary(ownerId, false) : identities.token(previous),
              valueId));
    }
    // Even a pinned last element can acquire an outgoing neighbor by appending at the pin boundary.
    if (next == null) {
      result.add(new Feature(Kind.ARC, variable, valueId, new Boundary(ownerId, true)));
    }
    return result;
  }

  /** Only penalized active keys need origins; unpenalized populations do not trigger list scans. */
  GuidedLocalSearchFeatureTracker.OriginPriorities originPriorities(
      GuidedLocalSearchLearning.Snapshot learning,
      GuidedLocalSearchPenaltyTable.Snapshot<Object> penalties,
      Set<Object> activePenalized,
      int level) {
    var entityPriorities = new IdentityHashMap<Object, Map<String, GuidedLocalSearchNumber>>();
    var valuePriorities = new IdentityHashMap<Object, Map<String, GuidedLocalSearchNumber>>();
    var boundaryPriorities = new IdentityHashMap<Object, Map<String, GuidedLocalSearchNumber>>();
    var listBuilders = new IdentityHashMap<Object, ListPriorityBuilder>();
    String listName = listDescriptor == null ? null : listDescriptor.getVariableName();
    for (var key : activePenalized) {
      var source = featureSources.get(key);
      var cost =
          GuidedLocalSearchNumber.of(learning.weight(level, key)).multiply(penalties.count(key));
      var feature = (Feature) key;
      switch (feature.kind) {
        case ASSIGNMENT -> {
          var basic = (BasicEntry) source;
          addPriority(entityPriorities, basic.entity, basic.descriptor.getVariableName(), cost);
        }
        case OWNERSHIP -> {
          var element = (ElementEntry) source;
          addPriority(valuePriorities, element.value, listName, cost);
          var owner = listState.getInverseSingleton(element.value);
          if (owner != null) {
            var builder =
                listBuilders.computeIfAbsent(
                    owner,
                    ignored -> new ListPriorityBuilder(listDescriptor.getValue(owner).size()));
            builder.ownership.put(listState.getIndexOrFail(element.value), cost);
          }
        }
        case ARC -> {
          if (feature.left instanceof Boundary && feature.right instanceof Boundary) {
            var owner = ((EmptyListEntry) source).owner;
            addPriority(boundaryPriorities, owner, listName, cost);
            listBuilders
                .computeIfAbsent(owner, ignored -> new ListPriorityBuilder(0))
                .arcs
                .put(0, cost);
          } else {
            var element = (ElementEntry) source;
            var owner = listState.getInverseSingleton(element.value);
            var builder =
                listBuilders.computeIfAbsent(
                    owner,
                    ignored -> new ListPriorityBuilder(listDescriptor.getValue(owner).size()));
            addPriority(valuePriorities, element.value, listName, cost);
            if (feature.right instanceof Boundary) {
              addPriority(boundaryPriorities, owner, listName, cost);
              builder.arcs.put(builder.size, cost);
            } else {
              var previous = listState.getPreviousElement(element.value);
              if (previous == null) addPriority(boundaryPriorities, owner, listName, cost);
              else addPriority(valuePriorities, previous, listName, cost);
              builder.arcs.put(listState.getIndexOrFail(element.value), cost);
            }
          }
        }
      }
    }
    var listPriorities =
        new IdentityHashMap<
            Object, Map<String, GuidedLocalSearchFeatureTracker.ListContributions>>();
    listBuilders.forEach(
        (owner, builder) ->
            listPriorities.put(
                owner,
                Map.of(
                    listName,
                    new GuidedLocalSearchFeatureTracker.ListContributions(
                        builder.size, builder.arcs, builder.ownership))));
    return new GuidedLocalSearchFeatureTracker.OriginPriorities(
        entityPriorities, valuePriorities, boundaryPriorities, listPriorities);
  }

  private static final class ListPriorityBuilder {
    private final int size;
    private final Map<Integer, GuidedLocalSearchNumber> arcs = new HashMap<>();
    private final Map<Integer, GuidedLocalSearchNumber> ownership = new HashMap<>();

    ListPriorityBuilder(int size) {
      this.size = size;
    }
  }

  private static void addPriority(
      Map<Object, Map<String, GuidedLocalSearchNumber>> priorities,
      Object origin,
      String variableName,
      GuidedLocalSearchNumber contribution) {
    if (contribution.signum() == 0) return;
    priorities
        .computeIfAbsent(origin, ignored -> new java.util.HashMap<>())
        .merge(variableName, contribution, GuidedLocalSearchNumber::add);
  }

  private static VariableIdentity variableId(VariableDescriptor<?> descriptor) {
    return new VariableIdentity(
        descriptor.getEntityDescriptor().getOrdinal(), descriptor.getOrdinal());
  }

  private boolean isMovable(Object entity) {
    return director
        .getSolutionDescriptor()
        .findEntityDescriptorOrFail(entity.getClass())
        .isMovable(director.getWorkingSolution(), entity);
  }

  private abstract class Entry {
    boolean dirty;
    List<Object> emitted = List.of();

    abstract List<Object> features();
  }

  private final class BasicEntry extends Entry {
    private final Object entity;
    private final BasicVariableDescriptor<Solution_> descriptor;

    BasicEntry(Object entity, BasicVariableDescriptor<Solution_> descriptor) {
      this.entity = entity;
      this.descriptor = descriptor;
    }

    @Override
    List<Object> features() {
      return isMovable(entity) ? List.of(basicKey(entity, descriptor)) : List.of();
    }
  }

  private final class ElementEntry extends Entry {
    private final Object value;

    ElementEntry(Object value) {
      this.value = value;
    }

    @Override
    List<Object> features() {
      var owner = listState.getInverseSingleton(value);
      return elementFeatures(
          value,
          owner,
          owner == null ? null : listState.getPreviousElement(value),
          owner == null ? null : listState.getNextElement(value),
          listState.isPinned(value));
    }
  }

  private final class EmptyListEntry extends Entry {
    private final Object owner;

    EmptyListEntry(Object owner) {
      this.owner = owner;
    }

    @Override
    List<Object> features() {
      return listDescriptor.getValue(owner).isEmpty() && isMovable(owner)
          ? List.of(emptyKey(owner))
          : List.of();
    }
  }

  private enum Kind {
    ASSIGNMENT,
    OWNERSHIP,
    ARC
  }

  private record VariableIdentity(int entity, int variable) {}

  private record Boundary(Object owner, boolean end) {}

  private record Feature(Kind kind, VariableIdentity variable, Object left, Object right) {}
}
