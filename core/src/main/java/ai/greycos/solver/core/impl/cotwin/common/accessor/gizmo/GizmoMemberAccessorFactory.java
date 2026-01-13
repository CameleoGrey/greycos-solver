package ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.util.Objects;

import ai.greycos.solver.core.api.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.ReflectionHelper;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;

public class GizmoMemberAccessorFactory {
  /**
   * Returns the generated class name for a given member. (Here as accessing any method of
   * GizmoMemberAccessorImplementor will try to load Gizmo code)
   *
   * @param member The member to get the generated class name for
   * @return The generated class name for member
   */
  public static String getGeneratedClassName(Member member) {
    String memberName =
        Objects.requireNonNullElse(
            ReflectionHelper.getGetterPropertyName(member), member.getName());
    String memberType = (member instanceof Field) ? "Field" : "Method";

    return member.getDeclaringClass().getName()
        + "$GreyCOS$MemberAccessor$"
        + memberType
        + "$"
        + memberName;
  }

  /**
   * @param member never null
   * @param annotationClass may be null if the member is not annotated
   * @param gizmoClassLoader never null
   * @param accessorInfo additional information of the accessor
   * @return never null
   */
  public static MemberAccessor buildGizmoMemberAccessor(
      Member member,
      Class<? extends Annotation> annotationClass,
      AccessorInfo accessorInfo,
      GizmoClassLoader gizmoClassLoader) {
    try {
      // Check if Gizmo on the classpath by verifying we can access one of its classes
      Class.forName(
          "io.quarkus.gizmo2.Gizmo", false, Thread.currentThread().getContextClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          """
                    When using the cotwinAccessType (%s) the classpath or modulepath must contain io.quarkus.gizmo:gizmo2.
                    Maybe add a dependency to io.quarkus.gizmo:gizmo2."""
              .formatted(CotwinAccessType.GIZMO));
    }
    return GizmoMemberAccessorImplementor.createAccessorFor(
        member, annotationClass, accessorInfo, gizmoClassLoader);
  }

  private GizmoMemberAccessorFactory() {}
}
