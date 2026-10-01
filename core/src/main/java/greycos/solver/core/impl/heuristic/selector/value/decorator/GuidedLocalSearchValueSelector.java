package greycos.solver.core.impl.heuristic.selector.value.decorator;

import java.util.Iterator;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;

/** Applies GLS priorities to legal list origins before mimic recording. */
public final class GuidedLocalSearchValueSelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements IterableValueSelector<Solution_> {

  private final IterableValueSelector<Solution_> child;
  private final GuidedLocalSearchSelectionContext<Solution_> context;
  private final boolean discardLosingProbes;

  public GuidedLocalSearchValueSelector(
      IterableValueSelector<Solution_> child,
      GuidedLocalSearchSelectionContext<Solution_> context,
      boolean discardLosingProbes) {
    this.child = child;
    this.context = context;
    this.discardLosingProbes = discardLosingProbes;
    phaseLifecycleSupport.addEventListener(child);
  }

  @Override
  public GenuineVariableDescriptor<Solution_> getVariableDescriptor() {
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
  public long getSize(Object entity) {
    return child.getSize(entity);
  }

  @Override
  public Iterator<Object> iterator() {
    return direct(child.iterator());
  }

  @Override
  public Iterator<Object> iterator(Object entity) {
    return direct(child.iterator(entity));
  }

  private Iterator<Object> direct(Iterator<Object> iterator) {
    return context.direct(
        iterator,
        discardLosingProbes,
        value -> context.valuePriority(value, getVariableDescriptor().getVariableName()));
  }

  @Override
  public Iterator<Object> endingIterator(Object entity) {
    return child.endingIterator(entity);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof GuidedLocalSearchValueSelector<?> that
        && Objects.equals(child, that.child)
        && context == that.context
        && discardLosingProbes == that.discardLosingProbes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(child, context, discardLosingProbes);
  }
}
