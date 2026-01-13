package ai.greycos.solver.core.impl.cotwin.valuerange.descriptor;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.buildin.bigdecimal.BigDecimalValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.buildin.composite.CompositeCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.buildin.primdouble.DoubleValueRange;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class CompositeValueRangeDescriptor<Solution_>
    extends AbstractValueRangeDescriptor<Solution_> {

  private final boolean canExtractValueRangeFromSolution;
  private final List<ValueRangeDescriptor<Solution_>> childValueRangeDescriptorList;
  private final boolean isGenericTypeImmutable;

  public CompositeValueRangeDescriptor(
      int ordinal,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      List<ValueRangeDescriptor<Solution_>> childValueRangeDescriptorList) {
    super(ordinal, variableDescriptor);
    this.childValueRangeDescriptorList = childValueRangeDescriptorList;
    var canExtractFromSolution = true;
    var isImmutable = true;
    for (var valueRangeDescriptor : childValueRangeDescriptorList) {
      if (!valueRangeDescriptor.isCountable()) {
        throw new IllegalStateException(
            """
                        The valueRange (%s) has a childValueRange (%s) which is not countable.
                        Maybe replace %s with %s?"""
                .formatted(
                    this,
                    valueRangeDescriptor,
                    DoubleValueRange.class.getSimpleName(),
                    BigDecimalValueRange.class.getSimpleName()));
      }
      canExtractFromSolution =
          canExtractFromSolution && valueRangeDescriptor.canExtractValueRangeFromSolution();
      isImmutable = isImmutable && valueRangeDescriptor.isGenericTypeImmutable();
    }
    this.canExtractValueRangeFromSolution = canExtractFromSolution;
    this.isGenericTypeImmutable = isImmutable;
  }

  @Override
  public boolean isGenericTypeImmutable() {
    return isGenericTypeImmutable;
  }

  public int getValueRangeCount() {
    return childValueRangeDescriptorList.size();
  }

  @Override
  public boolean canExtractValueRangeFromSolution() {
    return canExtractValueRangeFromSolution;
  }

  @Override
  public boolean isCountable() {
    return true;
  }

  @Override
  public <T> ValueRange<T> extractAllValues(Solution_ solution) {
    var childValueRangeList =
        new ArrayList<AbstractCountableValueRange<T>>(childValueRangeDescriptorList.size());
    for (var valueRangeDescriptor : childValueRangeDescriptorList) {
      childValueRangeList.add(
          (AbstractCountableValueRange<T>) valueRangeDescriptor.<T>extractAllValues(solution));
    }
    return new CompositeCountableValueRange<>(childValueRangeList);
  }

  @Override
  public <T> ValueRange<T> extractValuesFromEntity(Solution_ solution, Object entity) {
    var childValueRangeList =
        new ArrayList<AbstractCountableValueRange<T>>(childValueRangeDescriptorList.size());
    for (var valueRangeDescriptor : childValueRangeDescriptorList) {
      childValueRangeList.add(
          (AbstractCountableValueRange<T>)
              valueRangeDescriptor.<T>extractValuesFromEntity(solution, entity));
    }
    return new CompositeCountableValueRange<>(childValueRangeList);
  }
}
