package ai.greycos.solver.core.impl.cotwin.valuerange.descriptor;

import java.util.ArrayList;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.buildin.composite.CompositeCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class FromEntityPropertyValueRangeDescriptor<Solution_>
    extends AbstractFromPropertyValueRangeDescriptor<Solution_> {

  public FromEntityPropertyValueRangeDescriptor(
      int ordinal,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      MemberAccessor memberAccessor) {
    super(ordinal, variableDescriptor, memberAccessor);
  }

  @Override
  public <T> ValueRange<T> extractAllValues(Solution_ solution) {
    var entityList = variableDescriptor.getEntityDescriptor().extractEntities(solution);
    var rangesFromEntities = new ArrayList<AbstractCountableValueRange<T>>(entityList.size());
    for (var entity : entityList) {
      var valueRange =
          (AbstractCountableValueRange<T>) this.<T>extractValuesFromEntity(solution, entity);
      rangesFromEntities.add(valueRange);
    }
    return new CompositeCountableValueRange<>(rangesFromEntities);
  }

  @Override
  public <T> ValueRange<T> extractValuesFromEntity(Solution_ solution, Object entity) {
    return readValueRange(entity, solution);
  }

  @Override
  public boolean canExtractValueRangeFromSolution() {
    return false;
  }
}
