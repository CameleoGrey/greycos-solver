package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile.ArgumentShape;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

/** Immutable metadata shared by phases and their child score directors. */
public final class ConstructionHeuristicNearbyProfiles {

  private static final ConstructionHeuristicNearbyProfiles EMPTY =
      new ConstructionHeuristicNearbyProfiles(List.of());

  private final Map<GenuineVariableDescriptor<?>, List<ConstructionHeuristicNearbyProfile>>
      profiles;

  public ConstructionHeuristicNearbyProfiles(
      Collection<ConstructionHeuristicNearbyProfile> profiles) {
    var byVariable =
        new LinkedHashMap<GenuineVariableDescriptor<?>, List<ConstructionHeuristicNearbyProfile>>();
    for (var profile : profiles) {
      var variableProfiles =
          byVariable.computeIfAbsent(profile.variableDescriptor(), ignored -> new ArrayList<>());
      if (variableProfiles.stream()
          .anyMatch(
              existing ->
                  existing.distanceMeterClass() == profile.distanceMeterClass()
                      && dominates(existing.argumentShape(), profile.argumentShape()))) {
        continue;
      }
      variableProfiles.removeIf(
          existing ->
              existing.distanceMeterClass() == profile.distanceMeterClass()
                  && dominates(profile.argumentShape(), existing.argumentShape()));
      // Distribution tuning and repeated selector families do not create new ranking streams.
      if (variableProfiles.stream()
          .noneMatch(
              existing ->
                  existing.distanceMeterClass() == profile.distanceMeterClass()
                      && existing.argumentShape() == profile.argumentShape())) {
        variableProfiles.add(profile);
      }
    }
    byVariable.replaceAll((variable, variableProfiles) -> List.copyOf(variableProfiles));
    this.profiles = Collections.unmodifiableMap(byVariable);
  }

  public static ConstructionHeuristicNearbyProfiles empty() {
    return EMPTY;
  }

  public List<ConstructionHeuristicNearbyProfile> getProfiles(
      GenuineVariableDescriptor<?> variableDescriptor) {
    return profiles.getOrDefault(variableDescriptor, List.of());
  }

  public boolean isEmpty() {
    return profiles.isEmpty();
  }

  private static boolean dominates(ArgumentShape direct, ArgumentShape anchor) {
    return (direct == ArgumentShape.ENTITY_VALUE && anchor == ArgumentShape.ENTITY_ENTITY)
        || (direct == ArgumentShape.VALUE_DESTINATION && anchor == ArgumentShape.VALUE_VALUE);
  }
}
