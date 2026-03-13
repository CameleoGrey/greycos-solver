package ai.greycos.solver.core.impl.cotwin.lookup;

import java.util.Map;

import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.util.Pair;

public final class PlanningIdLookUpStrategy implements LookUpStrategy {

  private final MemberAccessor planningIdMemberAccessor;

  public PlanningIdLookUpStrategy(MemberAccessor planningIdMemberAccessor) {
    this.planningIdMemberAccessor = planningIdMemberAccessor;
  }

  @Override
  public void addWorkingObject(Map<Object, Object> idToWorkingObjectMap, Object workingObject) {
    var planningId = extractPlanningId(workingObject);
    var oldAddedObject = idToWorkingObjectMap.put(planningId, workingObject);
    if (oldAddedObject != null) {
      throw new IllegalStateException(
          "The workingObjects ("
              + oldAddedObject
              + ", "
              + workingObject
              + ") have the same planningId ("
              + planningId
              + "). Working objects must be unique.");
    }
  }

  @Override
  public void removeWorkingObject(Map<Object, Object> idToWorkingObjectMap, Object workingObject) {
    var planningId = extractPlanningId(workingObject);
    var removedObject = idToWorkingObjectMap.remove(planningId);
    if (workingObject != removedObject) {
      throw new IllegalStateException(
          "The workingObject ("
              + workingObject
              + ") differs from the removedObject ("
              + removedObject
              + ") for planningId ("
              + planningId
              + ").");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E> E lookUpWorkingObject(Map<Object, Object> idToWorkingObjectMap, E externalObject) {
    var planningId = extractPlanningId(externalObject);
    var workingObject = idToWorkingObjectMap.get(planningId);
    if (workingObject == null) {
      throw new IllegalStateException(
          """
          The externalObject (%s) with planningId (%s) has no known workingObject (%s).
          Maybe the workingObject was never added because the planning solution doesn't have a @%s annotation on a member with instances of the externalObject's class (%s)."""
              .formatted(
                  externalObject,
                  planningId,
                  workingObject,
                  ProblemFactCollectionProperty.class.getSimpleName(),
                  externalObject.getClass()));
    }
    return (E) workingObject;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <E> E lookUpWorkingObjectIfExists(
      Map<Object, Object> idToWorkingObjectMap, E externalObject) {
    var planningId = extractPlanningId(externalObject);
    return (E) idToWorkingObjectMap.get(planningId);
  }

  private Pair<Class<?>, Object> extractPlanningId(Object externalObject) {
    var planningId = planningIdMemberAccessor.executeGetter(externalObject);
    if (planningId == null) {
      throw new IllegalArgumentException(
          """
          The planningId (%s) of the member (%s) of the class (%s) on externalObject (%s) must not be null.
          Maybe initialize the planningId of the class (%s) instance (%s) before solving."""
              .formatted(
                  planningId,
                  planningIdMemberAccessor,
                  externalObject.getClass(),
                  externalObject,
                  externalObject.getClass().getSimpleName(),
                  externalObject));
    }
    return new Pair<>(externalObject.getClass(), planningId);
  }
}
