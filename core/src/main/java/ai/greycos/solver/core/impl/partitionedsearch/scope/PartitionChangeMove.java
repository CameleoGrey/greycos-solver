package ai.greycos.solver.core.impl.partitionedsearch.scope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Encapsulates planning variable changes from a partition's best solution.
 *
 * <p>Used to transfer changes from a partition's best solution to the main solution. The move
 * captures entity-variable value pairs that need to be applied.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public final class PartitionChangeMove<Solution_> extends AbstractMove<Solution_> {

  /** Mapping of variable descriptors to entity-value change pairs */
  private final Map<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> changeMap;

  /** Partition index this move originated from */
  private final int partIndex;

  /**
   * Creates a new partition change move.
   *
   * @param changeMap mapping of variable descriptors to change records
   * @param partIndex partition index
   */
  private PartitionChangeMove(
      Map<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> changeMap, int partIndex) {
    this.changeMap = changeMap;
    this.partIndex = partIndex;
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

    Map<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> changeMap =
        new LinkedHashMap<>();

    // Collect all variable changes from partition
    solutionDescriptor.visitAllEntities(
        workingSolution,
        entity -> {
          var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          if (entityDescriptor.isMovable(workingSolution, entity)) {
            for (GenuineVariableDescriptor<Solution_> variableDescriptor :
                entityDescriptor.getGenuineVariableDescriptorList()) {
              if (changeMap
                  .computeIfAbsent(variableDescriptor, k -> new ArrayList<>())
                  .add(new ChangeRecord<>(entity, variableDescriptor.getValue(entity)))) {
                // Value already recorded for this variable, skip
              }
            }
          }
        });

    return new PartitionChangeMove<>(changeMap, partIndex);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    var innerScoreDirector = (InnerScoreDirector<Solution_, ?>) scoreDirector;
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> entry :
        changeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (ChangeRecord<?> changeRecord : entry.getValue()) {
        innerScoreDirector.changeVariableFacade(
            variableDescriptor, changeRecord.entity(), changeRecord.value());
      }
    }
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public PartitionChangeMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    var innerScoreDirector = (InnerScoreDirector<Solution_, ?>) destinationScoreDirector;

    // Rebase entities and values from partition to main solution context
    Map<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> destinationChangeMap =
        new LinkedHashMap<>();

    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<ChangeRecord<?>>> entry :
        changeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      List<ChangeRecord<?>> originPairList = entry.getValue();
      List<ChangeRecord<?>> destinationPairList = new ArrayList<>();

      for (ChangeRecord<?> pair : originPairList) {
        Object originEntity = pair.entity();
        Object originValue = pair.value();

        // Translate entity from partition to main solution
        Object destinationEntity = innerScoreDirector.lookUpWorkingObject(originEntity);

        // Translate value from partition to main solution
        Object destinationValue = innerScoreDirector.lookUpWorkingObject(originValue);

        destinationPairList.add(new ChangeRecord<>(destinationEntity, destinationValue));
      }

      destinationChangeMap.put(variableDescriptor, destinationPairList);
    }

    return new PartitionChangeMove<>(destinationChangeMap, partIndex);
  }

  @Override
  public String toString() {
    return "PartitionChangeMove(partition=" + partIndex + ", changes=" + changeMap.size() + ")";
  }

  /**
   * Record of a single entity-value change.
   *
   * @param <E> entity type
   */
  private static final class ChangeRecord<E> {
    private final E entity;
    private final Object value;

    ChangeRecord(E entity, Object value) {
      this.entity = entity;
      this.value = value;
    }

    public E entity() {
      return entity;
    }

    public Object value() {
      return value;
    }
  }
}
