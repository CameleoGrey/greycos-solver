package greycos.solver.core.impl.heuristic.selector.list;

import java.util.Iterator;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;

/** Applies GLS priorities to legal sublist origins before mimic recording. */
public final class GuidedLocalSearchSubListSelector<Solution_> extends AbstractSelector<Solution_>
    implements SubListSelector<Solution_> {

  private final SubListSelector<Solution_> child;
  private final GuidedLocalSearchSelectionContext<Solution_> context;

  public GuidedLocalSearchSubListSelector(
      SubListSelector<Solution_> child, GuidedLocalSearchSelectionContext<Solution_> context) {
    this.child = child;
    this.context = context;
    phaseLifecycleSupport.addEventListener(child);
  }

  @Override
  public ListVariableDescriptor<Solution_> getVariableDescriptor() {
    return child.getVariableDescriptor();
  }

  @Override
  public SelectionCacheType getCacheType() {
    return child.getCacheType();
  }

  @Override
  public boolean isNeverEnding() {
    return child.isNeverEnding();
  }

  @Override
  public long getSize() {
    return child.getSize();
  }

  @Override
  public Iterator<SubList> iterator() {
    return context.direct(
        child.iterator(),
        child.isNeverEnding(),
        subList ->
            context.subListPriority(
                subList.entity(),
                getVariableDescriptor().getVariableName(),
                subList.fromIndex(),
                subList.length()));
  }

  @Override
  public Iterator<Object> endingValueIterator() {
    return child.endingValueIterator();
  }

  @Override
  public long getValueCount() {
    return child.getValueCount();
  }
}
