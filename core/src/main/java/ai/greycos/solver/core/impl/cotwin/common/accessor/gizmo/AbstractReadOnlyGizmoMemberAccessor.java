package ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo;

public abstract class AbstractReadOnlyGizmoMemberAccessor extends AbstractGizmoMemberAccessor {

  @Override
  public final boolean supportSetter() {
    return false;
  }

  @Override
  public final void executeSetter(Object bean, Object value) {
    throw new UnsupportedOperationException("Setter not supported");
  }
}
