package greycos.solver.core.impl.move;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningListVariableMetaModel;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;

/** Resolves variable identity in the destination model when rebasing between solver contexts. */
public final class VariableDescriptorRebaser {

  private VariableDescriptorRebaser() {}

  public static <Solution_, Entity_, Value_>
      PlanningListVariableMetaModel<Solution_, Entity_, Value_> rebase(
          PlanningListVariableMetaModel<Solution_, Entity_, Value_> metaModel, Lookup lookup) {
    var descriptor =
        ((DefaultPlanningListVariableMetaModel<Solution_, Entity_, Value_>) metaModel)
            .variableDescriptor();
    var destination = rebase(descriptor, lookup);
    return destination == descriptor ? metaModel : destination.getVariableMetaModel();
  }

  @SuppressWarnings("unchecked")
  public static <Solution_, Descriptor_ extends VariableDescriptor<Solution_>> Descriptor_ rebase(
      Descriptor_ descriptor, Lookup lookup) {
    VariableDescriptorAwareScoreDirector<Solution_> director;
    if (lookup instanceof InnerMutableSolutionView<?> view) {
      director = (VariableDescriptorAwareScoreDirector<Solution_>) view.getScoreDirector();
    } else if (lookup instanceof VariableDescriptorAwareScoreDirector<?> scoreDirector) {
      director = (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    } else {
      // User-supplied lookups only map working objects and have no model metadata.
      return descriptor;
    }
    var destination =
        director
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(descriptor.getEntityDescriptor().getEntityClass())
            .getVariableDescriptor(descriptor.getVariableName());
    if (destination == null || destination.getClass() != descriptor.getClass()) {
      throw new IllegalArgumentException(
          "The destination model has no compatible variable for (" + descriptor + ").");
    }
    return (Descriptor_) destination;
  }
}
