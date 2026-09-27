package greycos.solver.core.impl.nodesharing;

import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the prerequisites for preserving provider behavior in a generated subclass. */
public final class NodeSharingValidator {

  public static void validate(Class<?> providerClass) {
    if (Modifier.isFinal(providerClass.getModifiers())) {
      throw new IllegalArgumentException(
          "ConstraintProvider class %s must not be final for automatic node sharing."
              .formatted(providerClass.getName()));
    }
    if (providerClass.isSealed()) {
      throw new IllegalArgumentException(
          ("ConstraintProvider class %s must not be sealed for automatic node sharing; "
                  + "the generated subclass must be permitted.")
              .formatted(providerClass.getName()));
    }
    for (Method method : providerClass.getDeclaredMethods()) {
      if (Modifier.isFinal(method.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider method %s.%s must not be final for automatic node sharing."
                .formatted(providerClass.getName(), method.getName()));
      }
    }
  }

  static void validateCopiedMethod(Class<?> providerClass, MethodNode method) {
    validateCopiedMethod(
        providerClass,
        method,
        new StackObservationAnalyzer(
            providerClass, NodeSharingTransformer.readClassFile(providerClass)));
  }

  static void validateCopiedMethod(
      Class<?> providerClass,
      MethodNode method,
      StackObservationAnalyzer stackObservationAnalyzer) {
    var stackObservation =
        stackObservationAnalyzer.inspectMethod(
            Type.getInternalName(providerClass), method.name, method.desc);
    if (stackObservation.observationPath() != null) {
      throw unsupported(
          providerClass, method, "stack inspection through " + stackObservation.observationPath());
    }
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof InvokeDynamicInsnNode dynamic) {
        String owner = dynamic.bsm.getOwner();
        String name = dynamic.bsm.getName();
        boolean lambda =
            owner.equals("java/lang/invoke/LambdaMetafactory")
                && (name.equals("metafactory") || name.equals("altMetafactory"));
        boolean concat =
            owner.equals("java/lang/invoke/StringConcatFactory")
                && (name.equals("makeConcat") || name.equals("makeConcatWithConstants"));
        if (!lambda && !concat) {
          throw unsupported(
              providerClass, method, "custom invokedynamic bootstrap " + owner + "." + name);
        }
        for (Object argument : dynamic.bsmArgs) {
          if (argument instanceof ConstantDynamic) {
            throw unsupported(providerClass, method, "dynamic bootstrap constants");
          } else if (argument instanceof Handle handle
              && LambdaImplementationCanonicalizer.inspectsStack(
                  handle.getOwner(), handle.getName(), handle.getDesc())) {
            throw unsupported(
                providerClass,
                method,
                "stack-inspecting method reference " + handle.getOwner() + "." + handle.getName());
          } else if (argument instanceof Handle handle
              && handle.getTag() >= Opcodes.H_INVOKEVIRTUAL
              && isCallerSensitive(
                  providerClass,
                  new MethodInsnNode(
                      Opcodes.INVOKESTATIC,
                      handle.getOwner(),
                      handle.getName(),
                      handle.getDesc(),
                      handle.isInterface()))) {
            throw unsupported(
                providerClass,
                method,
                "caller-sensitive method reference " + handle.getOwner() + "." + handle.getName());
          }
        }
      } else if (instruction instanceof LdcInsnNode constant
          && (constant.cst instanceof ConstantDynamic || constant.cst instanceof Handle)) {
        throw unsupported(providerClass, method, "dynamic constants or method-handle constants");
      } else if (instruction instanceof MethodInsnNode invocation) {
        if (LambdaImplementationCanonicalizer.inspectsStack(
            invocation.owner, invocation.name, invocation.desc)) {
          throw unsupported(
              providerClass,
              method,
              "stack inspection through " + invocation.owner + "." + invocation.name);
        }
        if (isCallerSensitive(providerClass, invocation)) {
          throw unsupported(
              providerClass,
              method,
              "caller-sensitive invocation " + invocation.owner + "." + invocation.name);
        }
      } else if (instruction.getOpcode() == Opcodes.JSR || instruction.getOpcode() == Opcodes.RET) {
        throw unsupported(providerClass, method, "legacy jsr/ret bytecode");
      }
    }
  }

  private static boolean isCallerSensitive(Class<?> providerClass, MethodInsnNode invocation) {
    if (invocation.name.equals("<init>")) {
      return false;
    }
    try {
      ClassLoader loader = providerClass.getClassLoader();
      Class<?> owner = Class.forName(invocation.owner.replace('/', '.'), false, loader);
      MethodType methodType = MethodType.fromMethodDescriptorString(invocation.desc, loader);
      Method target =
          findMethod(owner, invocation.name, methodType.parameterArray(), new HashSet<>());
      if (target == null) {
        // Signature-polymorphic MethodHandle calls have no reflected method with this descriptor.
        return false;
      }
      for (var annotation : target.getDeclaredAnnotations()) {
        String name = annotation.annotationType().getName();
        if (name.equals("jdk.internal.reflect.CallerSensitive")
            || name.equals("sun.reflect.CallerSensitive")) {
          return true;
        }
      }
      return false;
    } catch (ClassNotFoundException | TypeNotPresentException e) {
      throw new IllegalStateException(
          ("Cannot resolve method %s.%s%s while validating automatic node sharing for %s. "
                  + "Make its declaring class and signature types available to the provider class loader.")
              .formatted(
                  invocation.owner, invocation.name, invocation.desc, providerClass.getName()),
          e);
    }
  }

  private static Method findMethod(
      Class<?> owner, String name, Class<?>[] parameters, Set<Class<?>> visited) {
    if (owner == null || !visited.add(owner)) {
      return null;
    }
    try {
      return owner.getDeclaredMethod(name, parameters);
    } catch (NoSuchMethodException e) {
      Method inherited = findMethod(owner.getSuperclass(), name, parameters, visited);
      if (inherited != null) {
        return inherited;
      }
      for (Class<?> iface : owner.getInterfaces()) {
        inherited = findMethod(iface, name, parameters, visited);
        if (inherited != null) {
          return inherited;
        }
      }
      return null;
    }
  }

  private static IllegalArgumentException unsupported(
      Class<?> providerClass, MethodNode method, String operation) {
    return new IllegalArgumentException(
        ("ConstraintProvider method %s.%s%s uses %s, which cannot be relocated safely for automatic node sharing. "
                + "\nMaybe remove caller-dependent behavior from constraint construction, or disable constraintStreamAutomaticNodeSharing.")
            .formatted(providerClass.getName(), method.name, method.desc, operation));
  }

  private NodeSharingValidator() {}
}
