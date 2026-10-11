package greycos.solver.math.api;

import org.jspecify.annotations.NullMarked;

/** A model-owned integer variable whose domain is exactly zero and one. */
@NullMarked
public final class BinaryVar extends IntVar {

  BinaryVar(Object owner, int id, String name) {
    super(owner, id, name, 0, 1);
  }
}
