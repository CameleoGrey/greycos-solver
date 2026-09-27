package greycos.solver.core.impl.partitionedsearch.partitioner;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Simple round-robin partitioner for testing (not production-ready).
 *
 * <p>Distributes independent basic-variable entities round-robin across partitions. Each entity
 * needs a unique {@code @PlanningId}; list variables and entity-valued ranges (including chains)
 * require a cotwin-specific partitioner. Planning clones preserve the fact ranges in each partition
 * and may share immutable facts. Entity properties and arrays need writable accessors; cloned
 * entity collections must be mutable.
 *
 * <p>For production, implement a cotwin-specific partitioner considering entity relationships.
 *
 * @param <Solution_> solution type, class with {@link PlanningSolution} annotation
 */
@NullMarked
public class RoundRobinPartitioner<Solution_> implements SolutionPartitioner<Solution_> {

  private final int partCount;

  public RoundRobinPartitioner(int partCount) {
    if (partCount < 1) {
      throw new IllegalArgumentException(
          "Partition count must be at least 1, but was: " + partCount);
    }
    this.partCount = partCount;
  }

  @Override
  public List<Solution_> splitWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, Integer runnablePartThreadLimit) {

    InnerScoreDirector<Solution_, ?> innerScoreDirector =
        (InnerScoreDirector<Solution_, ?>) scoreDirector;
    SolutionDescriptor<Solution_> solutionDescriptor = innerScoreDirector.getSolutionDescriptor();
    Solution_ workingSolution = innerScoreDirector.getWorkingSolution();

    if (runnablePartThreadLimit != null && runnablePartThreadLimit < 1) {
      throw new IllegalArgumentException(
          "The runnablePartThreadLimit (" + runnablePartThreadLimit + ") must be at least 1.");
    }
    validateModel(solutionDescriptor);
    int actualPartCount =
        runnablePartThreadLimit != null ? Math.min(partCount, runnablePartThreadLimit) : partCount;
    Map<EntityKey, Integer> entityPartMap = new HashMap<>();
    solutionDescriptor.visitAllEntities(
        workingSolution,
        entity -> {
          var key = entityKey(solutionDescriptor, entity);
          if (entityPartMap.putIfAbsent(key, entityPartMap.size() % actualPartCount) != null) {
            throw new IllegalArgumentException(
                "RoundRobinPartitioner requires distinct entities with unique @PlanningId values, but found duplicate "
                    + key
                    + ".");
          }
        });

    Set<Object> seenEntities = Collections.newSetFromMap(new IdentityHashMap<>());
    solutionDescriptor.visitAllEntities(workingSolution, seenEntities::add);
    List<Solution_> partList = new ArrayList<>(actualPartCount);
    for (int partIndex = 0; partIndex < actualPartCount; partIndex++) {
      Solution_ partSolution = innerScoreDirector.cloneSolution(workingSolution);
      if (partSolution == workingSolution) {
        throw new IllegalArgumentException(
            "RoundRobinPartitioner requires independent planning clones. Check the solution cloner.");
      }
      solutionDescriptor.visitAllEntities(
          partSolution,
          entity -> {
            if (!seenEntities.add(entity)) {
              throw new IllegalArgumentException(
                  "RoundRobinPartitioner requires independent cloned entities, but entity ("
                      + entity
                      + ") is shared. Check the solution cloner.");
            }
          });
      for (var accessor : solutionDescriptor.getEntityMemberAccessorMap().values()) {
        var entity = accessor.executeGetter(partSolution);
        if (entity != null
            && !belongsToPartition(solutionDescriptor, entityPartMap, entity, partIndex)) {
          accessor.executeSetter(partSolution, null);
        }
      }
      for (var accessor : solutionDescriptor.getEntityCollectionMemberAccessorMap().values()) {
        var entities = accessor.executeGetter(partSolution);
        if (entities instanceof Collection<?>
            && entities == accessor.executeGetter(workingSolution)) {
          throw new IllegalArgumentException(
              "RoundRobinPartitioner requires an independent cloned entity collection or array for member ("
                  + accessor.getName()
                  + "). Check the solution cloner.");
        }
        if (entities instanceof Collection<?> collection) {
          final int currentPartIndex = partIndex;
          try {
            collection.removeIf(
                entity ->
                    !belongsToPartition(
                        solutionDescriptor, entityPartMap, entity, currentPartIndex));
          } catch (UnsupportedOperationException exception) {
            throw new IllegalArgumentException(
                "RoundRobinPartitioner requires a mutable cloned entity collection for member ("
                    + accessor.getName()
                    + "). Check the solution cloner.",
                exception);
          }
        } else {
          var retainedEntities = new ArrayList<>();
          for (int i = 0; i < Array.getLength(entities); i++) {
            var entity = Array.get(entities, i);
            if (belongsToPartition(solutionDescriptor, entityPartMap, entity, partIndex)) {
              retainedEntities.add(entity);
            }
          }
          var retainedArray =
              Array.newInstance(entities.getClass().getComponentType(), retainedEntities.size());
          for (int i = 0; i < retainedEntities.size(); i++) {
            Array.set(retainedArray, i, retainedEntities.get(i));
          }
          accessor.executeSetter(partSolution, retainedArray);
        }
      }
      partList.add(partSolution);
    }
    return partList;
  }

  private void validateModel(SolutionDescriptor<Solution_> solutionDescriptor) {
    for (var entityDescriptor : solutionDescriptor.getEntityDescriptors()) {
      for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
        if (variableDescriptor.isListVariable()
            || variableDescriptor.getValueRangeDescriptor().mightContainEntity()) {
          throw new IllegalArgumentException(
              "RoundRobinPartitioner only supports independent basic variables with fact value ranges. Variable ("
                  + variableDescriptor.getSimpleEntityAndVariableName()
                  + ") uses a list variable or an entity-valued range (including chains). "
                  + "Use a cotwin-specific SolutionPartitioner for this model.");
        }
      }
    }
    solutionDescriptor.getEntityMemberAccessorMap().values().forEach(this::requireSetter);
    solutionDescriptor.getEntityCollectionMemberAccessorMap().values().stream()
        .filter(accessor -> accessor.getType().isArray())
        .forEach(this::requireSetter);
  }

  private void requireSetter(MemberAccessor accessor) {
    if (!accessor.supportSetter()) {
      throw new IllegalArgumentException(
          "RoundRobinPartitioner requires a writable entity property or array member ("
              + accessor.getName()
              + "). Add a setter or use a cotwin-specific SolutionPartitioner.");
    }
  }

  private boolean belongsToPartition(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Map<EntityKey, Integer> entityPartMap,
      Object entity,
      int partIndex) {
    var key = entityKey(solutionDescriptor, entity);
    var assignedPartIndex = entityPartMap.get(key);
    if (assignedPartIndex == null) {
      throw new IllegalArgumentException(
          "RoundRobinPartitioner found an unknown cloned entity ("
              + key
              + "). Check the solution cloner.");
    }
    return assignedPartIndex == partIndex;
  }

  private EntityKey entityKey(SolutionDescriptor<Solution_> solutionDescriptor, Object entity) {
    var idAccessor = solutionDescriptor.getPlanningIdAccessor(entity.getClass());
    var id = idAccessor == null ? null : idAccessor.executeGetter(entity);
    if (id == null) {
      throw new IllegalArgumentException(
          "RoundRobinPartitioner requires a non-null @PlanningId on entity ("
              + entity
              + ") of class ("
              + entity.getClass().getName()
              + ").");
    }
    return new EntityKey(entity.getClass(), id);
  }

  private record EntityKey(Class<?> entityClass, Object planningId) {}

  public int getPartCount() {
    return partCount;
  }
}
