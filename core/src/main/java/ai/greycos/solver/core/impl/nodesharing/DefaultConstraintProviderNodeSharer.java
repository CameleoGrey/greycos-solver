package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of ConstraintProviderNodeSharer for community edition.
 *
 * <p>Transforms ConstraintProvider classes to enable automatic node sharing using ASM bytecode
 * manipulation.
 */
public final class DefaultConstraintProviderNodeSharer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultConstraintProviderNodeSharer.class);

  private final NodeSharedClassLoader classLoader;

  public DefaultConstraintProviderNodeSharer() {
    // NodeSharedClassLoader uses system class loader as parent by default
    this.classLoader = new NodeSharedClassLoader();
  }

  public <T extends ConstraintProvider> Class<T> buildNodeSharedConstraintProvider(
      Class<T> constraintProviderClass) {

    LOGGER.debug("Starting node sharing transformation for {}", constraintProviderClass.getName());
    try {
      // Validate class meets requirements
      NodeSharingValidator.validate(constraintProviderClass);
      LOGGER.debug("Validation passed for {}", constraintProviderClass.getName());

      // Transform class bytecode
      NodeSharingTransformer transformer = new NodeSharingTransformer(constraintProviderClass);
      byte[] transformedBytecode = transformer.transform();
      LOGGER.debug("Bytecode transformation completed for {}", constraintProviderClass.getName());

      // Define and load transformed class
      Class<T> transformedClass =
          classLoader.defineNodeSharedClass(constraintProviderClass, transformedBytecode);
      LOGGER.info(
          "Successfully created node-shared ConstraintProvider: {} -> {}",
          constraintProviderClass.getSimpleName(),
          transformedClass.getSimpleName());

      return transformedClass;

    } catch (IllegalArgumentException e) {
      // Validation failed - rethrow as-is
      LOGGER.warn(
          "Validation failed for {}: {}", constraintProviderClass.getName(), e.getMessage());
      throw e;
    } catch (Exception e) {
      // Transformation failed - throw with helpful message
      LOGGER.error(
          "Node sharing transformation failed for {}: {}",
          constraintProviderClass.getName(),
          e.getMessage(),
          e);
      throw new IllegalStateException(
          "Failed to create node-shared ConstraintProvider for "
              + constraintProviderClass.getName()
              + ". Falling back to original class. Error: "
              + e.getMessage(),
          e);
    }
  }
}
