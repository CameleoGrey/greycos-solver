package greycos.solver.core.impl.nodesharing;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

/** Defines generated classes in the original provider's loader, module, package and nest. */
final class NodeSharingClassDefiner {

  static Class<?> define(
      Class<?> providerClass,
      byte[] companionBytecode,
      byte[] providerBytecode,
      List<HelperBinding> helperBindings)
      throws ReflectiveOperationException {
    Module solverModule = NodeSharingClassDefiner.class.getModule();
    Module providerModule = providerClass.getModule();
    if (!solverModule.canRead(providerModule)) {
      solverModule.addReads(providerModule);
    }
    MethodHandles.Lookup initialLookup;
    try {
      initialLookup = MethodHandles.privateLookupIn(providerClass, MethodHandles.lookup());
    } catch (IllegalAccessException e) {
      String target = solverModule.isNamed() ? solverModule.getName() : "ALL-UNNAMED";
      String directive =
          providerModule.isNamed()
              ? "--add-opens "
                  + providerModule.getName()
                  + "/"
                  + providerClass.getPackageName()
                  + "="
                  + target
              : "open package " + providerClass.getPackageName() + " to " + target;
      throw new IllegalStateException(
          "Automatic node sharing cannot access ConstraintProvider "
              + providerClass.getName()
              + " in module "
              + providerModule.getName()
              + ". Open its package using "
              + directive
              + ", or disable constraintStreamAutomaticNodeSharing.",
          e);
    }
    // A cross-module private lookup lacks MODULE privilege. A tiny companion in the provider
    // module obtains a full lookup without privileged JVM internals or a new class loader.
    Class<?> companion = initialLookup.defineClass(companionBytecode);
    try {
      var companionLookup =
          (MethodHandles.Lookup)
              initialLookup
                  .findStatic(
                      companion, "lookup", MethodType.methodType(MethodHandles.Lookup.class))
                  .invokeExact();
      var originalLookup =
          (MethodHandles.Lookup)
              initialLookup
                  .findStatic(
                      companion,
                      "originalLookup",
                      MethodType.methodType(MethodHandles.Lookup.class))
                  .invokeExact();
      var generatedLookup =
          originalLookup.defineHiddenClass(
              providerBytecode, false, MethodHandles.Lookup.ClassOption.NESTMATE);
      Class<?> generatedClass = generatedLookup.lookupClass();
      for (HelperBinding binding : helperBindings) {
        MethodHandle helper =
            generatedLookup.findStatic(
                generatedClass,
                binding.helperName(),
                MethodType.fromMethodDescriptorString(
                    binding.descriptor(), providerClass.getClassLoader()));
        companionLookup
            .findStaticSetter(companion, binding.fieldName(), MethodHandle.class)
            .invokeExact(helper);
      }
      return generatedClass;
    } catch (ReflectiveOperationException e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(
          "Cannot define or bind generated helpers for ConstraintProvider "
              + providerClass.getName()
              + ". Cause: "
              + e
              + "\nMaybe check provider initialization and module access, or disable constraintStreamAutomaticNodeSharing.",
          e);
    }
  }

  record HelperBinding(String fieldName, String helperName, String descriptor) {}

  private NodeSharingClassDefiner() {}
}
