package ai.greycos.solver.core.impl.cotwin.valuerange.descriptor;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class FromSolutionPropertyValueRangeDescriptor<Solution_>
    extends AbstractFromPropertyValueRangeDescriptor<Solution_> {

  public FromSolutionPropertyValueRangeDescriptor(
      int ordinal,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      MemberAccessor memberAccessor) {
    super(ordinal, variableDescriptor, memberAccessor);
  }

  @Override
  public <T> ValueRange<T> extractAllValues(Solution_ solution) {
    return readValueRangeForSolution(solution);
  }

  @Override
  public <T> ValueRange<T> extractValuesFromEntity(Solution_ solution, Object entity) {
    return readValueRangeForSolution(
        solution); // Needed for composite ranges on solution and on entity.
  }

  @Override
  public boolean canExtractValueRangeFromSolution() {
    return true;
  }
}
