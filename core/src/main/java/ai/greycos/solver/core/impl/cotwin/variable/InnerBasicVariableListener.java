package ai.greycos.solver.core.impl.cotwin.variable;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InnerBasicVariableListener<Solution_, Entity_>
    extends InnerVariableListener<Solution_, BasicVariableChangeEvent<Entity_>> {}
