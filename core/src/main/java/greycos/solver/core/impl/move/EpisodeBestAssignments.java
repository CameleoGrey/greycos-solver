package greycos.solver.core.impl.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.GenuineAssignmentChangeObserver;

/**
 * An episode-owned best assignment. Only strict improvements copy dirty genuine bindings; the
 * immutable result is materialized once after the episode stops committing moves.
 */
public final class EpisodeBestAssignments<Solution_>
    implements GenuineAssignmentChangeObserver<Solution_> {
  private final Map<
          GenuineVariableDescriptor<Solution_>, List<SolutionAssignments.BasicChangeRecord<?>>>
      basic;
  private final Map<
          ListVariableDescriptor<Solution_>, List<SolutionAssignments.ListChangeRecord<?>>>
      lists;
  private final Map<GenuineVariableDescriptor<Solution_>, IdentityHashMap<Object, Integer>>
      indexes = new IdentityHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, Set<Object>> dirty =
      new LinkedHashMap<>();
  private SolutionAssignments<Solution_> frozen;

  public EpisodeBestAssignments(SolutionDescriptor<Solution_> descriptor, Solution_ solution) {
    var initial = SolutionAssignments.captureComplete(descriptor, solution);
    basic = new LinkedHashMap<>();
    lists = new LinkedHashMap<>();
    initial
        .getBasicChanges()
        .forEach(
            (variable, records) -> {
              basic.put(variable, new ArrayList<>(records));
              var entityIndexes = new IdentityHashMap<Object, Integer>();
              for (int i = 0; i < records.size(); i++)
                entityIndexes.put(records.get(i).entity(), i);
              indexes.put(variable, entityIndexes);
            });
    initial
        .getListChanges()
        .forEach(
            (variable, records) -> {
              lists.put(variable, new ArrayList<>(records));
              var entityIndexes = new IdentityHashMap<Object, Integer>();
              for (int i = 0; i < records.size(); i++)
                entityIndexes.put(records.get(i).entity(), i);
              indexes.put(variable, entityIndexes);
            });
  }

  @Override
  public void beforeAssignmentChanged(
      GenuineVariableDescriptor<Solution_> variable, Object entity) {
    requireMutable();
    var entityIndexes = indexes.get(variable);
    if (entityIndexes == null || !entityIndexes.containsKey(entity)) {
      throw new IllegalStateException(
          "The committed assignment for variable ("
              + variable
              + ") and entity ("
              + entity
              + ") is not part of the episode's working solution.");
    }
    dirty
        .computeIfAbsent(variable, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
        .add(entity);
  }

  /** Called only after a committed state strictly improves the native episode-best score. */
  public void updateBest() {
    requireMutable();
    long bindings = 0;
    long listElements = 0;
    for (var entry : dirty.entrySet()) {
      var variable = entry.getKey();
      var entityIndexes = indexes.get(variable);
      for (var entity : entry.getValue()) {
        int index = entityIndexes.get(entity);
        if (variable instanceof ListVariableDescriptor<Solution_> listVariable) {
          var record =
              new SolutionAssignments.ListChangeRecord<>(entity, listVariable.getValue(entity));
          lists.get(listVariable).set(index, record);
          listElements += record.values().size();
        } else {
          basic
              .get(variable)
              .set(
                  index,
                  new SolutionAssignments.BasicChangeRecord<>(entity, variable.getValue(entity)));
        }
        bindings++;
      }
    }
    dirty.clear();
    var diagnostics = SolutionAssignmentDiagnostics.current();
    if (diagnostics != null) diagnostics.accumulated(bindings, listElements);
  }

  /** Transfers ownership of the private record lists; no genuine bindings are copied here. */
  public SolutionAssignments<Solution_> freeze() {
    if (frozen == null) {
      frozen = new SolutionAssignments<>(basic, lists);
      dirty.clear();
      indexes.clear();
    }
    return frozen;
  }

  private void requireMutable() {
    if (frozen != null)
      throw new IllegalStateException("The episode best assignments are already frozen.");
  }
}
