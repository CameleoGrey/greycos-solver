package ai.greycos.solver.core.impl.nodesharing;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM visitor that adds static final fields for shared lambdas.
 *
 * <p>Adds fields after existing class fields but before methods. The fields are not initialized
 * here - they will be lazily initialized by the first lambda creation in the transformed bytecode.
 */
public class FieldAddingVisitor extends ClassVisitor {

  private static final int STATIC_FINAL =
      Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE;

  private final LambdaDeduplicator deduplicator;

  public FieldAddingVisitor(ClassVisitor cv, LambdaDeduplicator deduplicator) {
    super(Opcodes.ASM9, cv);
    this.deduplicator = deduplicator;
  }

  @Override
  public void visitEnd() {
    // Add static final fields for each lambda group
    for (var entry : deduplicator.getAnalysis().getShareableLambdas().entrySet()) {
      LambdaKey key = entry.getKey();
      String fieldName = deduplicator.getFieldName(key);
      String fieldDescriptor = deduplicator.getFieldDescriptor(key);

      if (fieldName != null && fieldDescriptor != null) {
        // Create field: private static final Predicate<Shift> $predicate1;
        FieldVisitor fv = cv.visitField(STATIC_FINAL, fieldName, fieldDescriptor, null, null);
        if (fv != null) {
          fv.visitEnd();
        }
      }
    }

    super.visitEnd();
  }
}
