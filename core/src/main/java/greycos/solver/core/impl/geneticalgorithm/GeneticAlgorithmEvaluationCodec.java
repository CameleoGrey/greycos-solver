package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * Run-local assignment transport. Only immutable literals and canonical object ordinals cross the
 * evaluation boundary. Basic ranges are never enumerated, including long-indexed numeric ranges.
 */
public final class GeneticAlgorithmEvaluationCodec<Solution_> {
  private final GeneticAlgorithmWorkspace<Solution_, ?> coordinatorWorkspace;
  private final List<Object> canonicalObjects;
  private final IdentityHashMap<Object, Integer> objectIds = new IdentityHashMap<>();
  private final int[] slotEntityIds;
  private final String[] slotVariables;
  private final int[] ownerIds;
  private final int[] valueIds;

  public GeneticAlgorithmEvaluationCodec(
      InnerScoreDirector<Solution_, ?> director,
      GeneticAlgorithmWorkspace<Solution_, ?> workspace) {
    coordinatorWorkspace = Objects.requireNonNull(workspace);
    var objects = new ArrayList<Object>();
    director
        .getSolutionDescriptor()
        .visitAll(director.getWorkingSolution(), value -> add(objects, value));
    slotEntityIds = new int[workspace.slots().size()];
    slotVariables = new String[slotEntityIds.length];
    for (int i = 0; i < slotEntityIds.length; i++) {
      var slot = workspace.slots().get(i);
      slotEntityIds[i] = add(objects, slot.entity());
      slotVariables[i] = slot.variableDescriptor().getVariableName();
    }
    var model = workspace.listModel();
    ownerIds = new int[model == null ? 0 : model.ownerCount()];
    valueIds = new int[model == null ? 0 : model.valueCount()];
    if (model != null) {
      for (int i = 0; i < ownerIds.length; i++) ownerIds[i] = add(objects, model.owner(i));
      for (int i = 0; i < valueIds.length; i++) valueIds[i] = add(objects, model.value(i));
    }
    canonicalObjects = List.copyOf(objects);
  }

  private int add(List<Object> objects, Object value) {
    var id = objectIds.get(value);
    if (id != null) return id;
    int next = objects.size();
    objectIds.put(value, next);
    objects.add(value);
    return next;
  }

  public EncodedGenome encode(GeneticAlgorithmGenome genome) {
    if (genome.size() != slotEntityIds.length || genome.listCount() != ownerIds.length) {
      throw new IllegalArgumentException(
          "The evaluator genome does not match its coordinator workspace.");
    }
    var values = new Object[genome.size()];
    for (int i = 0; i < values.length; i++) {
      var value = genome.value(i);
      if (value == null || DeepCloningUtils.isImmutable(value.getClass())) {
        values[i] = value;
      } else {
        var id = objectIds.get(value);
        if (id == null) {
          var slot = coordinatorWorkspace.slots().get(i);
          throw new IllegalArgumentException(
              "The genetic algorithm evaluator value (%s) for variable (%s) on entity (%s) is not a canonical solution object."
                  .formatted(
                      value,
                      slot.variableDescriptor().getSimpleEntityAndVariableName(),
                      slot.entity()));
        }
        values[i] = new Reference(id);
      }
    }
    return new EncodedGenome(values, genome.lists());
  }

