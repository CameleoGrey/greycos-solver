package greycos.solver.core.impl.multistage;

import java.util.Objects;

import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

/** A declared variable and its exact entity scope, resolved before candidate evaluation. */
public record MultistageVariableBinding<Solution_>(
    MultistageVariableReference<?, ?> reference,
    GenuineVariableDescriptor<Solution_> variable,
    EntityDescriptor<Solution_> targetEntityDescriptor) {
  public MultistageVariableBinding {
    Objects.requireNonNull(reference);
    Objects.requireNonNull(variable);
    Objects.requireNonNull(targetEntityDescriptor);
    if (!variable
        .getEntityDescriptor()
        .getEntityClass()
        .isAssignableFrom(targetEntityDescriptor.getEntityClass())) {
      throw new IllegalArgumentException(
          "Multistage target entity ("
              + targetEntityDescriptor
              + ") does not declare or inherit variable ("
              + variable
              + ").");
    }
  }
}
