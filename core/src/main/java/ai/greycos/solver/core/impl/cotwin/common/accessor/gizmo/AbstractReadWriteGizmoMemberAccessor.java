package ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo;

public abstract class AbstractReadWriteGizmoMemberAccessor extends AbstractGizmoMemberAccessor {

  @Override
  public final boolean supportSetter() {
    return true;
  }
}
