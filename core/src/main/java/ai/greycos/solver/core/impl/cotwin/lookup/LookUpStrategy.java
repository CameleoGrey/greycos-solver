package ai.greycos.solver.core.impl.cotwin.lookup;

import java.util.Map;

import org.jspecify.annotations.Nullable;

public sealed interface LookUpStrategy
    permits EqualsLookUpStrategy,
        ImmutableLookUpStrategy,
        NoneLookUpStrategy,
        PlanningIdLookUpStrategy {

  void addWorkingObject(Map<Object, Object> idToWorkingObjectMap, Object workingObject);

  void removeWorkingObject(Map<Object, Object> idToWorkingObjectMap, Object workingObject);

  <E> E lookUpWorkingObject(Map<Object, Object> idToWorkingObjectMap, E externalObject);

  <E> @Nullable E lookUpWorkingObjectIfExists(
      Map<Object, Object> idToWorkingObjectMap, E externalObject);
}
