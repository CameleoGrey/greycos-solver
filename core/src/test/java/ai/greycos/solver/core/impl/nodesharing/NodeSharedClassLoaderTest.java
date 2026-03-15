package ai.greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import ai.greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

class NodeSharedClassLoaderTest {

  @Test
  void defineNodeSharedClassReturnsClass() throws IOException {
    NodeSharedClassLoader classLoader =
        new NodeSharedClassLoader(SimpleConstraintProvider.class.getClassLoader());

    // Use actual bytecode from the class file
    byte[] bytecode = readClassFile(SimpleConstraintProvider.class);

    Class<? extends ConstraintProvider> result =
        classLoader.defineNodeSharedClass(SimpleConstraintProvider.class, bytecode);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(SimpleConstraintProvider.class.getName());
  }

  @Test
  void defineNodeSharedClassCaching() throws IOException {
    NodeSharedClassLoader classLoader =
        new NodeSharedClassLoader(SimpleConstraintProvider.class.getClassLoader());

    byte[] bytecode = readClassFile(SimpleConstraintProvider.class);

    Class<? extends ConstraintProvider> result1 =
        classLoader.defineNodeSharedClass(SimpleConstraintProvider.class, bytecode);

    Class<? extends ConstraintProvider> result2 =
        classLoader.defineNodeSharedClass(SimpleConstraintProvider.class, bytecode);

    assertThat(result1).isSameAs(result2);
  }

  @Test
  void defineNodeSharedClassDifferentClasses() throws IOException {
    NodeSharedClassLoader classLoader =
        new NodeSharedClassLoader(SimpleConstraintProvider.class.getClassLoader());

    byte[] bytecode1 = readClassFile(SimpleConstraintProvider.class);
    byte[] bytecode2 = readClassFile(ComplexConstraintProvider.class);

    Class<? extends ConstraintProvider> result1 =
        classLoader.defineNodeSharedClass(SimpleConstraintProvider.class, bytecode1);

    Class<? extends ConstraintProvider> result2 =
        classLoader.defineNodeSharedClass(ComplexConstraintProvider.class, bytecode2);

    assertThat(result1).isNotSameAs(result2);
    assertThat(result1.getName()).isEqualTo(SimpleConstraintProvider.class.getName());
    assertThat(result2.getName()).isEqualTo(ComplexConstraintProvider.class.getName());
  }

  @Test
  void defineNodeSharedClassKeepsConfiguredParentClassLoader() throws IOException {
    ClassLoader parent = new ClassLoader(SimpleConstraintProvider.class.getClassLoader()) {};
    NodeSharedClassLoader classLoader = new NodeSharedClassLoader(parent);
    byte[] bytecode = readClassFile(SimpleConstraintProvider.class);

    Class<? extends ConstraintProvider> result =
        classLoader.defineNodeSharedClass(SimpleConstraintProvider.class, bytecode);

    assertThat(result.getClassLoader().getParent()).isSameAs(parent);
  }

  private byte[] readClassFile(Class<?> clazz) throws IOException {
    String className = clazz.getName().replace('.', '/');
    String classFileName = className + ".class";

    try (InputStream is = clazz.getClassLoader().getResourceAsStream(classFileName)) {
      if (is == null) {
        throw new IllegalStateException("Cannot find class file for " + clazz.getName());
      }
      return is.readAllBytes();
    }
  }
}
