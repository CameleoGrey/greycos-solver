package ai.greycos.solver.core.impl.nodesharing;

import java.util.HashMap;
import java.util.Map;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

/**
 * Loads transformed ConstraintProvider classes with node sharing enabled.
 *
 * <p>Why: Must load transformed bytecode without conflicting with original class. How: Uses
 * separate class loader with system class loader as parent. What: Defines classes with same name
 * but different bytecode (shared lambda fields).
 */
public final class NodeSharedClassLoader extends ClassLoader {

  private final Map<String, Class<?>> classCache = new HashMap<>();

  public NodeSharedClassLoader() {
    super(ClassLoader.getSystemClassLoader());
  }

  @SuppressWarnings("unchecked")
  public <T extends ConstraintProvider> Class<T> defineNodeSharedClass(
      Class<T> originalClass, byte[] transformedBytecode) {

    String originalClassName = originalClass.getName();

    try {
      Class<?> cachedClass = classCache.get(originalClassName);
      if (cachedClass != null) {
        return (Class<T>) cachedClass;
      }

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
