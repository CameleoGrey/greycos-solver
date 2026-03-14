package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class SelectorBasedKOptListMove<Solution_> extends KOptListMove<Solution_> {

  SelectorBasedKOptListMove(
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      KOptDescriptor<?> descriptor,
      MultipleDelegateList<?> combinedList,
      List<FlipSublistAction> equivalent2Opts,
      int postShiftAmount,
      int[] newEndIndices) {
    super(
        listVariableDescriptor,
        descriptor,
        combinedList,
        equivalent2Opts,
        postShiftAmount,
        newEndIndices);
  }

  SelectorBasedKOptListMove(
      ListVariableDescriptor<Solution_> listVariableDescriptor,
      KOptDescriptor<?> descriptor,
      List<FlipSublistAction> equivalent2Opts,
      int postShiftAmount,
      int[] newEndIndices,
      Object[] originalEntities) {
    super(
        listVariableDescriptor,
        descriptor,
        equivalent2Opts,
        postShiftAmount,
        newEndIndices,
        originalEntities);
  }

  @Override
  public SelectorBasedKOptListMove<Solution_> rebase(
      ScoreDirector<Solution_> destinationScoreDirector) {
    var rebasedEquivalent2Opts =
        new java.util.ArrayList<FlipSublistAction>(getEquivalent2Opts().size());
    var castScoreDirector = (VariableDescriptorAwareScoreDirector<?>) destinationScoreDirector;
    var newEntities = new Object[getOriginalEntities().length];

    for (var i = 0; i < newEntities.length; i++) {
      newEntities[i] = castScoreDirector.lookUpWorkingObject(getOriginalEntities()[i]);
    }
    for (var twoOpt : getEquivalent2Opts()) {
      rebasedEquivalent2Opts.add(twoOpt.rebase());
    }

    return new SelectorBasedKOptListMove<>(
        getListVariableDescriptor(),
        getDescriptor(),
        rebasedEquivalent2Opts,
        getPostShiftAmount(),
        getNewEndIndices(),
        newEntities);
  }
}
