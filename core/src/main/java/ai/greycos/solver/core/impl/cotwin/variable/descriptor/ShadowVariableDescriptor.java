package ai.greycos.solver.core.impl.cotwin.variable.descriptor;

import java.util.Collection;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import ai.greycos.solver.core.impl.cotwin.variable.listener.VariableListenerWithSources;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public abstract class ShadowVariableDescriptor<Solution_> extends VariableDescriptor<Solution_> {

  private int globalShadowOrder = Integer.MAX_VALUE;

  // ************************************************************************
  // Constructors and simple getters/setters
  // ************************************************************************

  protected ShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor, true);
  }

  public int getGlobalShadowOrder() {
    return globalShadowOrder;
  }

  public void setGlobalShadowOrder(int globalShadowOrder) {
    this.globalShadowOrder = globalShadowOrder;
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  public abstract void processAnnotations(DescriptorPolicy descriptorPolicy);

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  /**
   * Inverse of {@link #getSinkVariableDescriptorList()}.
   *
   * @return never null, only variables affect this shadow variable directly
   */
  public abstract List<VariableDescriptor<Solution_>> getSourceVariableDescriptorList();

  public abstract Collection<Class<?>> getVariableListenerClasses();

  /**
   * @return never null
   */
  public abstract Demand<?> getProvidedDemand();

  public boolean hasVariableListener() {
    return true;
  }

  /** return true if the source variable is a list variable; otherwise, return false. */
  public abstract boolean isListVariableSource();

  /**
   * @param supplyManager never null
   * @return never null
   */
  public abstract Iterable<VariableListenerWithSources> buildVariableListeners(
      SupplyManager supplyManager);

  // ************************************************************************
  // Extraction methods
  // ************************************************************************

  @Override
  public String toString() {
    return getSimpleEntityAndVariableName() + " shadow";
  }
}
