package ai.greycos.solver.core.impl.cotwin.common.accessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.ReflectionHelper;
import ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo.AccessorInfo;
import ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo.GizmoClassLoader;
import ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo.GizmoMemberAccessorFactory;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class MemberAccessorFactory {

  static final Logger LOGGER = LoggerFactory.getLogger(MemberAccessorFactory.class);
  static final String CLASSLOADER_NUDGE_MESSAGE =
      "Maybe add getClass().getClassLoader() as a parameter to the %s.create...() method call."
          .formatted(SolverFactory.class.getSimpleName());

  private static MemberAccessor buildMemberAccessor(
      Member member,
      MemberAccessorType memberAccessorType,
      CotwinAccessType cotwinAccessType,
      ClassLoader classLoader) {
    return buildMemberAccessor(member, memberAccessorType, null, cotwinAccessType, classLoader);
  }

  static MemberAccessor buildMemberAccessor(
      Member member,
      MemberAccessorType memberAccessorType,
      @Nullable Class<? extends Annotation> annotationClass,
      CotwinAccessType cotwinAccessType,
      ClassLoader classLoader) {
    MemberAccessorValidator.verifyIsValidMember(annotationClass, member, memberAccessorType);
    return switch (cotwinAccessType) {
      case AUTO ->
          throw new IllegalStateException(
              "Impossible state: called with %s (AUTO) instead of a resolved cotwin access type"
                  .formatted(CotwinAccessType.class.getSimpleName()));
      case FORCE_GIZMO ->
          GizmoMemberAccessorFactory.buildGizmoMemberAccessor(
              member,
              annotationClass,
              AccessorInfo.of(memberAccessorType),
              (GizmoClassLoader) Objects.requireNonNull(classLoader));
      case FORCE_REFLECTION ->
          buildReflectiveMemberAccessor(member, memberAccessorType, annotationClass);
    };
  }

  private static MemberAccessor buildReflectiveMemberAccessor(
      Member member,
      MemberAccessorType memberAccessorType,
      @Nullable Class<? extends Annotation> annotationClass) {
    return buildReflectiveMemberAccessor(
        member, memberAccessorType, annotationClass, (AnnotatedElement) member);
  }

  private static MemberAccessor buildReflectiveMemberAccessor(
      Member member,
      MemberAccessorType memberAccessorType,
      @Nullable Class<? extends Annotation> annotationClass,
      AnnotatedElement annotatedElement) {
    var messagePrefix =
        (annotationClass == null)
            ? "The"
            : "The @%s annotated".formatted(annotationClass.getSimpleName());
    if (member instanceof Field field) {
      var getter = ReflectionHelper.getGetterMethod(field.getDeclaringClass(), field.getName());
      if (getter == null) {
        var setter =
            ReflectionHelper.getSetterMethod(
                field.getDeclaringClass(), field.getType(), field.getName());
        if (setter != null) {
          throw new IllegalArgumentException(
              "%s field (%s) on class (%s) has a setter (%s) but no getter."
                  .formatted(
                      messagePrefix,
                      field.getName(),
                      field.getDeclaringClass().getCanonicalName(),
                      setter));
        }
        if (Modifier.isFinal(field.getModifiers()) && memberAccessorType.isSetterRequired()) {
          throw new IllegalArgumentException(
              "%s field (%s) on class (%s) is final but requires a setter."
                  .formatted(
                      messagePrefix,
                      field.getName(),
                      field.getDeclaringClass().getCanonicalName()));
        }
        if (Modifier.isPublic(field.getModifiers())) {
          return new ReflectionFieldMemberAccessor(field);
        } else {
          throw new IllegalArgumentException(
              "%s field (%s) on class (%s) is not public and does not have a public getter method."
                  .formatted(messagePrefix, field.getName(), field.getDeclaringClass().getName()));
        }
      }
      return buildReflectiveMemberAccessor(getter, memberAccessorType, annotationClass, field);
    } else if (member instanceof Method method) {
      MemberAccessor memberAccessor;
      if (!Modifier.isPublic(method.getModifiers())) {
        throw new IllegalStateException(
            "%s method (%s) on class (%s) is not public."
                .formatted(messagePrefix, method.getName(), method.getDeclaringClass().getName()));
      }
      switch (memberAccessorType) {
        case FIELD_OR_READ_METHOD, FIELD_OR_READ_METHOD_WITH_OPTIONAL_PARAMETER:
          if (!ReflectionHelper.isGetterMethod(method)) {
            boolean methodWithParameter =
                memberAccessorType
                        == MemberAccessorType.FIELD_OR_READ_METHOD_WITH_OPTIONAL_PARAMETER
                    && method.getParameterCount() > 0;
            if (annotationClass == null) {
              ReflectionHelper.assertReadMethod(method, methodWithParameter);
            } else {
              ReflectionHelper.assertReadMethod(method, methodWithParameter, annotationClass);
            }
            memberAccessor =
                methodWithParameter
                    ? new ReflectionMethodExtendedMemberAccessor(method)
                    : new ReflectionMethodMemberAccessor(method);
            break;
          }
        case FIELD_OR_GETTER_METHOD, FIELD_OR_GETTER_METHOD_WITH_SETTER:
          boolean getterOnly = !memberAccessorType.isSetterRequired();
          if (annotationClass == null) {
            ReflectionHelper.assertGetterMethod(method);
          } else {
            ReflectionHelper.assertGetterMethod(method, annotationClass);
          }
          memberAccessor =
              new ReflectionBeanPropertyMemberAccessor(method, annotatedElement, getterOnly);
          break;
        case VOID_METHOD:
          memberAccessor = new ReflectionMethodMemberAccessor(method);
          break;
        default:
          throw new IllegalStateException(
              "The memberAccessorType (%s) is not implemented.".formatted(memberAccessorType));
      }
      if (memberAccessorType == MemberAccessorType.FIELD_OR_GETTER_METHOD_WITH_SETTER
          && !memberAccessor.supportSetter()) {
        if (annotationClass == null) {
          throw new IllegalStateException(
              "The class (%s) has a getter method (%s), but lacks a setter for that property (%s)."
                  .formatted(method.getDeclaringClass(), method, memberAccessor.getName()));
        } else {
          throw new IllegalStateException(
              "The class (%s) has a @%s-annotated getter method (%s), but lacks a setter for that property (%s)."
                  .formatted(
                      method.getDeclaringClass(),
                      annotationClass.getSimpleName(),
                      method,
                      memberAccessor.getName()));
        }
      }
      return memberAccessor;
    } else {
      throw new IllegalStateException(
          "Impossible state: the member (%s)'s type is not a %s or a %s."
              .formatted(member, Field.class.getSimpleName(), Method.class.getSimpleName()));
    }
  }

  private final Map<String, MemberAccessor> memberAccessorCache;
  private final GizmoClassLoader gizmoClassLoader = new GizmoClassLoader();
  private final boolean isGizmoSupported;

  public MemberAccessorFactory() {
    this(null);
  }

  public MemberAccessorFactory(@Nullable Map<String, MemberAccessor> memberAccessorMap) {
    this.memberAccessorCache =
        memberAccessorMap == null
            ? new ConcurrentHashMap<>()
            : new ConcurrentHashMap<>(memberAccessorMap);
    this.isGizmoSupported =
        (memberAccessorMap != null && !memberAccessorMap.isEmpty())
            || gizmoClassLoader.isGizmoSupported();
    LOGGER.trace(
        "Using cotwin access type {} for member accessors.",
        isGizmoSupported ? CotwinAccessType.FORCE_GIZMO : CotwinAccessType.FORCE_REFLECTION);
  }

  public MemberAccessor buildAndCacheMemberAccessor(
      Member member,
      MemberAccessorType memberAccessorType,
      @Nullable Class<? extends Annotation> annotationClass,
      CotwinAccessType cotwinAccessType) {
    String generatedClassName = GizmoMemberAccessorFactory.getGeneratedClassName(member);
    if (cotwinAccessType == CotwinAccessType.AUTO) {
      cotwinAccessType =
          isGizmoSupported ? CotwinAccessType.FORCE_GIZMO : CotwinAccessType.FORCE_REFLECTION;
    }
    var finalCotwinAccessType = cotwinAccessType;
    return memberAccessorCache.computeIfAbsent(
        generatedClassName,
        k ->
            MemberAccessorFactory.buildMemberAccessor(
                member,
                memberAccessorType,
                annotationClass,
                finalCotwinAccessType,
                gizmoClassLoader));
  }

  public MemberAccessor buildAndCacheMemberAccessor(
      Member member, MemberAccessorType memberAccessorType, CotwinAccessType cotwinAccessType) {
    String generatedClassName = GizmoMemberAccessorFactory.getGeneratedClassName(member);
    if (cotwinAccessType == CotwinAccessType.AUTO) {
      cotwinAccessType =
          isGizmoSupported ? CotwinAccessType.FORCE_GIZMO : CotwinAccessType.FORCE_REFLECTION;
    }
    var finalCotwinAccessType = cotwinAccessType;
    return memberAccessorCache.computeIfAbsent(
        generatedClassName,
        k ->
            MemberAccessorFactory.buildMemberAccessor(
                member, memberAccessorType, finalCotwinAccessType, gizmoClassLoader));
  }

  public GizmoClassLoader getGizmoClassLoader() {
    return gizmoClassLoader;
  }
}
