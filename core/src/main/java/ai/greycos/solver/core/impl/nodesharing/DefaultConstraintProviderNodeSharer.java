package ai.greycos.solver.core.impl.nodesharing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms ConstraintProvider classes to enable automatic lambda node sharing.
 *
 * <p>Why: Sharing identical lambdas across constraint streams reduces memory and improves
 * performance. How: Validates class requirements, transforms bytecode using ASM, loads transformed
 * class. What: Public API for creating node-shared ConstraintProvider instances.
 */
public final class DefaultConstraintProviderNodeSharer {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultConstraintProviderNodeSharer.class);

  private static final Object BOOTSTRAP_CLASS_LOADER_KEY = new Object();

  private final Map<Object, NodeSharedClassLoader> classLoaderMap;

  public DefaultConstraintProviderNodeSharer() {
    this.classLoaderMap = new ConcurrentHashMap<>();
  }

  public <T extends ConstraintProvider> Class<T> buildNodeSharedConstraintProvider(
      Class<T> constraintProviderClass) {

    LOGGER.debug("Starting node sharing transformation for {}", constraintProviderClass.getName());
    try {
      NodeSharingValidator.validate(constraintProviderClass);
      LOGGER.debug("Validation passed for {}", constraintProviderClass.getName());

      NodeSharingTransformer transformer = new NodeSharingTransformer(constraintProviderClass);
      byte[] transformedBytecode = transformer.transform();
      LOGGER.debug("Bytecode transformation completed for {}", constraintProviderClass.getName());

      Class<T> transformedClass =
          getClassLoader(constraintProviderClass)
              .defineNodeSharedClass(constraintProviderClass, transformedBytecode);
      LOGGER.info(
          "Successfully created node-shared ConstraintProvider: {} -> {}",
          constraintProviderClass.getSimpleName(),
          transformedClass.getSimpleName());

      return transformedClass;

    } catch (IllegalArgumentException e) {
      LOGGER.warn(
          "Validation failed for {}: {}", constraintProviderClass.getName(), e.getMessage());
      throw e;
    } catch (Exception e) {
      LOGGER.error(
          "Node sharing transformation failed for {}: {}",
          constraintProviderClass.getName(),
          e.getMessage(),
          e);
      throw new IllegalStateException(
          "Failed to create node-shared ConstraintProvider for "
              + constraintProviderClass.getName()
              + ". Error: "
              + e.getMessage(),
          e);
    }
  }

  private NodeSharedClassLoader getClassLoader(Class<?> constraintProviderClass) {
    ClassLoader parentClassLoader = constraintProviderClass.getClassLoader();
    Object cacheKey = parentClassLoader == null ? BOOTSTRAP_CLASS_LOADER_KEY : parentClassLoader;
    return classLoaderMap.computeIfAbsent(
        cacheKey, ignored -> new NodeSharedClassLoader(parentClassLoader));
  }
}