  /** Called only during the startup barrier, while the coordinator graph is frozen. */
  public Decoder createDecoder(
      InnerScoreDirector<Solution_, ?> director,
      GeneticAlgorithmWorkspace<Solution_, ?> workspace) {
    var present = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    director.getSolutionDescriptor().visitAll(director.getWorkingSolution(), present::add);
    var localObjects = new Object[canonicalObjects.size()];
    for (int i = 0; i < localObjects.length; i++) {
      var original = canonicalObjects.get(i);
      var local = present.contains(original) ? original : director.lookUpWorkingObject(original);
      if (local == null)
        throw new IllegalArgumentException("An evaluator canonical object could not be rebased.");
      if (local == original
          && director.getSolutionDescriptor().findEntityDescriptor(original.getClass()) != null) {
        throw new IllegalStateException(
            "The genetic algorithm evaluator shares a planning entity with its coordinator.");
      }
      localObjects[i] = local;
    }
    var slots = new IdentityHashMap<Object, Map<String, Integer>>();
    for (int i = 0; i < workspace.slots().size(); i++) {
      var slot = workspace.slots().get(i);
      slots
          .computeIfAbsent(slot.entity(), ignored -> new LinkedHashMap<>())
          .put(slot.variableDescriptor().getVariableName(), i);
    }
    if (workspace.slots().size() != slotEntityIds.length) {
      throw new IllegalArgumentException(
          "The evaluator basic slot count differs from its coordinator.");
    }
    var slotMapping = new int[slotEntityIds.length];
    var seenSlots = new boolean[slotMapping.length];
    for (int i = 0; i < slotMapping.length; i++) {
      var variables = slots.get(localObjects[slotEntityIds[i]]);
      var local = variables == null ? null : variables.get(slotVariables[i]);
      if (local == null || seenSlots[local]) {
        throw new IllegalArgumentException(
            "The evaluator has an unknown or repeated basic slot (" + i + ").");
      }
      seenSlots[local] = true;
      slotMapping[i] = local;
    }
    var model = workspace.listModel();
    if ((model == null ? 0 : model.ownerCount()) != ownerIds.length
        || (model == null ? 0 : model.valueCount()) != valueIds.length) {
      throw new IllegalArgumentException("The evaluator list model differs from its coordinator.");
    }
    var ownerMapping = new int[ownerIds.length];
    var valueMapping = new int[valueIds.length];
    if (model != null) {
      var owners = new IdentityHashMap<Object, Integer>();
      var values = new IdentityHashMap<Object, Integer>();
      for (int i = 0; i < ownerIds.length; i++) owners.put(model.owner(i), i);
      for (int i = 0; i < valueIds.length; i++) values.put(model.value(i), i);
      map(localObjects, ownerIds, owners, ownerMapping, "owner");
      map(localObjects, valueIds, values, valueMapping, "value");
    }
    return new Decoder(localObjects, slotMapping, ownerMapping, valueMapping);
  }

  private static void map(
      Object[] objects,
      int[] ids,
      IdentityHashMap<Object, Integer> locals,
      int[] mapping,
      String kind) {
    var seen = new boolean[mapping.length];
    for (int i = 0; i < ids.length; i++) {
      var local = locals.get(objects[ids[i]]);
      if (local == null || seen[local]) {
        throw new IllegalArgumentException(
            "The evaluator has an unknown or repeated list " + kind + " (" + i + ").");
      }
      seen[local] = true;
      mapping[i] = local;
    }
  }

  private record Reference(int id) {}

  public static final class EncodedGenome {
    private final Object[] values;
    private final int[][] lists;

    private EncodedGenome(Object[] values, int[][] lists) {
      this.values = values.clone();
      this.lists = Arrays.stream(lists).map(int[]::clone).toArray(int[][]::new);
    }
  }

  /** Worker-owned canonical mapping. It retains no coordinator planning entities. */
  public static final class Decoder {
    private final Object[] objects;
    private final int[] slots;
    private final int[] owners;
    private final int[] values;

    private Decoder(Object[] objects, int[] slots, int[] owners, int[] values) {
      this.objects = objects;
      this.slots = slots;
      this.owners = owners;
      this.values = values;
    }

    public GeneticAlgorithmGenome decode(EncodedGenome encoded) {
      var basics = new Object[slots.length];
      for (int i = 0; i < slots.length; i++) {
        var value = encoded.values[i];
        basics[slots[i]] = value instanceof Reference reference ? objects[reference.id()] : value;
      }
      var lists = new int[owners.length][];
      for (int owner = 0; owner < owners.length; owner++) {
        var source = encoded.lists[owner];
        var row = new int[source.length];
        for (int index = 0; index < row.length; index++) {
          var value = source[index];
          // Preserve invalid IDs so the workspace's whole-candidate preflight rejects them.
          row[index] = value < 0 || value >= values.length ? value : values[value];
        }
        lists[owners[owner]] = row;
      }
      return new GeneticAlgorithmGenome(basics, lists);
    }
  }
}
