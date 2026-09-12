package greycos.solver.core.impl.cotwin.variable.descriptor;

import java.util.Collection;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import greycos.solver.core.impl.cotwin.variable.supply.Demand;

import org.jspecify.annotations.Nullable;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public abstract class ShadowVariableDescriptor<Solution_> extends VariableDescriptor<Solution_> {

  // ************************************************************************
  // Constructors and simple getters/setters
  // ************************************************************************

  protected ShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor, true);
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  public abstract void processAnnotations(DescriptorPolicy descriptorPolicy);

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  /**
   * @return if null, there is no source variable
   */
  public @Nullable VariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return null;
  }

  /**
   * @return never null, the classes responsible for updating this shadow variable
   */
  public abstract Collection<Class<?>> getUpdaterClasses();

  /**
   * @return never null
   */
  public abstract Demand<?> getProvidedDemand();

  // ************************************************************************
  // Extraction methods
  // ************************************************************************

  @Override
  public String toString() {
    return getSimpleEntityAndVariableName() + " shadow";
  }
}
