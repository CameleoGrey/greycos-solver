package greycos.solver.core.impl.nodesharing;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Bytecode template for the companion defined in the provider's package and module. Keep its
 * dependencies limited to java.base: the provider module need not read GreyCOS implementation
 * packages. The generator replaces the class literal in originalClass() with the original provider.
 */
final class NodeSharingLookupTemplate {

  private NodeSharingLookupTemplate() {}

  static MethodHandles.Lookup lookup() {
    return MethodHandles.lookup();
  }

  static MethodHandles.Lookup originalLookup() throws IllegalAccessException {
    return MethodHandles.privateLookupIn(originalClass(), lookup());
  }

  private static Class<?> originalClass() {
    return NodeSharingLookupTemplate.class;
  }

  static CallSite bootstrap(
      MethodHandles.Lookup ignored,
      String name,
      MethodType type,
      MethodHandle bootstrap,
      Object... encodedArguments)
      throws Throwable {
    // Handle constants must be resolved with the original lookup too. Resolving a protected or
    // invokespecial handle in the hidden subclass can narrow its receiver to that subclass.
    int argumentCount = (Integer) encodedArguments[0];
    Object[] originalArguments = new Object[argumentCount + 3];
    originalArguments[0] = originalLookup();
    originalArguments[1] = name;
    originalArguments[2] = type;
    int input = 1;
    for (int argument = 0; argument < argumentCount; argument++) {
      int handleKind = (Integer) encodedArguments[input++];
      if (handleKind == 0) {
        originalArguments[argument + 3] = encodedArguments[input++];
      } else {
        String owner = (String) encodedArguments[input++];
        String member = (String) encodedArguments[input++];
        String descriptor = (String) encodedArguments[input++];
        originalArguments[argument + 3] = resolveHandle(handleKind, owner, member, descriptor);
      }
    }
    return (CallSite) bootstrap.invokeWithArguments(originalArguments);
  }

  private static MethodHandle resolveHandle(
      int kind, String ownerName, String name, String descriptor)
      throws ReflectiveOperationException {
    Class<?> original = originalClass();
    ClassLoader loader = original.getClassLoader();
    Class<?> owner = Class.forName(ownerName, false, loader);
    MethodHandles.Lookup lookup = originalLookup();
    return switch (kind) {
      case MethodHandleInfo.REF_getField ->
          lookup.findGetter(owner, name, fieldType(descriptor, loader));
      case MethodHandleInfo.REF_putField ->
          lookup.findSetter(owner, name, fieldType(descriptor, loader));
      case MethodHandleInfo.REF_getStatic ->
          lookup.findStaticGetter(owner, name, fieldType(descriptor, loader));
      case MethodHandleInfo.REF_putStatic ->
          lookup.findStaticSetter(owner, name, fieldType(descriptor, loader));
      case MethodHandleInfo.REF_invokeVirtual, MethodHandleInfo.REF_invokeInterface ->
          lookup.findVirtual(
              owner, name, MethodType.fromMethodDescriptorString(descriptor, loader));
      case MethodHandleInfo.REF_invokeStatic ->
          lookup.findStatic(owner, name, MethodType.fromMethodDescriptorString(descriptor, loader));
      case MethodHandleInfo.REF_invokeSpecial ->
          lookup.findSpecial(
              owner, name, MethodType.fromMethodDescriptorString(descriptor, loader), original);
      case MethodHandleInfo.REF_newInvokeSpecial ->
          lookup.findConstructor(owner, MethodType.fromMethodDescriptorString(descriptor, loader));
      default -> throw new IllegalArgumentException("Unsupported method handle kind: " + kind);
    };
  }

  private static Class<?> fieldType(String descriptor, ClassLoader loader) {
    return MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
  }

  static MethodHandle access(
      int kind, String ownerName, String name, String descriptor, String bridgeDescriptor)
      throws ReflectiveOperationException {
    return resolveHandle(kind, ownerName, name, descriptor)
        .asType(
            MethodType.fromMethodDescriptorString(
                bridgeDescriptor, originalClass().getClassLoader()));
  }
}
