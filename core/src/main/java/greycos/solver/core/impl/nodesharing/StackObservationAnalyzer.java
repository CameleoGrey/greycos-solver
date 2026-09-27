package greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Finds stack observations in reachable method declarations without initializing their classes.
 * This is deliberately not a purity proof: virtual dispatch follows the available declaration, and
 * platform methods are terminal contracts. Callbacks and their runtime overrides must remain
 * stack-insensitive, as required for interchangeable constraint operations.
 */
final class StackObservationAnalyzer {

  private static final int MAX_CLASSES = 256;
  private static final int MAX_METHODS = 2048;
  private static final int MAX_INSTRUCTIONS = 100_000;

  private final ClassLoader classLoader;
  private final Map<String, ClassData> classes = new HashMap<>();
  private final Set<String> unavailableClasses = new HashSet<>();
  private final Map<MethodReference, MethodSummary> summaries = new HashMap<>();
  private final Map<Query, Result> results = new HashMap<>();
  private int instructionCount;

  StackObservationAnalyzer(Class<?> providerClass, byte[] providerBytecode) {
    classLoader = providerClass.getClassLoader();
    classes.put(Type.getInternalName(providerClass), classData(providerClass, providerBytecode));
  }

  Result inspectImplementation(Handle implementation) {
    return inspect(
        new MethodReference(
            implementation.getOwner(), implementation.getName(), implementation.getDesc()),
        true);
  }

  Result inspectMethod(String owner, String name, String descriptor) {
    return inspect(new MethodReference(owner, name, descriptor), false);
  }

  private Result inspect(MethodReference root, boolean includeDeferredCallbacks) {
    var query = new Query(root, includeDeferredCallbacks);
    Result cached = results.get(query);
    if (cached != null) {
      return cached;
    }
    // Collect the whole reachable graph before caching a result. A back edge does not establish
    // that a recursive helper is clear; another member of its cycle may observe the stack.
    var pending = new ArrayDeque<Path>();
    pending.add(new Path(root, null));
    var visited = new HashSet<MethodReference>();
    boolean capturesStack = false;
    boolean unknown = false;
    String observationPath = null;
    while (!pending.isEmpty()) {
      Path path = pending.removeFirst();
      if (!visited.add(path.method())) {
        continue;
      }
      MethodSummary summary = summarize(path.method());
      capturesStack |= summary.capturesStack();
      unknown |= summary.unknown();
      if (summary.observesStack() && observationPath == null) {
        observationPath = describe(path);
      }
      for (Dependency dependency : summary.dependencies()) {
        if (includeDeferredCallbacks || !dependency.deferred()) {
          pending.addLast(new Path(dependency.method(), path));
        }
      }
    }
    var result = new Result(capturesStack, unknown, observationPath);
    results.put(query, result);
    return result;
  }

  private static String describe(Path path) {
    var methods = new ArrayDeque<String>();
    for (Path current = path; current != null; current = current.parent()) {
      methods.addFirst(current.method().display());
    }
    return String.join(" -> ", methods);
  }

  private MethodSummary summarize(MethodReference reference) {
    MethodSummary cached = summaries.get(reference);
    if (cached != null) {
      return cached;
    }
    if (summaries.size() >= MAX_METHODS) {
      return MethodSummary.UNKNOWN;
    }
    MethodSummary summary = buildSummary(reference);
    summaries.put(reference, summary);
    return summary;
  }

