package ai.greycos.solver.quarkus.gizmo;

public interface GreyCOSGizmoBeanFactory {
  <T> T newInstance(Class<T> clazz);
}
