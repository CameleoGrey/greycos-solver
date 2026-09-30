package greycos.solver.core.impl.constructionheuristic.nearby;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;

/**
 * The declared argument types of a distance meter. This restricts existing selector contracts; it
 * does not establish whether the meter supports a new selector family or destination role.
 */
public final class NearbyDistanceMeterContract {

  private static final ClassValue<NearbyDistanceMeterContract> CONTRACTS =
      new ClassValue<>() {
        @Override
        protected NearbyDistanceMeterContract computeValue(Class<?> meterClass) {
          var arguments = resolveInterface(meterClass, Map.of());
          if (arguments == null) {
            throw new IllegalArgumentException(
                "The nearbyDistanceMeterClass (%s) does not implement %s."
                    .formatted(meterClass.getName(), NearbyDistanceMeter.class.getName()));
          }
          var originType = argumentClass(arguments.origin());
          var destinationType = argumentClass(arguments.destination());
          if (originType == Object.class && destinationType == Object.class) {
            // Raw/generic erasure may still have one concrete implementation with narrower
            // parameters. An unrelated overload must never broaden or select that contract.
            var methods =
                Arrays.stream(meterClass.getMethods())
                    .filter(
                        method ->
                            method.getName().equals("getNearbyDistance")
                                && !method.isBridge()
                                && !method.isSynthetic()
                                && !Modifier.isStatic(method.getModifiers())
                                && !Modifier.isAbstract(method.getModifiers())
                                && method.getParameterCount() == 2
                                && method.getReturnType() == double.class)
                    .toList();
            if (methods.size() == 1) {
              var parameters = methods.getFirst().getParameterTypes();
              originType = parameters[0];
              destinationType = parameters[1];
            }
          }
          return new NearbyDistanceMeterContract(originType, destinationType);
        }
      };

  private final Class<?> originType;
  private final Class<?> destinationType;

  private NearbyDistanceMeterContract(Class<?> originType, Class<?> destinationType) {
    this.originType = originType;
    this.destinationType = destinationType;
  }

  public static NearbyDistanceMeterContract of(
      Class<? extends NearbyDistanceMeter<?, ?>> distanceMeterClass) {
    return CONTRACTS.get(Objects.requireNonNull(distanceMeterClass));
  }

  public Class<?> originType() {
    return originType;
  }

  public Class<?> destinationType() {
    return destinationType;
  }

  public boolean accepts(Object origin, Object destination) {
    return (origin == null || originType.isInstance(origin))
        && (destination == null || destinationType.isInstance(destination));
  }

  /**
   * A declared base type may contain supported subclasses; actual candidates still need a guard.
   */
  public boolean canApplyTo(Class<?> originType, Class<?> destinationType) {
    return mayShareRuntimeType(this.originType, originType)
        && mayShareRuntimeType(this.destinationType, destinationType);
  }

  /**
   * Whether the types can have a common runtime subtype. Open interfaces and non-final classes may
   * overlap without either declared type being assignable to the other. This is only a metadata
   * test; {@link #accepts(Object, Object)} still guards each actual meter invocation.
   */
  static boolean mayShareRuntimeType(Class<?> first, Class<?> second) {
    if (first.isAssignableFrom(second) || second.isAssignableFrom(first)) {
      return true;
    }
    if (first.isPrimitive() || second.isPrimitive()) {
      return false;
    }
    if (first.isArray() && second.isArray()) {
      return mayShareRuntimeType(first.getComponentType(), second.getComponentType());
    }
    if (first.isArray() || second.isArray()) {
      // Object, Cloneable and Serializable were already handled by assignability.
      return false;
    }
    if (!first.isInterface() && !second.isInterface()) {
      // Java classes have only one superclass.
      return false;
    }
    if (first.isSealed()) {
      return Arrays.stream(first.getPermittedSubclasses())
          .anyMatch(permitted -> mayShareRuntimeType(permitted, second));
    }
    if (second.isSealed()) {
      return Arrays.stream(second.getPermittedSubclasses())
          .anyMatch(permitted -> mayShareRuntimeType(first, permitted));
    }
    return first.isInterface() && second.isInterface()
        || !Modifier.isFinal((first.isInterface() ? second : first).getModifiers());
  }

  private static Arguments resolveInterface(
      Type type, Map<TypeVariable<?>, Type> inheritedBindings) {
    Class<?> rawClass;
    var bindings = new HashMap<>(inheritedBindings);
    if (type instanceof ParameterizedType parameterizedType) {
      rawClass = (Class<?>) parameterizedType.getRawType();
      var parameters = rawClass.getTypeParameters();
      var arguments = parameterizedType.getActualTypeArguments();
      for (int index = 0; index < parameters.length; index++) {
        bindings.put(parameters[index], resolveVariable(arguments[index], inheritedBindings));
      }
    } else if (type instanceof Class<?> classType) {
      rawClass = classType;
    } else {
      return null;
    }
    if (rawClass == NearbyDistanceMeter.class) {
      var parameters = rawClass.getTypeParameters();
      return new Arguments(
          resolveVariable(parameters[0], bindings), resolveVariable(parameters[1], bindings));
    }
    for (var interfaceType : rawClass.getGenericInterfaces()) {
      var arguments = resolveInterface(interfaceType, bindings);
      if (arguments != null) {
        return arguments;
      }
    }
    var superclass = rawClass.getGenericSuperclass();
    return superclass == null ? null : resolveInterface(superclass, bindings);
  }

  private static Type resolveVariable(Type type, Map<TypeVariable<?>, Type> bindings) {
    var visited = new HashSet<TypeVariable<?>>();
    while (type instanceof TypeVariable<?> variable && visited.add(variable)) {
      var bound = bindings.get(variable);
      if (bound == null) {
        var declaredBounds = variable.getBounds();
        type = declaredBounds.length == 0 ? Object.class : declaredBounds[0];
        continue;
      }
      type = bound;
    }
    if (type instanceof GenericArrayType arrayType) {
      return argumentClass(resolveVariable(arrayType.getGenericComponentType(), bindings))
          .arrayType();
    }
    return type;
  }

  private static Class<?> argumentClass(Type type) {
    if (type instanceof Class<?> classType) {
      return classType;
    } else if (type instanceof ParameterizedType parameterizedType) {
      return (Class<?>) parameterizedType.getRawType();
    } else if (type instanceof GenericArrayType arrayType) {
      return argumentClass(arrayType.getGenericComponentType()).arrayType();
    } else if (type instanceof WildcardType wildcardType) {
      return argumentClass(wildcardType.getUpperBounds()[0]);
    }
    return Object.class;
  }

  private record Arguments(Type origin, Type destination) {}
}
