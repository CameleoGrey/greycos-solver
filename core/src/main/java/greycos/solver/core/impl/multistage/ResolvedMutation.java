package greycos.solver.core.impl.multistage;

import java.util.List;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

/** Resolved genuine-variable mutation. It contains no provider, evaluator or callback. */
record ResolvedMutation<Solution_>(
    GenuineVariableDescriptor<Solution_> descriptor,
    Object entity,
    Object value,
    int fromIndex,
    int removedCount,
    List<Object> inserted,
    List<Object> unassigned,
    List<Object> assigned) {

  static <S> ResolvedMutation<S> basic(
      GenuineVariableDescriptor<S> descriptor, Object entity, Object value) {
    return new ResolvedMutation<>(
        descriptor, entity, value, -1, 0, List.of(), List.of(), List.of());
  }

  static <S> ResolvedMutation<S> splice(
      ListVariableDescriptor<S> descriptor,
      Object entity,
      int from,
      int removed,
      List<Object> inserted,
      List<Object> unassigned,
      List<Object> assigned) {
    return new ResolvedMutation<>(
        descriptor,
        entity,
        null,
        from,
        removed,
        List.copyOf(inserted),
        List.copyOf(unassigned),
        List.copyOf(assigned));
  }

  static <S> void applyAll(
      List<ResolvedMutation<S>> mutations, VariableDescriptorAwareScoreDirector<S> director) {
    // Notify all source windows before any value changes ownership, as required by list swaps.
    for (var mutation : mutations) mutation.before(director);
    for (var mutation : mutations) mutation.mutate();
    for (var mutation : mutations) mutation.after(director);
  }

  private void before(VariableDescriptorAwareScoreDirector<Solution_> director) {
    if (fromIndex < 0) {
      director.beforeVariableChanged(descriptor, entity);
    } else {
      var listDescriptor = (ListVariableDescriptor<Solution_>) descriptor;
      for (var element : unassigned)
        director.beforeListVariableElementUnassigned(listDescriptor, element);
      for (var element : assigned)
        director.beforeListVariableElementAssigned(listDescriptor, element);
      director.beforeListVariableChanged(
          listDescriptor, entity, fromIndex, fromIndex + removedCount);
    }
  }

  private void mutate() {
    if (fromIndex < 0) descriptor.setValue(entity, value);
    else {
      var listDescriptor = (ListVariableDescriptor<Solution_>) descriptor;
      var list = listDescriptor.getValue(entity);
      list.subList(fromIndex, fromIndex + removedCount).clear();
      list.addAll(fromIndex, inserted);
    }
  }

  private void after(VariableDescriptorAwareScoreDirector<Solution_> director) {
    if (fromIndex < 0) director.afterVariableChanged(descriptor, entity);
    else {
      var listDescriptor = (ListVariableDescriptor<Solution_>) descriptor;
      director.afterListVariableChanged(
          listDescriptor, entity, fromIndex, fromIndex + inserted.size());
      for (var element : unassigned)
        director.afterListVariableElementUnassigned(listDescriptor, element);
      for (var element : assigned)
        director.afterListVariableElementAssigned(listDescriptor, element);
    }
  }

  ResolvedMutation<Solution_> rebase(Lookup lookup) {
    return new ResolvedMutation<>(
        descriptor,
        lookup.lookUpWorkingObject(entity),
        value == null ? null : lookup.lookUpWorkingObject(value),
        fromIndex,
        removedCount,
        rebase(inserted, lookup),
        rebase(unassigned, lookup),
        rebase(assigned, lookup));
  }

  private static List<Object> rebase(List<Object> values, Lookup lookup) {
    return values.stream().map(lookup::lookUpWorkingObject).toList();
  }
}
