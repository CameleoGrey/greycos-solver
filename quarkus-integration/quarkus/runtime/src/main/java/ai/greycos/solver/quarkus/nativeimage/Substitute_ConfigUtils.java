package ai.greycos.solver.quarkus.nativeimage;

import java.util.function.Supplier;

import jakarta.enterprise.inject.spi.CDI;

import ai.greycos.solver.quarkus.gizmo.GreycosGizmoBeanFactory;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "ai.greycos.solver.core.config.util.ConfigUtils")
public final class Substitute_ConfigUtils {

  @Substitute
  public static <T> T newInstance(
      Supplier<String> ownerDescriptor, String propertyName, Class<T> clazz) {
    T out =
        CDI.current()
            .getBeanManager()
            .createInstance()
            .select(GreycosGizmoBeanFactory.class)
            .get()
            .newInstance(clazz);
    if (out != null) {
      return out;
    } else {
      throw new IllegalArgumentException(
          "Impossible state: could not find the "
              + ownerDescriptor.get()
              + "'s "
              + propertyName
              + " ("
              + clazz.getName()
              + ") generated Gizmo supplier.");
    }
  }
}
