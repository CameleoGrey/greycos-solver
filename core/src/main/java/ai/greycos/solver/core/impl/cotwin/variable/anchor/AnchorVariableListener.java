package ai.greycos.solver.core.impl.cotwin.variable.anchor;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.cotwin.variable.BasicVariableChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.InnerBasicVariableListener;
import ai.greycos.solver.core.impl.cotwin.variable.InnerVariableListener;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableSupply;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public class AnchorVariableListener<Solution_, Entity_>
    implements InnerBasicVariableListener<Solution_, Entity_>, AnchorVariableSupply {

  protected final AnchorShadowVariableDescriptor<Solution_> anchorShadowVariableDescriptor;
  protected final VariableDescriptor<Solution_> previousVariableDescriptor;
  protected final SingletonInverseVariableSupply nextVariableSupply;

  public AnchorVariableListener(
      AnchorShadowVariableDescriptor<Solution_> anchorShadowVariableDescriptor,
      VariableDescriptor<Solution_> previousVariableDescriptor,
      SingletonInverseVariableSupply nextVariableSupply) {
    this.anchorShadowVariableDescriptor = anchorShadowVariableDescriptor;
    this.previousVariableDescriptor = previousVariableDescriptor;
    this.nextVariableSupply = nextVariableSupply;
  }

  @Override
  public void beforeChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, BasicVariableChangeEvent<Entity_> event) {
    // No need to retract() because the insert (which is guaranteed to be called later) affects the
    // same trailing entities.
  }

  @Override
  public void afterChange(
      InnerScoreDirector<Solution_, ?> scoreDirector, BasicVariableChangeEvent<Entity_> event) {
    insert(scoreDirector, event.entity());
  }

  protected void insert(InnerScoreDirector<Solution_, ?> scoreDirector, Object entity) {
    Object previousEntity = previousVariableDescriptor.getValue(entity);
    Object anchor;
    if (previousEntity == null) {
      anchor = null;
    } else if (previousVariableDescriptor.isValuePotentialAnchor(previousEntity)) {
      anchor = previousEntity;
    } else {
      anchor = anchorShadowVariableDescriptor.getValue(previousEntity);
    }
    Object nextEntity = entity;
    while (nextEntity != null && anchorShadowVariableDescriptor.getValue(nextEntity) != anchor) {
      scoreDirector.beforeVariableChanged(anchorShadowVariableDescriptor, nextEntity);
      anchorShadowVariableDescriptor.setValue(nextEntity, anchor);
      scoreDirector.afterVariableChanged(anchorShadowVariableDescriptor, nextEntity);
      nextEntity = nextVariableSupply.getInverseSingleton(nextEntity);
    }
  }

  @Override
  public Object getAnchor(Object entity) {
    return anchorShadowVariableDescriptor.getValue(entity);
  }

  @Override
  public void resetWorkingSolution(InnerScoreDirector<Solution_, ?> scoreDirector) {
    InnerVariableListener.forEachEntity(
        scoreDirector,
        anchorShadowVariableDescriptor.getEntityDescriptor().getEntityClass(),
        entity -> insert(scoreDirector, entity));
  }
}
