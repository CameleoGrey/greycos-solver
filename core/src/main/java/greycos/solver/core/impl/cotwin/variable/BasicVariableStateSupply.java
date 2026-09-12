package greycos.solver.core.impl.cotwin.variable;

import greycos.solver.core.impl.cotwin.variable.inverserelation.CollectionInverseVariableSupply;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;

import org.jspecify.annotations.NullMarked;

/**
 * Single source of truth for the inverse relation of a basic {@link
 * greycos.solver.core.api.cotwin.variable.PlanningVariable}. If the {@link
 * InverseRelationShadowVariableDescriptor} is externalized, there is a field on an entity holding
 * the inverse collection and that field is used. Otherwise, an internal map is used to track the
 * inverse collection.
 *
 * @param <Solution_>
 */
@NullMarked
public interface BasicVariableStateSupply<Solution_>
    extends BasicVariableChangeHandler<Solution_>, CollectionInverseVariableSupply {

  void externalize(InverseRelationShadowVariableDescriptor<Solution_> descriptor);
}
