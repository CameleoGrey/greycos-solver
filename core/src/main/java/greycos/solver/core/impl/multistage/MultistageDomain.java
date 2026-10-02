package greycos.solver.core.impl.multistage;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Stable membership shared by candidates of one provider session; eligibility remains live. */
final class MultistageDomain<Solution_> {
  final List<Object> entities;
  final Map<Object, Boolean> entityMembership = new IdentityHashMap<>();
  private Map<Object, Boolean> values;

  MultistageDomain(GenuineVariableDescriptor<Solution_> variable, Solution_ solution) {
    this(variable.getEntityDescriptor(), solution);
  }

  MultistageDomain(EntityDescriptor<Solution_> targetEntityDescriptor, Solution_ solution) {
    entities = List.copyOf(targetEntityDescriptor.extractEntities(solution));
    entities.forEach(entity -> entityMembership.put(entity, Boolean.TRUE));
  }

  boolean containsValue(
      Object value,
      GenuineVariableDescriptor<Solution_> variable,
      InnerScoreDirector<Solution_, ?> director,
      Runnable checkpoint) {
    if (values == null) {
      checkpoint.run();
      var newValues = new IdentityHashMap<Object, Boolean>();
      var descriptor = variable.getValueRangeDescriptor();
      if (descriptor.canExtractValueRangeFromSolution()) {
        collect(
            director.getValueRangeManager().getFromSolution(descriptor), newValues, checkpoint, 0);
      } else {
        int traversed = 0;
        for (var entity : entities) {
          if ((++traversed & 63) == 0) checkpoint.run();
          traversed =
              collect(
                  director.getValueRangeManager().getFromEntity(descriptor, entity),
                  newValues,
                  checkpoint,
                  traversed);
        }
      }
      checkpoint.run();
      values = newValues;
    }
    return values.containsKey(value);
  }

  private static int collect(
      ValueRange<Object> range, Map<Object, Boolean> values, Runnable checkpoint, int traversed) {
    var iterator = range.createOriginalIterator();
    while (iterator.hasNext()) {
      if ((++traversed & 63) == 0) checkpoint.run();
      values.put(iterator.next(), Boolean.TRUE);
    }
    return traversed;
  }
}
