package greycos.solver.core.impl.nodesharing;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Generates a nestmate subclass while keeping provider fields, initialization and lambda
 * implementations in the original class. Only constraint-building methods are relocated.
 */
public final class NodeSharingTransformer {

  static final String LOOKUP_DESCRIPTOR = "()Ljava/lang/invoke/MethodHandles$Lookup;";
  static final String BOOTSTRAP_DESCRIPTOR =
      "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
          + "Ljava/lang/invoke/MethodHandle;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;";
  private static final String HELPER_PREFIX = "$greycos$helper$";

  private final Class<?> providerClass;
  private final String originalName;
  private final String generatedName;
  private final String companionName;
  private final java.util.Set<String> selectedMethods = new HashSet<>();
  private final Map<String, HelperMethod> helpers = new LinkedHashMap<>();
  private final Map<AccessBridge, String> accessBridges = new LinkedHashMap<>();
  private final Map<LambdaKey, InvokeDynamicInsnNode> initializations = new LinkedHashMap<>();
  private final Map<LambdaKey, String> sharedAccessors = new LinkedHashMap<>();
  private final Map<HelperMethod, String> helperBridges = new LinkedHashMap<>();
  private final java.util.Set<String> reservedMethodNames = new HashSet<>();

  public NodeSharingTransformer(Class<?> providerClass) {
    this.providerClass = providerClass;
    originalName = Type.getInternalName(providerClass);
    generatedName = originalName + "$GreycosNodeShared";
    companionName =
        originalName + "$GreycosNodeSharingLookup$" + UUID.randomUUID().toString().replace("-", "");
  }

  public Class<?> transform() throws ReflectiveOperationException {
    byte[] originalBytecode = readClassFile(providerClass);
    var analysis = new ConstraintProviderAnalyzer(providerClass).analyze();
    if (!analysis.hasShareableLambdas()) {
      return providerClass;
    }
    if (Serializable.class.isAssignableFrom(providerClass)) {
      throw new IllegalArgumentException(
          "Automatic node sharing requires a generated hidden subclass for ConstraintProvider "
              + providerClass.getName()
              + ", which implements java.io.Serializable. Hidden provider instances, including "
              + "those captured by serializable lambdas, cannot be deserialized. "
              + "Use a non-Serializable constraint provider or disable constraintStreamAutomaticNodeSharing.");
    }
    var deduplicator = new LambdaDeduplicator(analysis);
    var canonicalizer = new LambdaImplementationCanonicalizer(providerClass, originalBytecode);
    var original = new ClassNode(Opcodes.ASM9);
    new ClassReader(originalBytecode).accept(original, ClassReader.SKIP_FRAMES);
    var stackObservationAnalyzer = new StackObservationAnalyzer(providerClass, originalBytecode);
    selectMethods(original, deduplicator, canonicalizer);
    collectHelpers(original);

    var generated = new ClassNode(Opcodes.ASM9);
    generated.version = Math.max(original.version, Opcodes.V17);
    generated.access =
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
    generated.name = generatedName;
    generated.superName = originalName;
    generated.sourceFile = original.sourceFile;
    generated.sourceDebug = original.sourceDebug;
    for (MethodNode method : original.methods) {
      if (method.name.equals("<init>")) {
        generated.methods.add(delegatingConstructor(method));
      } else if (isCopied(method)) {
        NodeSharingValidator.validateCopiedMethod(providerClass, method, stackObservationAnalyzer);
        generated.methods.add(copyMethod(method, deduplicator, canonicalizer));
      }
    }
    if (initializations.isEmpty()) {
      throw new IllegalStateException(
          "Automatic node sharing analysis for "
              + providerClass.getName()
              + " found shared lambdas, but none could be transformed. Check the provider bytecode.");
    }
    addSharedFieldsAndAccessors(generated, deduplicator);
    byte[] generatedBytecode = writeClass(generated);
    byte[] companionBytecode = buildCompanion();
    var bindings = new ArrayList<NodeSharingClassDefiner.HelperBinding>();
    helperBridges.forEach(
        (helper, bridgeName) ->
            bindings.add(
                new NodeSharingClassDefiner.HelperBinding(
                    bridgeName + "$handle", helper.name(), helper.descriptor())));
    return NodeSharingClassDefiner.define(
        providerClass, companionBytecode, generatedBytecode, bindings);
  }

