package ai.greycos.solver.quarkus.gizmo;

public interface GreycosGizmoBeanFactory {
  <T> T newInstance(Class<T> clazz);
}
