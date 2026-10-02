package greycos.solver.core.api.solver.multistage;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A list placement or insertion destination. A null entity and index -1 denote unassignment.
 * Destination indices refer to the destination list after the moved value has been removed from its
 * previous position. Current positions refer to the current, unchanged list.
 */
public record MultistagePosition<Entity_>(@Nullable Entity_ entity, int index) {

  public MultistagePosition {
    if (entity == null ? index != -1 : index < 0) {
      throw new IllegalArgumentException(
          "The entity (%s) and index (%d) must describe an assigned position or null/-1."
              .formatted(entity, index));
    }
  }

  public static <Entity_> MultistagePosition<Entity_> assigned(Entity_ entity, int index) {
    return new MultistagePosition<>(Objects.requireNonNull(entity), index);
  }

  public static <Entity_> MultistagePosition<Entity_> unassigned() {
    return new MultistagePosition<>(null, -1);
  }

  public boolean isUnassigned() {
    return entity == null;
  }
}
