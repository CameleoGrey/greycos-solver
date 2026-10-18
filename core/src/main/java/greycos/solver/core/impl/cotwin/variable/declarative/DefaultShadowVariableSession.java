package greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.List;

import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.impl.cotwin.variable.declarative.VariableReferenceGraph.VariableChangeHook;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.Supply;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefaultShadowVariableSession<Solution_> implements Supply {
  final VariableReferenceGraph graph;

  public DefaultShadowVariableSession(VariableReferenceGraph graph) {
    this.graph = graph;
  }

  /**
   * @return null if changes to the variable never affect the declarative shadow variables
   * @see VariableReferenceGraph#resolveHookFor(VariableMetaModel)
   */
  public @Nullable VariableChangeHook resolveHookFor(
      VariableDescriptor<Solution_> variableDescriptor) {
    return graph.resolveHookFor(variableDescriptor.getVariableMetaModel());
  }

  public boolean hasPendingChanges() {
    return graph.hasPendingChanges();
  }

  public boolean updateVariables() {
    return graph.updateChanged();
  }

  public List<VariableLoop> getVariableLoops() {
    return graph.getVariableLoops();
  }
}
