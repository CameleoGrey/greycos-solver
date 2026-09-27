package greycos.solver.core.impl.nodesharing;

import greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * Builds a subclass which shares equivalent stateless lambdas without duplicating provider state.
 */
public final class DefaultConstraintProviderNodeSharer {

  /* ClassValue permits the provider and its defining loader to be unloaded together. Its value is
   * a holder because computeValue may race; generation and class definition must happen only once. */
  private static final ClassValue<Transformation> TRANSFORMATIONS =
      new ClassValue<>() {
        @Override
        protected Transformation computeValue(Class<?> type) {
          return new Transformation();
        }
      };

  @SuppressWarnings("unchecked")
  public <T extends ConstraintProvider> Class<T> buildNodeSharedConstraintProvider(
      Class<T> constraintProviderClass) {
    var transformation = TRANSFORMATIONS.get(constraintProviderClass);
    synchronized (transformation) {
      if (transformation.result == null) {
        NodeSharingValidator.validate(constraintProviderClass);
        try {
          transformation.result = new NodeSharingTransformer(constraintProviderClass).transform();
        } catch (IllegalArgumentException | IllegalStateException e) {
          throw e;
        } catch (ReflectiveOperationException | LinkageError e) {
          throw new IllegalStateException(
              "Failed to create node-shared ConstraintProvider for "
                  + constraintProviderClass.getName()
                  + ". Correct the reported provider or module-access problem, or disable "
                  + "constraintStreamAutomaticNodeSharing. Cause: "
                  + e.getMessage(),
              e);
        }
      }
      return (Class<T>) transformation.result;
    }
  }

  private static final class Transformation {
    private Class<?> result;
  }
}
