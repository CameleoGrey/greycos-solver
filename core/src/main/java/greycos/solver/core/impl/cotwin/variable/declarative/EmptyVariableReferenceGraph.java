package greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.Nullable;

final class EmptyVariableReferenceGraph implements VariableReferenceGraph {

  public static final EmptyVariableReferenceGraph INSTANCE = new EmptyVariableReferenceGraph();

  @Override
  public boolean updateChanged() {
    // No need to do anything.
    return true;
  }

  @Override
  public boolean hasPendingChanges() {
    return false;
  }

  @Override
  public @Nullable VariableChangeHook resolveHookFor(VariableMetaModel<?, ?, ?> variableReference) {
    return null;
  }

  @Override
  public List<VariableLoop> getVariableLoops() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "{}";
  }
}
