package ai.greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Validates ConstraintProvider classes for automatic node sharing compatibility.
 *
 * <p>Why: Node sharing requires bytecode transformation, which has prerequisites. How: Validates
 * class is not final, has no final methods, and avoids non-public external access. What: Ensures
 * transformation can succeed before attempting bytecode modification.
 */
public final class NodeSharingValidator {

  public static void validate(Class<?> constraintProviderClass) {
    if (Modifier.isFinal(constraintProviderClass.getModifiers())) {
      throw new IllegalArgumentException(
          "ConstraintProvider class %s must not be final for automatic node sharing."
              .formatted(constraintProviderClass.getName()));
    }

    validateNoFinalMethods(constraintProviderClass);
    validateNoNonPublicExternalAccess(constraintProviderClass);
  }

  private static void validateNoFinalMethods(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (Modifier.isFinal(method.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider method %s.%s must not be final for automatic node sharing."
                .formatted(clazz.getName(), method.getName()));
      }
    }
  }

  private static void validateNoNonPublicExternalAccess(Class<?> constraintProviderClass) {
    String classFileName = constraintProviderClass.getName().replace('.', '/') + ".class";
    try (InputStream is =
        constraintProviderClass.getClassLoader().getResourceAsStream(classFileName)) {
      if (is == null) {
        throw new IllegalStateException(
            "Cannot find class file for " + constraintProviderClass.getName());
      }
      ClassReader reader = new ClassReader(is.readAllBytes());
      reader.accept(
          new NonPublicAccessVisitor(constraintProviderClass),
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to read class file for " + constraintProviderClass.getName(), e);
    }
  }

  private static final class NonPublicAccessVisitor extends ClassVisitor {

    private final Class<?> constraintProviderClass;
    private final String providerInternalName;

    private NonPublicAccessVisitor(Class<?> constraintProviderClass) {
      super(Opcodes.ASM9);
      this.constraintProviderClass = constraintProviderClass;
      this.providerInternalName = constraintProviderClass.getName().replace('.', '/');
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      validateTypeDescriptor(descriptor);
      return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      validateMethodDescriptor(descriptor);
      return new MethodVisitor(Opcodes.ASM9) {
        @Override
        public void visitTypeInsn(int opcode, String type) {
          validateReferencedType(type);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
          validateReferencedField(owner, name);
          validateTypeDescriptor(descriptor);
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String name, String descriptor, boolean isInterface) {
          validateReferencedMethod(owner, name, descriptor);
          validateMethodDescriptor(descriptor);
        }

        @Override
        public void visitLdcInsn(Object value) {
          if (value instanceof Type type) {
            validateType(type);
          }
        }

        @Override
        public void visitInvokeDynamicInsn(
            String name,
            String descriptor,
            Handle bootstrapMethodHandle,
            Object... bootstrapMethodArguments) {
          validateMethodDescriptor(descriptor);
          validateHandle(bootstrapMethodHandle);
          for (Object argument : bootstrapMethodArguments) {
            if (argument instanceof Type type) {
              validateType(type);
            } else if (argument instanceof Handle handle) {
              validateHandle(handle);
            }
          }
        }
      };
    }

    private void validateReferencedType(String internalName) {
      validateType(Type.getObjectType(internalName));
    }

    private void validateMethodDescriptor(String descriptor) {
      for (Type argumentType : Type.getArgumentTypes(descriptor)) {
        validateType(argumentType);
      }
      validateType(Type.getReturnType(descriptor));
    }

    private void validateTypeDescriptor(String descriptor) {
      validateType(Type.getType(descriptor));
    }

    private void validateType(Type type) {
      switch (type.getSort()) {
        case Type.ARRAY -> validateType(type.getElementType());
        case Type.OBJECT -> {
          Class<?> referencedClass = resolveClass(type.getClassName());
          if (referencedClass != null
              && referencedClass != constraintProviderClass
              && !Modifier.isPublic(referencedClass.getModifiers())) {
            throw new IllegalArgumentException(
                "ConstraintProvider class %s must not access non-public class %s for automatic node sharing."
                    .formatted(constraintProviderClass.getName(), referencedClass.getName()));
          }
        }
        default -> {
          // Primitive and void types are always safe.
        }
      }
    }

    private void validateReferencedField(String ownerInternalName, String fieldName) {
      if (providerInternalName.equals(ownerInternalName)) {
        return;
      }
      Class<?> ownerClass = resolveInternalName(ownerInternalName);
      if (ownerClass == null) {
        return;
      }
      if (!Modifier.isPublic(ownerClass.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider class %s must not access non-public class %s for automatic node sharing."
                .formatted(constraintProviderClass.getName(), ownerClass.getName()));
      }
      var field = ReflectionResolver.findField(ownerClass, fieldName);
      if (field != null && !Modifier.isPublic(field.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider class %s must not access non-public field %s.%s for automatic node sharing."
                .formatted(constraintProviderClass.getName(), ownerClass.getName(), fieldName));
      }
    }

    private void validateReferencedMethod(
        String ownerInternalName, String methodName, String descriptor) {
      if (providerInternalName.equals(ownerInternalName)) {
        return;
      }
      Class<?> ownerClass = resolveInternalName(ownerInternalName);
      if (ownerClass == null) {
        return;
      }
      if (!Modifier.isPublic(ownerClass.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider class %s must not access non-public class %s for automatic node sharing."
                .formatted(constraintProviderClass.getName(), ownerClass.getName()));
      }
      Executable executable =
          "<init>".equals(methodName)
              ? ReflectionResolver.findConstructor(ownerClass, descriptor)
              : ReflectionResolver.findMethod(ownerClass, methodName, descriptor);
      if (executable != null && !Modifier.isPublic(executable.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider class %s must not access non-public method %s.%s for automatic node sharing."
                .formatted(constraintProviderClass.getName(), ownerClass.getName(), methodName));
      }
    }

    private void validateHandle(Handle handle) {
      switch (handle.getTag()) {
        case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
            validateReferencedField(handle.getOwner(), handle.getName());
        default -> validateReferencedMethod(handle.getOwner(), handle.getName(), handle.getDesc());
      }
    }

    private Class<?> resolveInternalName(String internalName) {
      return resolveClass(internalName.replace('/', '.'));
    }

    private Class<?> resolveClass(String className) {
      try {
        return Class.forName(className, false, constraintProviderClass.getClassLoader());
      } catch (ClassNotFoundException e) {
        return null;
      }
    }
  }

  private static final class ReflectionResolver {

    private static java.lang.reflect.Field findField(Class<?> ownerClass, String fieldName) {
      Class<?> current = ownerClass;
      while (current != null) {
        try {
          return current.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
          current = current.getSuperclass();
        }
      }
      return null;
    }

    private static java.lang.reflect.Executable findConstructor(
        Class<?> ownerClass, String descriptor) {
      Class<?>[] parameterTypes = resolveParameterTypes(ownerClass.getClassLoader(), descriptor);
      if (parameterTypes == null) {
        return null;
      }
      try {
        return ownerClass.getDeclaredConstructor(parameterTypes);
      } catch (NoSuchMethodException e) {
        return null;
      }
    }

    private static java.lang.reflect.Executable findMethod(
        Class<?> ownerClass, String methodName, String descriptor) {
      Class<?>[] parameterTypes = resolveParameterTypes(ownerClass.getClassLoader(), descriptor);
      if (parameterTypes == null) {
        return null;
      }
      Class<?> current = ownerClass;
      while (current != null) {
        try {
          return current.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
          for (Class<?> iface : current.getInterfaces()) {
            try {
              return iface.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignored) {
              // Continue searching.
            }
          }
          current = current.getSuperclass();
        }
      }
      return null;
    }

    private static Class<?>[] resolveParameterTypes(ClassLoader classLoader, String descriptor) {
      Type[] argumentTypes = Type.getArgumentTypes(descriptor);
      Class<?>[] parameterTypes = new Class<?>[argumentTypes.length];
      for (int i = 0; i < argumentTypes.length; i++) {
        Class<?> parameterType = resolveType(classLoader, argumentTypes[i]);
        if (parameterType == null) {
          return null;
        }
        parameterTypes[i] = parameterType;
      }
      return parameterTypes;
    }

    private static Class<?> resolveType(ClassLoader classLoader, Type type) {
      return switch (type.getSort()) {
        case Type.BOOLEAN -> boolean.class;
        case Type.BYTE -> byte.class;
        case Type.CHAR -> char.class;
        case Type.DOUBLE -> double.class;
        case Type.FLOAT -> float.class;
        case Type.INT -> int.class;
        case Type.LONG -> long.class;
        case Type.SHORT -> short.class;
        case Type.VOID -> void.class;
        default -> {
          try {
            yield Class.forName(type.getClassName(), false, classLoader);
          } catch (ClassNotFoundException e) {
            yield null;
          }
        }
      };
    }
  }

  private NodeSharingValidator() {}
}
