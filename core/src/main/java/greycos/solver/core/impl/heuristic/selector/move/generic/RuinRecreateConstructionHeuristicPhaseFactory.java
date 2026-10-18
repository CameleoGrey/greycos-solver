package greycos.solver.core.impl.heuristic.selector.move.generic;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfile;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfiles;
import greycos.solver.core.impl.constructionheuristic.placer.EntityPlacer;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;

final class RuinRecreateConstructionHeuristicPhaseFactory<Solution_>
    extends DefaultConstructionHeuristicPhaseFactory<Solution_> {

  private final ListVariableDescriptor<Solution_> listVariableDescriptor;

  RuinRecreateConstructionHeuristicPhaseFactory(
      ConstructionHeuristicPhaseConfig phaseConfig,
      ListVariableDescriptor<Solution_> listVariableDescriptor) {
    super(phaseConfig);
    this.listVariableDescriptor = listVariableDescriptor;
  }

  RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> rebuild(
      HeuristicConfigPolicy<Solution_> configPolicy,
      InnerScoreDirector<Solution_, ?> scoreDirector) {
    var destinationDescriptor = scoreDirector.getSolutionDescriptor();
    var profiles = configPolicy.getConstructionHeuristicNearbyProfiles();
    var rebasedProfiles =
        configPolicy.getSolutionDescriptor().getGenuineEntityDescriptors().stream()
            .flatMap(entity -> entity.getGenuineVariableDescriptorList().stream())
            .distinct()
            .flatMap(variable -> profiles.getProfiles(variable).stream())
            .map(
                profile ->
                    new ConstructionHeuristicNearbyProfile(
                        destinationDescriptor
                            .findEntityDescriptorOrFail(
                                profile.variableDescriptor().getEntityDescriptor().getEntityClass())
                            .getGenuineVariableDescriptor(
                                profile.variableDescriptor().getVariableName()),
                        profile.distanceMeterClass(),
                        profile.argumentShape(),
                        profile.provenance()))
            .toList();
    // A new policy owns fresh mimic registries; none of the original selectors can be reused.
    var rebasedPolicy =
        configPolicy
            .cloneBuilder()
            .withSolutionDescriptor(destinationDescriptor)
            .withEntitySorterManner(configPolicy.getEntitySorterManner())
            .withValueSorterManner(configPolicy.getValueSorterManner())
            .withReinitializeVariableFilterEnabled(
                configPolicy.isReinitializeVariableFilterEnabled())
            .withUnassignedValuesAllowed(configPolicy.isUnassignedValuesAllowed())
            .withConstructionHeuristicNearbyProfiles(
                new ConstructionHeuristicNearbyProfiles(rebasedProfiles))
            .build();
    var rebasedListVariableDescriptor =
        listVariableDescriptor == null
            ? null
            : destinationDescriptor.resolveListVariableDescriptor(
                listVariableDescriptor.getEntityDescriptor().getEntityClass(),
                listVariableDescriptor.getVariableName());
    return RuinRecreateConstructionHeuristicPhaseBuilder.create(
        rebasedPolicy, phaseConfig, rebasedListVariableDescriptor);
  }

  @Override
  protected RuinRecreateConstructionHeuristicPhaseBuilder<Solution_> createBuilder(
      HeuristicConfigPolicy<Solution_> phaseConfigPolicy,
      SolverTermination<Solution_> solverTermination,
      int phaseIndex,
      boolean lastInitializingPhase,
      EntityPlacer<Solution_> entityPlacer) {
    var phaseTermination = PhaseTermination.bridge(new BasicPlumbingTermination<Solution_>(false));
    return new RuinRecreateConstructionHeuristicPhaseBuilder<>(
        phaseConfigPolicy,
        this,
        phaseTermination,
        entityPlacer,
        buildDecider(phaseConfigPolicy, phaseTermination),
        listVariableDescriptor);
  }

  @Override
  protected RuinRecreateConstructionHeuristicDecider<Solution_> buildDecider(
      HeuristicConfigPolicy<Solution_> configPolicy, PhaseTermination<Solution_> termination) {
    return new RuinRecreateConstructionHeuristicDecider<>(termination, buildForager(configPolicy));
  }
}