  private MethodSummary buildSummary(MethodReference reference) {
    if (LambdaImplementationCanonicalizer.inspectsStack(
        reference.owner(), reference.name(), reference.descriptor())) {
      return new MethodSummary(false, true, false, List.of());
    }
    // An opaque reflective target can observe the caller even when this invocation does not.
    if ((reference.owner().equals("java/lang/reflect/Method") && reference.name().equals("invoke"))
        || (reference.owner().equals("java/lang/reflect/Constructor")
            && reference.name().equals("newInstance"))
        || (reference.owner().equals("java/lang/invoke/MethodHandle")
            && reference.name().startsWith("invoke"))) {
      return MethodSummary.UNKNOWN;
    }
    ClassData owner = readClass(reference.owner());
    if (owner == null) {
      return MethodSummary.UNKNOWN;
    }
    boolean capturesStack = reference.name().equals("<init>") && owner.throwable();
    if (owner.platform()) {
      return new MethodSummary(capturesStack, false, false, List.of());
    }
    ResolvedMethod resolved =
        resolve(owner, reference.name(), reference.descriptor(), new HashSet<>());
    if (resolved == null) {
      return MethodSummary.UNKNOWN;
    }
    MethodNode method = resolved.method();
    if ((method.access & Opcodes.ACC_ABSTRACT) != 0) {
      // There is no declared body to inspect. Runtime implementations retain the callback contract.
      return new MethodSummary(capturesStack, false, false, List.of());
    }
    if (resolved.owner().platform()) {
      return new MethodSummary(capturesStack, false, false, List.of());
    }
    if ((method.access & Opcodes.ACC_NATIVE) != 0
        || instructionCount + method.instructions.size() > MAX_INSTRUCTIONS) {
      return MethodSummary.UNKNOWN;
    }
    instructionCount += method.instructions.size();
    var dependencies = new ArrayList<Dependency>();
    boolean unknown = false;
    for (AbstractInsnNode instruction : method.instructions) {
      if (instruction instanceof MethodInsnNode invocation) {
        dependencies.add(
            new Dependency(
                new MethodReference(invocation.owner, invocation.name, invocation.desc), false));
      } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
        String bootstrapOwner = dynamic.bsm.getOwner();
        boolean lambda = bootstrapOwner.equals("java/lang/invoke/LambdaMetafactory");
        boolean concat = bootstrapOwner.equals("java/lang/invoke/StringConcatFactory");
        unknown |= !lambda && !concat;
        for (Object argument : dynamic.bsmArgs) {
          unknown |= collectConstant(argument, dependencies, lambda);
        }
      } else if (instruction instanceof LdcInsnNode constant) {
        unknown |= collectConstant(constant.cst, dependencies, true);
      }
    }
    return new MethodSummary(capturesStack, false, unknown, List.copyOf(dependencies));
  }

  private static boolean collectConstant(
      Object constant, List<Dependency> dependencies, boolean deferred) {
    if (constant instanceof Handle handle && handle.getTag() >= Opcodes.H_INVOKEVIRTUAL) {
      dependencies.add(
          new Dependency(
              new MethodReference(handle.getOwner(), handle.getName(), handle.getDesc()),
              deferred));
    }
    return constant instanceof ConstantDynamic;
  }

  private ResolvedMethod resolve(
      ClassData owner, String name, String descriptor, Set<String> visited) {
    if (!visited.add(owner.node().name)) {
      return null;
    }
    for (MethodNode method : owner.node().methods) {
      if (method.name.equals(name) && method.desc.equals(descriptor)) {
        return new ResolvedMethod(owner, method);
      }
    }
    if (name.equals("<init>")) {
      return null;
    }
    if (owner.node().superName != null) {
      ClassData parent = readClass(owner.node().superName);
      if (parent != null) {
        ResolvedMethod method = resolve(parent, name, descriptor, visited);
        if (method != null) {
          return method;
        }
      }
    }
    for (String interfaceName : owner.node().interfaces) {
      ClassData iface = readClass(interfaceName);
      if (iface != null) {
        ResolvedMethod method = resolve(iface, name, descriptor, visited);
        if (method != null) {
          return method;
        }
      }
    }
    return null;
  }

  private ClassData readClass(String owner) {
    ClassData cached = classes.get(owner);
    if (cached != null) {
      return cached;
    }
    if (unavailableClasses.contains(owner) || classes.size() >= MAX_CLASSES) {
      return null;
    }
    try {
      Class<?> type = Class.forName(owner.replace('/', '.'), false, classLoader);
      if (type.isArray()) {
        var node = new ClassNode(Opcodes.ASM9);
        node.name = owner;
        var data = new ClassData(node, true, Throwable.class.isAssignableFrom(type));
        classes.put(owner, data);
        return data;
      }
      try (InputStream input = type.getResourceAsStream("/" + owner + ".class")) {
        if (input != null) {
          ClassData data = classData(type, input.readAllBytes());
          classes.put(owner, data);
          return data;
        }
      }
    } catch (ClassNotFoundException | IOException | LinkageError | SecurityException e) {
      // Unavailable helper code prevents a structural merge; it need not prevent relocation.
    }
    unavailableClasses.add(owner);
    return null;
  }

  private static ClassData classData(Class<?> type, byte[] bytecode) {
    var node = new ClassNode(Opcodes.ASM9);
    boolean platform = isPlatform(type);
    int flags = ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;
    if (platform) {
      // Keep declarations and inheritance for application calls to inherited platform methods.
      // Their executable implementation remains outside this analysis boundary.
      flags |= ClassReader.SKIP_CODE;
    }
    new ClassReader(bytecode).accept(node, flags);
    return new ClassData(node, platform, Throwable.class.isAssignableFrom(type));
  }

  private static boolean isPlatform(Class<?> type) {
    return type.getClassLoader() == null
        || type.getClassLoader() == ClassLoader.getPlatformClassLoader();
  }

  record Result(boolean capturesStack, boolean unknown, String observationPath) {
    boolean permitsStructuralIdentity() {
      return !capturesStack && !unknown && observationPath == null;
    }
  }

  private record MethodReference(String owner, String name, String descriptor) {
    String display() {
      return owner.replace('/', '.') + "." + name + descriptor;
    }
  }

  private record ClassData(ClassNode node, boolean platform, boolean throwable) {}

  private record ResolvedMethod(ClassData owner, MethodNode method) {}

  private record Dependency(MethodReference method, boolean deferred) {}

  private record MethodSummary(
      boolean capturesStack,
      boolean observesStack,
      boolean unknown,
      List<Dependency> dependencies) {
    private static final MethodSummary UNKNOWN = new MethodSummary(false, false, true, List.of());
  }

  private record Query(MethodReference root, boolean includeDeferredCallbacks) {}

  private record Path(MethodReference method, Path parent) {}
}
