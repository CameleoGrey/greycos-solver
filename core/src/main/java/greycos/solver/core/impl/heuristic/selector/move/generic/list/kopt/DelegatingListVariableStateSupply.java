package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import java.util.function.ToIntFunction;

import greycos.solver.core.impl.cotwin.variable.IndexShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.ListElementsChangeEvent;
import greycos.solver.core.impl.cotwin.variable.ListVariableStateSupply;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.NextElementShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.PreviousElementShadowVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.ElementPosition;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record DelegatingListVariableStateSupply<Solution_>(
    ListVariableStateSupply<Solution_, Object, Object> delegate,
    ToIntFunction<Object> indexFunction)
    implements ListVariableStateSupply<Solution_, Object, Object> {

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
  public int getIndexOrFail(Object planningValue) {
    var index = indexFunction.applyAsInt(planningValue);
    if (index < 0) {
      throw new IllegalStateException("The element (%s) is not assigned to any list variable.");
    }
    return index;
  }

  @Override
  public int getIndexOrElse(Object planningValue, int defaultValue) {
    var index = indexFunction.applyAsInt(planningValue);
    if (index < 0) {
      return defaultValue;
    }
    return index;
  }

  @Override
  public @Nullable Object getInverseSingleton(Object planningValue) {
    return delegate.getInverseSingleton(planningValue);
  }

  @Override
  public ListVariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return delegate.getSourceVariableDescriptor();
  }

  @Override
  public boolean isAssigned(Object queryCompositeKey) {
    return delegate.isAssigned(queryCompositeKey);
  }

  @Override
  public boolean isPinned(Object queryCompositeKey) {
    return delegate.isPinned(queryCompositeKey);
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
  public @Nullable Object getPreviousElement(Object queryCompositeKey) {
    return delegate.getPreviousElement(queryCompositeKey);
  }

  @Override
  public @Nullable Object getNextElement(Object queryCompositeKey) {
    return delegate.getNextElement(queryCompositeKey);
  }

  @Override
  public void afterListElementUnassigned(
      InnerScoreDirector<Solution_, ?> scoreDirector, Object unassignedElement) {
    delegate.afterListElementUnassigned(scoreDirector, unassignedElement);
  }

  @Override
  public void beforeChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, ListElementsChangeEvent<Object> event) {
    delegate.beforeChange(scoreDirector, event);
  }

  @Override
  public void afterChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, ListElementsChangeEvent<Object> event) {
    delegate.afterChange(scoreDirector, event);
  }
}
