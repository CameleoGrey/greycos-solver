package ai.greycos.solver.core.impl.partitionedsearch.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

/**
 * Encapsulates planning variable changes from a partition's best solution.
 *
 * <p>Used to transfer changes from a partition's best solution to the main solution. The move
 * captures entity-variable value pairs that need to be applied.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {

  /** Mapping of variable descriptors to entity-value change pairs */
  private final Map<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> changeMap;

  /** Partition index this move originated from */
  private final int partIndex;

  /**
   * Creates a new partition change move.
   *
   * @param changeMap mapping of variable descriptors to change records
   * @param partIndex partition index
   */
  private PartitionChangeMove(
      Map<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> changeMap, int partIndex) {
    this.changeMap = changeMap;
    this.partIndex = partIndex;
  }

  /** Record of a single entity-value change. */
  private static final class EntityValuePair {
    private final Object entity;
    private final Object value;

    EntityValuePair(Object entity, Object value) {
      this.entity = entity;
      this.value = value;
    }

    public Object entity() {
      return entity;
    }

    public Object value() {
      return value;
    }
  }

  /**
   * Creates a move from a partition's best solution.
   *
   * <p>Collects all entity-variable value pairs from the partition's working solution. Each
   * planning entity's genuine variable values are captured.
   *
   * @param scoreDirector the score director from the partition's solver
   * @param partIndex the partition index
   * @return a new PartitionChangeMove
   */
  public static <Solution_> PartitionChangeMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, int partIndex) {
    SolutionDescriptor<Solution_> solutionDescriptor = scoreDirector.getSolutionDescriptor();
    Solution_ workingSolution = scoreDirector.getWorkingSolution();

    int entityCount = solutionDescriptor.getGenuineEntityCount(workingSolution);
    Map<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> changeMap =
        new LinkedHashMap<>(solutionDescriptor.getEntityDescriptors().size() * 3);
    for (EntityDescriptor<Solution_> entityDescriptor : solutionDescriptor.getEntityDescriptors()) {
      for (GenuineVariableDescriptor<Solution_> variableDescriptor :
          entityDescriptor.getDeclaredGenuineVariableDescriptors()) {
        changeMap.put(variableDescriptor, new ArrayList<>(entityCount));
      }
    }
    solutionDescriptor.visitAllEntities(
        workingSolution,
        entity -> {
          EntityDescriptor<Solution_> entityDescriptor =
              solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          if (entityDescriptor.isMovable(workingSolution, entity)) {
            for (GenuineVariableDescriptor<Solution_> variableDescriptor :
                entityDescriptor.getGenuineVariableDescriptorList()) {
              Object value = variableDescriptor.getValue(entity);
              changeMap.get(variableDescriptor).add(new EntityValuePair(entity, value));
            }
          }
        });
    return new PartitionChangeMove<>(changeMap, partIndex);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    InnerScoreDirector<Solution_, ?> innerScoreDirector;
    if (scoreDirector instanceof VariableChangeRecordingScoreDirector) {
      innerScoreDirector =
          ((VariableChangeRecordingScoreDirector<Solution_, ?>) scoreDirector).getBacking();
    } else {
      innerScoreDirector = (InnerScoreDirector<Solution_, ?>) scoreDirector;
    }
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> entry :
        changeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (EntityValuePair pair : entry.getValue()) {
        Object entity = pair.entity();
        Object value = pair.value();
        innerScoreDirector.changeVariableFacade(variableDescriptor, entity, value);
      }
    }
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public PartitionChangeMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    InnerScoreDirector<Solution_, ?> innerScoreDirector;
    if (destinationScoreDirector instanceof VariableChangeRecordingScoreDirector) {
      innerScoreDirector =
          ((VariableChangeRecordingScoreDirector<Solution_, ?>) destinationScoreDirector)
              .getBacking();
    } else {
      innerScoreDirector = (InnerScoreDirector<Solution_, ?>) destinationScoreDirector;
    }
    Map<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> destinationChangeMap =
        new LinkedHashMap<>(changeMap.size());
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<EntityValuePair>> entry :
        changeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      List<EntityValuePair> originPairList = entry.getValue();
      List<EntityValuePair> destinationPairList = new ArrayList<>(originPairList.size());
      for (EntityValuePair pair : originPairList) {
        Object originEntity = pair.entity();
        Object destinationEntity = innerScoreDirector.lookUpWorkingObject(originEntity);
        if (destinationEntity == null && originEntity != null) {
          throw new IllegalStateException(
              "The destinationEntity ("
                  + destinationEntity
                  + ") cannot be null if the originEntity ("
                  + originEntity
                  + ") is not null.");
        }
        Object originValue = pair.value();
        Object destinationValue = innerScoreDirector.lookUpWorkingObject(originValue);
        if (destinationValue == null && originValue != null) {
          throw new IllegalStateException(
              "The destinationEntity ("
                  + destinationEntity
                  + ")'s destinationValue ("
                  + destinationValue
                  + ") cannot be null if the originEntity ("
                  + originEntity
                  + ")'s originValue ("
                  + originValue
                  + ") is not null.\n"
                  + "Maybe add the originValue ("
                  + originValue
                  + ") of class ("
                  + originValue.getClass()
                  + ") as a problem fact in the planning solution with a "
                  + ProblemFactCollectionProperty.class.getSimpleName()
                  + " annotation.");
        }
        destinationPairList.add(new EntityValuePair(destinationEntity, destinationValue));
      }
      destinationChangeMap.put(variableDescriptor, destinationPairList);
    }
    return new PartitionChangeMove<>(destinationChangeMap, partIndex);
  }

  @Override
  public Collection<? extends Object> getPlanningEntities() {
    throw new UnsupportedOperationException(
        "Impossible situation: "
            + PartitionChangeMove.class.getSimpleName()
            + " is only used to communicate between a part thread and the solver thread, it's never used in Tabu Search.");
  }

  @Override
  public Collection<? extends Object> getPlanningValues() {
    throw new UnsupportedOperationException(
        "Impossible situation: "
            + PartitionChangeMove.class.getSimpleName()
            + " is only used to communicate between a part thread and the solver thread, it's never used in Tabu Search.");
  }

  @Override
  public String toString() {
    int changeCount = changeMap.values().stream().mapToInt(List::size).sum();
    return "part-" + partIndex + " {" + changeCount + " variables changed}";
  }
}
