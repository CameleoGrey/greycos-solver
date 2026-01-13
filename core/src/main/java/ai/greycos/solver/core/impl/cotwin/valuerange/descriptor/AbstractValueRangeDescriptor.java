package ai.greycos.solver.core.impl.cotwin.valuerange.descriptor;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public abstract sealed class AbstractValueRangeDescriptor<Solution_>
    implements ValueRangeDescriptor<Solution_>
    permits AbstractFromPropertyValueRangeDescriptor, CompositeValueRangeDescriptor {

  private final int ordinal;
  protected final GenuineVariableDescriptor<Solution_> variableDescriptor;

  protected AbstractValueRangeDescriptor(
      int ordinal, GenuineVariableDescriptor<Solution_> variableDescriptor) {
    this.ordinal = ordinal;
    this.variableDescriptor = variableDescriptor;
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  @Override
  public GenuineVariableDescriptor<Solution_> getVariableDescriptor() {
    return variableDescriptor;
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public boolean mightContainEntity() {
    SolutionDescriptor<Solution_> solutionDescriptor =
        variableDescriptor.getEntityDescriptor().getSolutionDescriptor();
    Class<?> variablePropertyType;
    if (variableDescriptor instanceof ListVariableDescriptor<Solution_> listVariableDescriptor) {
      // For list variables, the element type determines whether the value range contains entities.
      variablePropertyType = listVariableDescriptor.getElementType();
    } else {
      variablePropertyType = variableDescriptor.getVariablePropertyType();
    }
    for (Class<?> entityClass : solutionDescriptor.getEntityClassSet()) {
      if (variablePropertyType.isAssignableFrom(entityClass)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + variableDescriptor.getVariableName() + ")";
  }
}
