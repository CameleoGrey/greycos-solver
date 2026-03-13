package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import java.util.function.Function;

import ai.greycos.solver.core.impl.cotwin.variable.IndexShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.ListElementsChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.nextprev.NextElementShadowVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.nextprev.PreviousElementShadowVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class DelegatingListVariableStateSupply<Solution_>
    implements ListVariableStateSupply<Solution_, Object, Object> {

  private final ListVariableStateSupply<Solution_, Object, Object> delegate;
  private final Function<Object, Integer> indexFunction;

  DelegatingListVariableStateSupply(
      ListVariableStateSupply<Solution_, Object, Object> delegate,
      Function<Object, Integer> indexFunction) {
    this.delegate = delegate;
    this.indexFunction = indexFunction;
  }

  @Override
  public void externalize(IndexShadowVariableDescriptor<Solution_> shadowVariableDescriptor) {
    delegate.externalize(shadowVariableDescriptor);
  }

  @Override
  public void externalize(
      InverseRelationShadowVariableDescriptor<Solution_> shadowVariableDescriptor) {
    delegate.externalize(shadowVariableDescriptor);
  }

  @Override
  public void externalize(
      PreviousElementShadowVariableDescriptor<Solution_> shadowVariableDescriptor) {
    delegate.externalize(shadowVariableDescriptor);
  }

  @Override
  public void externalize(NextElementShadowVariableDescriptor<Solution_> shadowVariableDescriptor) {
    delegate.externalize(shadowVariableDescriptor);
  }

  @Override
  public void resetWorkingSolution(InnerScoreDirector<Solution_, ?> scoreDirector) {
    delegate.resetWorkingSolution(scoreDirector);
  }

  @Override
  public void beforeChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, ListElementsChangeEvent<Object> changeEvent) {
    delegate.beforeChange(scoreDirector, changeEvent);
  }

  @Override
  public void afterChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, ListElementsChangeEvent<Object> changeEvent) {
    delegate.afterChange(scoreDirector, changeEvent);
  }

  @Override
  public void afterListElementUnassigned(
      InnerScoreDirector<Solution_, ?> scoreDirector, Object unassignedElement) {
    delegate.afterListElementUnassigned(scoreDirector, unassignedElement);
  }

  @Override
  public ListVariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return delegate.getSourceVariableDescriptor();
  }

  @Override
  public boolean isAssigned(Object element) {
    return delegate.isAssigned(element);
  }

  @Override
  public boolean isPinned(Object element) {
    return delegate.isPinned(element);
  }

  @Override
  public ElementPosition getElementPosition(Object value) {
    return delegate.getElementPosition(value);
  }

  @Override
  public int getUnassignedCount() {
    return delegate.getUnassignedCount();
  }

  @Override
  public @Nullable Object getPreviousElement(Object element) {
    return delegate.getPreviousElement(element);
  }

  @Override
  public @Nullable Object getNextElement(Object element) {
    return delegate.getNextElement(element);
  }

  @Override
  public @Nullable Integer getIndex(Object planningValue) {
    return indexFunction.apply(planningValue);
  }

  @Override
  public @Nullable Object getInverseSingleton(Object planningValue) {
    return delegate.getInverseSingleton(planningValue);
  }
}
