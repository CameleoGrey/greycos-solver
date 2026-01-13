package ai.greycos.solver.core.impl.heuristic.selector.value.decorator;

import java.util.Objects;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableDemand;
import ai.greycos.solver.core.impl.cotwin.variable.inverserelation.SingletonInverseVariableSupply;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import ai.greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public final class MovableChainedTrailingValueFilter<Solution_>
    implements SelectionFilter<Solution_, Object> {

  private final GenuineVariableDescriptor<Solution_> variableDescriptor;

  public MovableChainedTrailingValueFilter(
      GenuineVariableDescriptor<Solution_> variableDescriptor) {
    this.variableDescriptor = variableDescriptor;
  }

  @Override
  public boolean accept(ScoreDirector<Solution_> scoreDirector, Object value) {
    if (value == null) {
      return true;
    }
    SingletonInverseVariableSupply supply = retrieveSingletonInverseVariableSupply(scoreDirector);
    Object trailingEntity = supply.getInverseSingleton(value);
    EntityDescriptor<Solution_> entityDescriptor = variableDescriptor.getEntityDescriptor();
    if (trailingEntity == null || !entityDescriptor.matchesEntity(trailingEntity)) {
      return true;
    }
    return entityDescriptor
        .getEffectiveMovableEntityFilter()
        .test(scoreDirector.getWorkingSolution(), trailingEntity);
  }

  private SingletonInverseVariableSupply retrieveSingletonInverseVariableSupply(
      ScoreDirector<Solution_> scoreDirector) {
    // TODO Performance loss because the supply is retrieved for every accept
    // A SelectionFilter should be optionally made aware of lifecycle events, so it can cache the
    // supply
    SupplyManager supplyManager =
        ((InnerScoreDirector<Solution_, ?>) scoreDirector).getSupplyManager();
    return supplyManager.demand(new SingletonInverseVariableDemand<>(variableDescriptor));
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other == null || getClass() != other.getClass()) return false;
    MovableChainedTrailingValueFilter<?> that = (MovableChainedTrailingValueFilter<?>) other;
    return Objects.equals(variableDescriptor, that.variableDescriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableDescriptor);
  }
}
