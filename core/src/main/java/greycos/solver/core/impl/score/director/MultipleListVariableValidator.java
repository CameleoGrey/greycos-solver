package greycos.solver.core.impl.score.director;

import java.util.IdentityHashMap;

import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

/** Validates independent list populations before any state or shadow variables are installed. */
final class MultipleListVariableValidator {

  private MultipleListVariableValidator() {}

  static <Solution_> void validate(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ValueRangeManager<Solution_> valueRangeManager,
      Solution_ solution) {
    var descriptors = solutionDescriptor.getListVariableDescriptorList();
    if (descriptors.size() < 2) {
      return;
    }
    var valueFamilies = new IdentityHashMap<Object, ListVariableDescriptor<Solution_>>();
    var listOwners = new IdentityHashMap<Object, ListOwner<Solution_>>();
    for (var descriptor : descriptors) {
      var range = valueRangeManager.getFromSolution(descriptor.getValueRangeDescriptor());
      var values = range.createOriginalIterator();
      var familyValues = new IdentityHashMap<Object, Boolean>();
      while (values.hasNext()) {
        var value = values.next();
        validateElement(solutionDescriptor, descriptor, value);
        var previousDescriptor = valueFamilies.putIfAbsent(value, descriptor);
        if (previousDescriptor != null && previousDescriptor != descriptor) {
          throw new IllegalArgumentException(
              ("The planning element (%s) occurs in the value ranges of both list variables (%s) and (%s). "
                      + "Use distinct allocation or occurrence objects for independent list variables.")
                  .formatted(value, previousDescriptor, descriptor));
        }
        familyValues.put(value, Boolean.TRUE);
      }
      var assignments = new IdentityHashMap<Object, Object>();
      descriptor
          .getEntityDescriptor()
          .visitAllEntities(
              solution,
              owner -> {
                var list = descriptor.getValue(owner);
                var previousOwner =
                    listOwners.putIfAbsent(list, new ListOwner<>(descriptor, owner));
                if (previousOwner != null
                    && (previousOwner.descriptor() != descriptor
                        || previousOwner.owner() != owner)) {
                  throw new IllegalArgumentException(
                      ("The list container for variable (%s) on entity (%s) is also used by variable (%s) on entity (%s). "
                              + "Each planning list must have its own mutable list instance.")
                          .formatted(
                              descriptor,
                              owner,
                              previousOwner.descriptor(),
                              previousOwner.owner()));
                }
                for (var value : list) {
                  validateElement(solutionDescriptor, descriptor, value);
                  if (!familyValues.containsKey(value)) {
                    throw new IllegalArgumentException(
                        "The element (%s) assigned to list variable (%s) on entity (%s) is not an object from its value range."
                            .formatted(value, descriptor, owner));
                  }
                  var previousAssignment = assignments.putIfAbsent(value, owner);
                  if (previousAssignment != null) {
                    throw new IllegalArgumentException(
                        "The element (%s) is assigned more than once to list variable (%s), on entities (%s) and (%s)."
                            .formatted(value, descriptor, previousAssignment, owner));
                  }
                }
              });
    }
  }

  private static <Solution_> void validateElement(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ListVariableDescriptor<Solution_> descriptor,
      Object value) {
    if (!descriptor.getElementType().isInstance(value)) {
      throw new IllegalArgumentException(
          "The element (%s) of list variable (%s) must have the declared element type (%s)."
              .formatted(value, descriptor, descriptor.getElementType().getName()));
    }
    var entityDescriptor = solutionDescriptor.findEntityDescriptor(value.getClass());
    if (entityDescriptor != null && entityDescriptor.hasAnyListVariables()) {
      throw new IllegalArgumentException(
          "The element (%s) of list variable (%s) also owns a planning list. Nested list ownership is not supported."
              .formatted(value, descriptor));
    }
  }

  private record ListOwner<Solution_>(ListVariableDescriptor<Solution_> descriptor, Object owner) {}
}
