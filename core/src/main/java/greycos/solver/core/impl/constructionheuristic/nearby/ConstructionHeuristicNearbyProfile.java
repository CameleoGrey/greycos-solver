package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.Objects;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

/** The argument contract of a nearby selector, independent of its random selection settings. */
public record ConstructionHeuristicNearbyProfile(
    GenuineVariableDescriptor<?> variableDescriptor,
    Class<? extends NearbyDistanceMeter<?, ?>> distanceMeterClass,
    ArgumentShape argumentShape,
    String provenance) {

  public ConstructionHeuristicNearbyProfile {
    Objects.requireNonNull(variableDescriptor);
    Objects.requireNonNull(distanceMeterClass);
    Objects.requireNonNull(argumentShape);
    Objects.requireNonNull(provenance);
  }

  public enum ArgumentShape {
    ENTITY_VALUE,
    ENTITY_ENTITY,
    VALUE_DESTINATION,
    VALUE_VALUE
  }
}
