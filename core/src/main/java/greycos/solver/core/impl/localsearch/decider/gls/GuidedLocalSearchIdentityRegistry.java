package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Phase-local identities; ordinals are assigned once and never encode a changing list position. */
public final class GuidedLocalSearchIdentityRegistry<Solution_> {

  private final InnerScoreDirector<Solution_, ?> director;
  private final GuidedLocalSearchIdentityRegistry<Solution_> root;
  private final Map<Object, Object> tokens = new IdentityHashMap<>();
  private long nextOrdinal;

  private GuidedLocalSearchIdentityRegistry(
      InnerScoreDirector<Solution_, ?> director,
      GuidedLocalSearchIdentityRegistry<Solution_> root) {
    this.director = director;
    this.root = root == null ? this : root;
  }

  public static <Solution_> GuidedLocalSearchIdentityRegistry<Solution_> create(
      InnerScoreDirector<Solution_, ?> director) {
    var registry = new GuidedLocalSearchIdentityRegistry<Solution_>(director, null);
    // Numeric value ranges are deliberately not enumerated. Scalars already have stable tokens.
    director.getSolutionDescriptor().visitAll(director.getWorkingSolution(), registry::token);
    return registry;
  }

  GuidedLocalSearchIdentityRegistry<Solution_> bind(InnerScoreDirector<Solution_, ?> target) {
    if (target == director) {
      return this;
    }
    var bound = new GuidedLocalSearchIdentityRegistry<Solution_>(target, root);
    // Planning-ID and immutable tokens can be obtained independently. Shared facts retain identity.
    // Only deep-cloned objects without IDs need existing worker lookup; defer this until a feature
    // actually uses them, so pinned objects impose no additional worker requirements.
    synchronized (root) {
      root.tokens.forEach(
          (object, token) -> {
            if (!DeepCloningUtils.isClassDeepCloned(
                target.getSolutionDescriptor(), object.getClass())) {
              bound.tokens.put(object, token);
            }
          });
    }
    return bound;
  }

  synchronized Object token(Object object) {
    if (object == null) {
      return Unassigned.INSTANCE;
    }
    var type = object.getClass();
    if ((DeepCloningUtils.IMMUTABLE_CLASSES.contains(type) && type != Optional.class)
        || type.isEnum()
        || Score.class.isAssignableFrom(type)) {
      // Range iterators may allocate a fresh box for every trial. Equality tokens do not require
      // retaining those boxes; only identity-bearing model objects belong in the identity cache.
      return new Value(type, object);
    }
    var cached = tokens.get(object);
    if (cached != null) {
      return cached;
    }
    Object token;
    var accessor = director.getSolutionDescriptor().getPlanningIdAccessor(type);
    if (accessor != null) {
      token =
          new PlanningIdentity(
              type,
              Objects.requireNonNull(
                  accessor.executeGetter(object),
                  "The automatic GLS feature identity has a null @PlanningId on "
                      + type.getName()));
    } else if (root == this) {
      nextOrdinal = Math.incrementExact(nextOrdinal);
      token = new Ordinal(nextOrdinal);
    } else {
      token = tokenFromRoot(object);
    }
    tokens.put(object, token);
    return token;
  }

  private Object tokenFromRoot(Object object) {
    synchronized (root) {
      var shared = root.tokens.get(object);
      if (shared != null) {
        return shared;
      }
      for (var entry : root.tokens.entrySet()) {
        if (entry.getValue() instanceof Ordinal && entry.getKey().getClass() == object.getClass()) {
          if (director.lookUpWorkingObject(entry.getKey()) == object) {
            return entry.getValue();
          }
        }
      }
    }
    // Reaching this branch means a worker cannot rebase an object used by a planning decision.
    // This is the same lookup condition required by its moves, not a single-thread GLS requirement.
    throw new IllegalStateException(
        "Cannot rebase automatic GLS feature object of type ("
            + object.getClass().getName()
            + "). Move workers require a stable lookup identity; "
            + "add @PlanningId or use single-threaded solving.");
  }

  private record Value(Class<?> type, Object value) {}

  private record PlanningIdentity(Class<?> type, Object id) {}

  private record Ordinal(long value) {}

  private enum Unassigned {
    INSTANCE
  }
}