  static byte[] readClassFile(Class<?> type) {
    String resource = "/" + Type.getInternalName(type) + ".class";
    try (InputStream input = type.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IllegalStateException("Cannot find class file for " + type.getName());
      }
      return input.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read class file for " + type.getName(), e);
    }
  }

  private boolean isCopied(MethodNode method) {
    return selectedMethods.contains(method.name + method.desc);
  }

  private void selectMethods(
      ClassNode original,
      LambdaDeduplicator deduplicator,
      LambdaImplementationCanonicalizer canonicalizer) {
    var candidates = new LinkedHashMap<String, MethodNode>();
    for (MethodNode method : original.methods) {
      if (method.name.startsWith("<")
          || (method.access
                  & (Opcodes.ACC_SYNTHETIC
                      | Opcodes.ACC_BRIDGE
                      | Opcodes.ACC_NATIVE
                      | Opcodes.ACC_ABSTRACT))
              != 0) {
        continue;
      }
      String identity = method.name + method.desc;
      candidates.put(identity, method);
      for (AbstractInsnNode instruction : method.instructions) {
        if (instruction instanceof InvokeDynamicInsnNode dynamic) {
          LambdaKey key =
              canonicalizer.buildKey(dynamic.name, dynamic.desc, dynamic.bsm, dynamic.bsmArgs);
          if (key != null && deduplicator.getFieldName(key) != null) {
            selectedMethods.add(identity);
          }
        }
      }
    }
    // Virtual callers already dispatch to copied overrides. Private/static helpers need their
    // callers copied as well, so their call instructions can target the generated helper.
    boolean changed;
    do {
      changed = false;
      for (var entry : candidates.entrySet()) {
        if (selectedMethods.contains(entry.getKey())) {
          continue;
        }
        for (AbstractInsnNode instruction : entry.getValue().instructions) {
          String targetIdentity = null;
          if (instruction instanceof MethodInsnNode invocation
              && invocation.owner.equals(originalName)) {
            targetIdentity = invocation.name + invocation.desc;
          } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
            Handle implementation = ordinaryImplementation(dynamic);
            if (implementation != null && implementation.getOwner().equals(originalName)) {
              targetIdentity = implementation.getName() + implementation.getDesc();
            }
          }
          if (targetIdentity != null) {
            MethodNode target = candidates.get(targetIdentity);
            if (target != null
                && selectedMethods.contains(targetIdentity)
                && (target.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0) {
              changed |= selectedMethods.add(entry.getKey());
              break;
            }
          }
        }
      }
    } while (changed);
  }

  private void collectHelpers(ClassNode original) {
    int index = 0;
    for (Class<?> type = providerClass; type != null; type = type.getSuperclass()) {
      for (Method method : type.getDeclaredMethods()) {
        reservedMethodNames.add(method.getName());
      }
    }
    for (Method method : providerClass.getMethods()) {
      reservedMethodNames.add(method.getName());
    }
    for (MethodNode method : original.methods) {
      if (isCopied(method) && (method.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) != 0) {
        boolean instance = (method.access & Opcodes.ACC_STATIC) == 0;
        String helperName;
        do {
          helperName = HELPER_PREFIX + method.name + "$" + index++;
        } while (!reservedMethodNames.add(helperName));
        helpers.put(
            method.name + method.desc,
            new HelperMethod(
                helperName, instance ? withReceiver(method.desc) : method.desc, instance));
      }
    }
  }

  private String withReceiver(String descriptor) {
    return "(L" + originalName + ";" + descriptor.substring(1);
  }

  private String newAccessorName() {
    int index = sharedAccessors.size();
    String name;
    do {
      name = "$greycos$shared$" + index++;
    } while (!reservedMethodNames.add(name));
    return name;
  }

  private MethodNode delegatingConstructor(MethodNode original) {
    var constructor =
        new MethodNode(
            Opcodes.ASM9,
            original.access,
            "<init>",
            original.desc,
            original.signature,
            original.exceptions.toArray(String[]::new));
    constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    appendArguments(constructor.instructions, Type.getArgumentTypes(original.desc), 1);
    constructor.instructions.add(
        new MethodInsnNode(Opcodes.INVOKESPECIAL, originalName, "<init>", original.desc, false));
    constructor.instructions.add(new InsnNode(Opcodes.RETURN));
    return constructor;
  }

  private MethodNode copyMethod(
      MethodNode original,
      LambdaDeduplicator deduplicator,
      LambdaImplementationCanonicalizer canonicalizer) {
    var copy =
        new MethodNode(
            Opcodes.ASM9,
            original.access,
            original.name,
            original.desc,
            original.signature,
            original.exceptions.toArray(String[]::new));
    original.accept(copy);
    HelperMethod helper = helpers.get(original.name + original.desc);
    if (helper != null) {
      copy.name = helper.name();
      copy.desc = helper.descriptor();
      copy.signature = null;
      copy.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
      // Explicit-receiver helpers are implementation details; original parameter metadata does
      // not describe their additional receiver parameter.
      copy.parameters = null;
      copy.visibleParameterAnnotations = null;
      copy.invisibleParameterAnnotations = null;
      copy.visibleAnnotableParameterCount = 0;
      copy.invisibleAnnotableParameterCount = 0;
    }
    for (AbstractInsnNode instruction : copy.instructions.toArray()) {
      if (instruction instanceof InvokeDynamicInsnNode dynamic) {
        LambdaKey key =
            canonicalizer.buildKey(dynamic.name, dynamic.desc, dynamic.bsm, dynamic.bsmArgs);
        String fieldName = key == null ? null : deduplicator.getFieldName(key);
        if (fieldName != null) {
          initializations.putIfAbsent(key, dynamic);
          String accessor = sharedAccessors.computeIfAbsent(key, ignored -> newAccessorName());
          copy.instructions.set(
              dynamic,
              new MethodInsnNode(
                  Opcodes.INVOKESTATIC,
                  generatedName,
                  accessor,
                  "()" + deduplicator.getFieldDescriptor(key),
                  false));
        } else {
          forwardBootstrap(dynamic);
        }
      } else if (instruction instanceof MethodInsnNode invocation) {
        rewriteInvocation(copy.instructions, invocation);
      } else if (instruction instanceof FieldInsnNode field
          && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.PUTFIELD)
          && requiresProtectedBridge(field.owner, field.name, field.desc, true)) {
        int kind = field.getOpcode() == Opcodes.GETFIELD ? Opcodes.H_GETFIELD : Opcodes.H_PUTFIELD;
        String descriptor =
            field.getOpcode() == Opcodes.GETFIELD ? "()" + field.desc : "(" + field.desc + ")V";
        rewriteAccess(
            copy.instructions,
            field,
            new AccessBridge(kind, field.owner, field.name, field.desc, withReceiver(descriptor)));
      }
    }
    if (helper != null && helper.instance()) {
      var nullCheck = new InsnList();
      nullCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
      nullCheck.add(
          new MethodInsnNode(
              Opcodes.INVOKESTATIC,
              "java/util/Objects",
              "requireNonNull",
              "(Ljava/lang/Object;)Ljava/lang/Object;",
              false));
      nullCheck.add(new InsnNode(Opcodes.POP));
      copy.instructions.insert(nullCheck);
    }
    if (helper != null && (original.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
      preserveMonitor(copy, helper.instance());
    }
    return copy;
  }

  private void rewriteInvocation(InsnList instructions, MethodInsnNode invocation) {
    HelperMethod helper =
        invocation.owner.equals(originalName)
            ? helpers.get(invocation.name + invocation.desc)
            : null;
    if (helper != null) {
      instructions.set(
          invocation,
          new MethodInsnNode(
              Opcodes.INVOKESTATIC, generatedName, helper.name(), helper.descriptor(), false));
    } else if (invocation.getOpcode() == Opcodes.INVOKESPECIAL
        && !invocation.name.equals("<init>")) {
      rewriteAccess(
          instructions,
          invocation,
          new AccessBridge(
              Opcodes.H_INVOKESPECIAL,
              invocation.owner,
              invocation.name,
              invocation.desc,
              withReceiver(invocation.desc)));
    } else if (invocation.getOpcode() == Opcodes.INVOKEVIRTUAL
        && requiresProtectedBridge(invocation.owner, invocation.name, invocation.desc, false)) {
      rewriteAccess(
          instructions,
          invocation,
          new AccessBridge(
              Opcodes.H_INVOKEVIRTUAL,
              invocation.owner,
              invocation.name,
              invocation.desc,
              withReceiver(invocation.desc)));
    }
  }

  private void rewriteAccess(
      InsnList instructions, AbstractInsnNode instruction, AccessBridge access) {
    String name =
        accessBridges.computeIfAbsent(access, ignored -> "$access$" + accessBridges.size());
    instructions.set(
        instruction,
        new MethodInsnNode(
            Opcodes.INVOKESTATIC, companionName, name, access.bridgeDescriptor(), false));
  }

  private boolean requiresProtectedBridge(
      String ownerName, String name, String descriptor, boolean field) {
    try {
      Class<?> owner =
          Class.forName(ownerName.replace('/', '.'), false, providerClass.getClassLoader());
      if (owner.isArray()) {
        return false; // Array clone is public, unlike Object.clone found by reflection.
      }
      Member member = findMember(owner, name, descriptor, field, new HashSet<>());
      if (member == null || !Modifier.isProtected(member.getModifiers())) {
        return false;
      }
      Class<?> declaringClass = member.getDeclaringClass();
      return declaringClass.getClassLoader() != providerClass.getClassLoader()
          || !declaringClass.getPackageName().equals(providerClass.getPackageName());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Cannot resolve protected member "
              + ownerName
              + "."
              + name
              + " while transforming ConstraintProvider "
              + providerClass.getName(),
          e);
    }
  }

  private static Member findMember(
      Class<?> owner,
      String name,
      String descriptor,
      boolean field,
      java.util.Set<Class<?>> visited) {
    if (owner == null || !visited.add(owner)) {
      return null;
    }
    if (field) {
      for (Field candidate : owner.getDeclaredFields()) {
        if (candidate.getName().equals(name)
            && Type.getDescriptor(candidate.getType()).equals(descriptor)) {
          return candidate;
        }
      }
    } else {
      for (Method candidate : owner.getDeclaredMethods()) {
        if (candidate.getName().equals(name)
            && Type.getMethodDescriptor(candidate).equals(descriptor)) {
          return candidate;
        }
      }
    }
    Member inherited = findMember(owner.getSuperclass(), name, descriptor, field, visited);
    if (inherited != null) {
      return inherited;
    }
    for (Class<?> iface : owner.getInterfaces()) {
      inherited = findMember(iface, name, descriptor, field, visited);
      if (inherited != null) {
        return inherited;
      }
    }
    return null;
  }

  private void forwardBootstrap(InvokeDynamicInsnNode dynamic) {
    Handle implementation = ordinaryImplementation(dynamic);
    if (implementation != null && implementation.getOwner().equals(originalName)) {
      HelperMethod helper = helpers.get(implementation.getName() + implementation.getDesc());
      if (helper != null) {
        String bridgeName =
            helperBridges.computeIfAbsent(helper, ignored -> "$helper$" + helperBridges.size());
        dynamic.bsmArgs[1] =
            new Handle(
                Opcodes.H_INVOKESTATIC, companionName, bridgeName, helper.descriptor(), false);
      }
    }
    var arguments = new ArrayList<Object>();
    arguments.add(dynamic.bsm);
    arguments.add(dynamic.bsmArgs.length);
    for (Object argument : dynamic.bsmArgs) {
      if (argument instanceof Handle handle) {
        arguments.add(handle.getTag());
        arguments.add(handle.getOwner().replace('/', '.'));
        arguments.add(handle.getName());
        arguments.add(handle.getDesc());
      } else {
        arguments.add(0);
        arguments.add(argument);
      }
    }
    dynamic.bsm =
        new Handle(Opcodes.H_INVOKESTATIC, companionName, "bootstrap", BOOTSTRAP_DESCRIPTOR, false);
    dynamic.bsmArgs = arguments.toArray();
  }

  /** Extended bootstraps retain their original implementation and serialization metadata. */
  private static Handle ordinaryImplementation(InvokeDynamicInsnNode dynamic) {
    if (dynamic.bsm.getTag() != Opcodes.H_INVOKESTATIC
        || !dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
        || !dynamic.bsm.getName().equals("metafactory")
        || !dynamic
            .bsm
            .getDesc()
            .equals(
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;"
                    + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
        || dynamic.bsmArgs.length != 3
        || !(dynamic.bsmArgs[1] instanceof Handle implementation)) {
      return null;
    }
    return implementation;
  }

  private void addSharedFieldsAndAccessors(ClassNode generated, LambdaDeduplicator deduplicator) {
    var initializer = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    for (var entry : initializations.entrySet()) {
      LambdaKey key = entry.getKey();
      String name = deduplicator.getFieldName(key);
      String descriptor = deduplicator.getFieldDescriptor(key);
      generated.fields.add(
          new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, descriptor, null, null));
      String lockName = name + "$lock";
      generated.fields.add(
          new FieldNode(
              Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
              lockName,
              "Ljava/lang/Object;",
              null,
              null));
      initializer.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
      initializer.instructions.add(new InsnNode(Opcodes.DUP));
      initializer.instructions.add(
          new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
      initializer.instructions.add(
          new FieldInsnNode(Opcodes.PUTSTATIC, generatedName, lockName, "Ljava/lang/Object;"));
      InvokeDynamicInsnNode creation = entry.getValue();
      forwardBootstrap(creation);
      // Lambda creation may initialize its functional interface. Keep it at the first executed
      // call site, including calls dispatched from the original constructor. Synchronization
      // publishes exactly one shared instance when providers are built concurrently. Each group
      // has its own lock: initializing one functional interface may execute a different getter.
      var accessor =
          new MethodNode(
              Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
              sharedAccessors.get(key),
              "()" + descriptor,
              null,
              null);
      var initialized = new LabelNode();
      accessor.instructions.add(
          new FieldInsnNode(Opcodes.GETSTATIC, generatedName, name, descriptor));
      accessor.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, initialized));
      accessor.instructions.add(creation);
      accessor.instructions.add(
          new FieldInsnNode(Opcodes.PUTSTATIC, generatedName, name, descriptor));
      accessor.instructions.add(initialized);
      accessor.instructions.add(
          new FieldInsnNode(Opcodes.GETSTATIC, generatedName, name, descriptor));
      accessor.instructions.add(new InsnNode(Opcodes.ARETURN));
      preserveMonitor(
          accessor,
          new FieldInsnNode(Opcodes.GETSTATIC, generatedName, lockName, "Ljava/lang/Object;"));
      generated.methods.add(accessor);
    }
    initializer.instructions.add(new InsnNode(Opcodes.RETURN));
    generated.methods.add(initializer);
  }

  private void preserveMonitor(MethodNode method, boolean instance) {
    preserveMonitor(
        method,
        instance
            ? new VarInsnNode(Opcodes.ALOAD, 0)
            : new LdcInsnNode(Type.getObjectType(originalName)));
  }

  private void preserveMonitor(MethodNode method, AbstractInsnNode monitor) {
    int monitorLocal = method.maxLocals;
    int resultLocal = monitorLocal + 1;
    Type resultType = Type.getReturnType(method.desc);
    int exceptionLocal = resultLocal + resultType.getSize();
    method.maxLocals = exceptionLocal + 1;
    var start = new LabelNode();
    var end = new LabelNode();
    var exit = new LabelNode();
    var handler = new LabelNode();
    var enter = new InsnList();
    enter.add(monitor);
    enter.add(new InsnNode(Opcodes.DUP));
    enter.add(new VarInsnNode(Opcodes.ASTORE, monitorLocal));
    enter.add(new InsnNode(Opcodes.MONITORENTER));
    enter.add(start);
    method.instructions.insert(enter);
    for (AbstractInsnNode instruction : method.instructions.toArray()) {
      int opcode = instruction.getOpcode();
      if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
        var replacement = new InsnList();
        if (opcode != Opcodes.RETURN) {
          replacement.add(new VarInsnNode(resultType.getOpcode(Opcodes.ISTORE), resultLocal));
        }
        replacement.add(new JumpInsnNode(Opcodes.GOTO, exit));
        method.instructions.insertBefore(instruction, replacement);
        method.instructions.remove(instruction);
      }
    }
    method.instructions.add(end);
    method.instructions.add(exit);
    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, monitorLocal));
    method.instructions.add(new InsnNode(Opcodes.MONITOREXIT));
    if (resultType.getSort() != Type.VOID) {
      method.instructions.add(new VarInsnNode(resultType.getOpcode(Opcodes.ILOAD), resultLocal));
    }
    method.instructions.add(new InsnNode(resultType.getOpcode(Opcodes.IRETURN)));
    method.instructions.add(handler);
    method.instructions.add(new VarInsnNode(Opcodes.ASTORE, exceptionLocal));
    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, monitorLocal));
    method.instructions.add(new InsnNode(Opcodes.MONITOREXIT));
    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, exceptionLocal));
    method.instructions.add(new InsnNode(Opcodes.ATHROW));
    method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
  }

  private byte[] buildCompanion() {
    var companion = new ClassNode(Opcodes.ASM9);
    new ClassReader(readClassFile(NodeSharingLookupTemplate.class))
        .accept(companion, ClassReader.SKIP_FRAMES);
    String templateName = companion.name;
    companion.name = companionName;
    companion.nestHostClass = null;
    companion.nestMembers = null;
    companion.innerClasses.clear();
    for (MethodNode method : companion.methods) {
      for (AbstractInsnNode instruction : method.instructions) {
        if (instruction instanceof MethodInsnNode invocation
            && invocation.owner.equals(templateName)) {
          invocation.owner = companionName;
        } else if (instruction instanceof LdcInsnNode constant
            && constant.cst instanceof Type type
            && type.getInternalName().equals(templateName)) {
          constant.cst = Type.getObjectType(originalName);
        }
      }
    }
    var initializer = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    for (var entry : accessBridges.entrySet()) {
      AccessBridge access = entry.getKey();
      String bridgeName = entry.getValue();
      String fieldName = bridgeName + "$handle";
      companion.fields.add(
          new FieldNode(
              Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
              fieldName,
              "Ljava/lang/invoke/MethodHandle;",
              null,
              null));
      initializer.instructions.add(new LdcInsnNode(access.kind()));
      initializer.instructions.add(new LdcInsnNode(access.owner().replace('/', '.')));
      initializer.instructions.add(new LdcInsnNode(access.name()));
      initializer.instructions.add(new LdcInsnNode(access.descriptor()));
      initializer.instructions.add(new LdcInsnNode(access.bridgeDescriptor()));
      initializer.instructions.add(
          new MethodInsnNode(
              Opcodes.INVOKESTATIC,
              companionName,
              "access",
              "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/invoke/MethodHandle;",
              false));
      initializer.instructions.add(
          new FieldInsnNode(
              Opcodes.PUTSTATIC, companionName, fieldName, "Ljava/lang/invoke/MethodHandle;"));
      addHandleBridge(companion, bridgeName, access.bridgeDescriptor());
    }
    helperBridges.forEach(
        (helper, bridgeName) -> {
          // These handles are bound after the hidden helper class has been defined, before any
          // generated provider can be constructed. The named bridge remains linkable from
          // lambdas created with the original provider lookup.
          companion.fields.add(
              new FieldNode(
                  Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE,
                  bridgeName + "$handle",
                  "Ljava/lang/invoke/MethodHandle;",
                  null,
                  null));
          addHandleBridge(companion, bridgeName, helper.descriptor());
        });
    initializer.instructions.add(new InsnNode(Opcodes.RETURN));
    companion.methods.add(initializer);
    return writeClass(companion);
  }

  private void addHandleBridge(ClassNode companion, String bridgeName, String descriptor) {
    var bridge =
        new MethodNode(
            Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, bridgeName, descriptor, null, null);
    bridge.instructions.add(
        new FieldInsnNode(
            Opcodes.GETSTATIC,
            companionName,
            bridgeName + "$handle",
            "Ljava/lang/invoke/MethodHandle;"));
    appendArguments(bridge.instructions, Type.getArgumentTypes(descriptor), 0);
    bridge.instructions.add(
        new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/invoke/MethodHandle",
            "invokeExact",
            descriptor,
            false));
    bridge.instructions.add(
        new InsnNode(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN)));
    companion.methods.add(bridge);
  }

  private byte[] writeClass(ClassNode node) {
    var writer = new ProviderClassWriter(providerClass, generatedName);
    node.accept(writer);
    return writer.toByteArray();
  }

  private static void appendArguments(InsnList instructions, Type[] arguments, int offset) {
    int local = offset;
    for (Type argument : arguments) {
      instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
      local += argument.getSize();
    }
  }

  private record HelperMethod(String name, String descriptor, boolean instance) {}

  private record AccessBridge(
      int kind, String owner, String name, String descriptor, String bridgeDescriptor) {}

  /**
   * Resolve application types using the provider loader, including the not-yet-defined subclass.
   */
  private static final class ProviderClassWriter extends ClassWriter {
    private final Class<?> providerClass;
    private final String generatedName;

    private ProviderClassWriter(Class<?> providerClass, String generatedName) {
      super(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
      this.providerClass = providerClass;
      this.generatedName = generatedName;
    }

    @Override
    protected String getCommonSuperClass(String first, String second) {
      if (first.equals(second)) {
        return first;
      }
      try {
        Class<?> firstClass = resolve(first);
        Class<?> secondClass = resolve(second);
        if (firstClass.isAssignableFrom(secondClass)) {
          return Type.getInternalName(firstClass);
        }
        if (secondClass.isAssignableFrom(firstClass)) {
          return Type.getInternalName(secondClass);
        }
        if (firstClass.isInterface() || secondClass.isInterface()) {
          return "java/lang/Object";
        }
        do {
          firstClass = firstClass.getSuperclass();
        } while (!firstClass.isAssignableFrom(secondClass));
        return Type.getInternalName(firstClass);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(
            "Cannot resolve frame types "
                + first
                + " and "
                + second
                + " for automatic node sharing of "
                + providerClass.getName()
                + ". Make both types available to the provider class loader.",
            e);
      }
    }

    private Class<?> resolve(String name) throws ClassNotFoundException {
      if (name.equals(generatedName)) {
        return providerClass;
      }
      return Class.forName(name.replace('/', '.'), false, providerClass.getClassLoader());
    }
  }
}
