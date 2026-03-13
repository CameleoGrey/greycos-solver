package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import java.util.List;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

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
}
