package ai.greycos.solver.core.impl.nodesharing;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * Class loader that defines transformed ConstraintProvider classes with node sharing enabled.
 *
 * <p>The transformed class has the same name as the original class, but with lambda fields added
 * and lambda creation replaced with field references. The class is loaded in a separate class
 * loader to avoid conflicts with the original class.
 *
 * <p>Uses the system class loader as parent to ensure access to all application classes.
 */
public final class NodeSharedClassLoader extends ClassLoader {

  // Cache to avoid duplicate class definitions
  private final java.util.Map<String, Class<?>> classCache = new java.util.HashMap<>();

  public NodeSharedClassLoader() {
    // Use system class loader as parent to ensure access to all application classes
    super(ClassLoader.getSystemClassLoader());
  }

  /**
   * Defines and loads a transformed ConstraintProvider class.
   *
   * <p>The transformed bytecode has the same class name as the original. To avoid conflicts, we
   * define the class directly in this class loader, which will delegate to parent (system class
   * loader) for loading referenced classes.
   *
   * @param <T> ConstraintProvider type
   * @param originalClass original ConstraintProvider class
   * @param transformedBytecode transformed bytecode (same class name as original)
   * @return transformed class instance
   */
  @SuppressWarnings("unchecked")
  public <T extends ConstraintProvider> Class<T> defineNodeSharedClass(
      Class<T> originalClass, byte[] transformedBytecode) {

    String originalClassName = originalClass.getName();

    try {
      // Check cache first to avoid duplicate definitions
      Class<?> cachedClass = classCache.get(originalClassName);
      if (cachedClass != null) {
        return (Class<T>) cachedClass;
      }

      // Define the transformed class in this class loader
      // The class loader will delegate to parent (system class loader) for loading referenced
      // classes
      Class<?> definedClass =
          defineClass(originalClassName, transformedBytecode, 0, transformedBytecode.length);
      classCache.put(originalClassName, definedClass);
      return (Class<T>) definedClass;
    } catch (LinkageError e) {
      throw new IllegalStateException(
          "Failed to define node-shared class for " + originalClassName, e);
    }
  }
}
