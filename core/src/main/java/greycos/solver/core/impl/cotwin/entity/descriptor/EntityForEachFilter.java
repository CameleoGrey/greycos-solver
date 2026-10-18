package greycos.solver.core.impl.cotwin.entity.descriptor;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.cotwin.variable.declarative.DeclarativeShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class EntityForEachFilter<Solution_> {
  private final EntityDescriptor<Solution_> entityDescriptor;
  private final Predicate<Object> assignedPredicate;
  private final boolean hasDeclarativeShadowVariables;

  EntityForEachFilter(EntityDescriptor<Solution_> entityDescriptor) {
    var solutionDescriptor = entityDescriptor.getSolutionDescriptor();
    this.entityDescriptor = entityDescriptor;
    this.assignedPredicate =
        getAssignedPredicate(entityDescriptor, solutionDescriptor.getListVariableDescriptorList());
    this.hasDeclarativeShadowVariables = !getDeclarativeShadowVariables(entityDescriptor).isEmpty();
  }

  public Predicate<Object> getAssignedAndConsistentPredicate(
      ConsistencyTracker<Solution_> consistencyTracker) {
    if (!hasDeclarativeShadowVariables) {
      return assignedPredicate;
    }
    var entityConsistencyState =
        consistencyTracker.getDeclarativeEntityConsistencyState(entityDescriptor);
    // When the tracker is frozen (ConstraintVerifier mode), use a more lenient predicate
    // that only checks consistency, not the hasNoNullVariables requirement.
    // This allows ConstraintVerifier to work with entities that have manually set shadow variables,
    // including intentionally null shadow variables.
    if (consistencyTracker.isFrozen()) {
      return entityConsistencyState::isEntityConsistent;
    }
    return assignedPredicate.and(entityConsistencyState::isEntityConsistent);
  }

  @Nullable
  public Predicate<Object> getConsistentPredicate(
      ConsistencyTracker<Solution_> consistencyTracker) {
    if (!hasDeclarativeShadowVariables) {
      return null;
    }
    var entityConsistencyState =
        consistencyTracker.getDeclarativeEntityConsistencyState(entityDescriptor);
    return entityConsistencyState::isEntityConsistent;
  }

  private static Collection<? extends ShadowVariableDescriptor> getDeclarativeShadowVariables(
      EntityDescriptor<?> entityDescriptor) {
    return entityDescriptor.getShadowVariableDescriptors().stream()
        .filter(
            shadowVariableDescriptor ->
                shadowVariableDescriptor instanceof DeclarativeShadowVariableDescriptor<?>)
        .toList();
  }

  private static Predicate<Object> getAssignedPredicate(
      EntityDescriptor<?> entityDescriptor,
      List<? extends ListVariableDescriptor<?>> listVariableDescriptors) {
    var applicableDescriptors =
        listVariableDescriptors.stream()
            .filter(
                descriptor ->
                    descriptor.acceptsValueType(entityDescriptor.getEntityClass())
                        || entityDescriptor
                            .getEntityClass()
                            .isAssignableFrom(descriptor.getElementType()))
            .toList();
    if (applicableDescriptors.isEmpty()) {
      return entityDescriptor::hasNoNullVariables;
    }
    if (applicableDescriptors.size() == 1
        && applicableDescriptors.getFirst().acceptsValueType(entityDescriptor.getEntityClass())) {
      var inverse = applicableDescriptors.getFirst().getInverseRelationShadowVariableDescriptor();
      if (inverse != null) {
        return entity ->
            entityDescriptor.hasNoNullVariables(entity) && inverse.getValue(entity) != null;
      }
      // The stream factory enforces membership reactively against the list owners.
      return entityDescriptor::hasNoNullVariables;
    }
    return entity -> {
      if (!entityDescriptor.hasNoNullVariables(entity)) {
        return false;
      }
      for (var descriptor : applicableDescriptors) {
        if (descriptor.acceptsValueType(entity.getClass())) {
          var inverse = descriptor.getInverseRelationShadowVariableDescriptor();
          return inverse == null || inverse.getValue(entity) != null;
        }
      }
      return true;
    };
  }
}
