package ai.greycos.solver.core.impl.nodesharing;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM visitor that adds static final fields for shared lambdas to transformed classes.
 *
 * <p>Why: Identical lambdas need shared static fields to enable node sharing. How: Adds private
 * static final fields via ASM after analyzing lambda groups. What: Inserts field declarations into
 * bytecode before methods.
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
    for (var entry : deduplicator.getAnalysis().getShareableLambdas().entrySet()) {
      LambdaKey key = entry.getKey();
      String fieldName = deduplicator.getFieldName(key);
      String fieldDescriptor = deduplicator.getFieldDescriptor(key);

      if (fieldName != null && fieldDescriptor != null) {
        FieldVisitor fv = cv.visitField(STATIC_FINAL, fieldName, fieldDescriptor, null, null);
        if (fv != null) {
          fv.visitEnd();
        }
      }
    }

    super.visitEnd();
  }
}
