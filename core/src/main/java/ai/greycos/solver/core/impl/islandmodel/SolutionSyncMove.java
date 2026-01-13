package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Applies a full solution state to the current working solution without replacing entity instances.
 * Used by island model to adopt migrants or global bests while keeping move threads in sync.
 */
@NullMarked
final class SolutionSyncMove<Solution_> extends AbstractMove<Solution_> {

  private final Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>>
      basicChangeMap;
  private final Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap;

  private SolutionSyncMove(
      Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> basicChangeMap,
      Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap) {
    this.basicChangeMap = basicChangeMap;
    this.listChangeMap = listChangeMap;
  }

  static <Solution_> SolutionSyncMove<Solution_> createMove(
      InnerScoreDirector<Solution_, ?> scoreDirector, Solution_ sourceSolution) {
    SolutionDescriptor<Solution_> solutionDescriptor = scoreDirector.getSolutionDescriptor();
    Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> basicChangeMap =
        new LinkedHashMap<>();
    Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap =
        new LinkedHashMap<>();

    solutionDescriptor.visitAllEntities(
        sourceSolution,
        entity -> {
          var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          if (!entityDescriptor.isMovable(sourceSolution, entity)) {
            return;
          }
          for (GenuineVariableDescriptor<Solution_> variableDescriptor :
              entityDescriptor.getGenuineVariableDescriptorList()) {
            if (variableDescriptor
                instanceof ListVariableDescriptor<Solution_> listVariableDescriptor) {
              List<Object> values = new ArrayList<>(listVariableDescriptor.getValue(entity));
              listChangeMap
                  .computeIfAbsent(listVariableDescriptor, k -> new ArrayList<>())
                  .add(new ListChangeRecord<>(entity, values));
            } else {
              Object value = variableDescriptor.getValue(entity);
              basicChangeMap
                  .computeIfAbsent(variableDescriptor, k -> new ArrayList<>())
                  .add(new BasicChangeRecord<>(entity, value));
            }
          }
        });

    return new SolutionSyncMove<>(basicChangeMap, listChangeMap).rebase(scoreDirector);
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    var castScoreDirector = (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    applyBasicChanges(castScoreDirector);
    applyListChanges(castScoreDirector);
  }

  private void applyBasicChanges(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var workingSolution = scoreDirector.getWorkingSolution();
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> entry :
        basicChangeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (BasicChangeRecord<?> changeRecord : entry.getValue()) {
        if (!variableDescriptor
            .getEntityDescriptor()
            .isMovable(workingSolution, changeRecord.entity())) {
          continue;
        }
        scoreDirector.changeVariableFacade(
            variableDescriptor, changeRecord.entity(), changeRecord.value());
      }
    }
  }

  private void applyListChanges(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var workingSolution = scoreDirector.getWorkingSolution();
    for (Map.Entry<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> entry :
        listChangeMap.entrySet()) {
      ListVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (ListChangeRecord<?> changeRecord : entry.getValue()) {
        Object entity = changeRecord.entity();
        if (!variableDescriptor.getEntityDescriptor().isMovable(workingSolution, entity)) {
          continue;
        }
        List<Object> targetList = changeRecord.values();
        List<Object> currentList = variableDescriptor.getValue(entity);
        if (currentList.equals(targetList)) {
          continue;
        }
        int fromIndex = variableDescriptor.getFirstUnpinnedIndex(entity);
        if (fromIndex > currentList.size()) {
          throw new IllegalStateException(
              "Pinned index (" + fromIndex + ") exceeds list size (" + currentList.size() + ").");
        }
        if (fromIndex > targetList.size()) {
          throw new IllegalStateException(
              "Target list size ("
                  + targetList.size()
                  + ") is smaller than pinned index ("
                  + fromIndex
                  + ").");
        }
        if (fromIndex > 0) {
          List<Object> currentPinned = currentList.subList(0, fromIndex);
          List<Object> targetPinned = targetList.subList(0, fromIndex);
          if (!currentPinned.equals(targetPinned)) {
            throw new IllegalStateException(
                "Pinned list segment differs for "
                    + variableDescriptor.getSimpleEntityAndVariableName()
                    + " on entity ("
                    + entity
                    + ").");
          }
        }
        int oldSize = currentList.size();
        scoreDirector.beforeListVariableChanged(variableDescriptor, entity, fromIndex, oldSize);
        currentList.subList(fromIndex, oldSize).clear();
        currentList.addAll(targetList.subList(fromIndex, targetList.size()));
        scoreDirector.afterListVariableChanged(
            variableDescriptor, entity, fromIndex, currentList.size());
      }
    }
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return true;
  }

  @Override
  public SolutionSyncMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    var innerScoreDirector = (InnerScoreDirector<Solution_, ?>) destinationScoreDirector;
    Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>>
        destinationBasicChangeMap = new LinkedHashMap<>();
    Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> destinationListChangeMap =
        new LinkedHashMap<>();

    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> entry :
        basicChangeMap.entrySet()) {
      List<BasicChangeRecord<?>> destinationChangeRecords = new ArrayList<>();
      for (BasicChangeRecord<?> record : entry.getValue()) {
        Object destinationEntity = innerScoreDirector.lookUpWorkingObject(record.entity());
        Object destinationValue =
            record.value() == null ? null : innerScoreDirector.lookUpWorkingObject(record.value());
        destinationChangeRecords.add(new BasicChangeRecord<>(destinationEntity, destinationValue));
      }
      destinationBasicChangeMap.put(entry.getKey(), destinationChangeRecords);
    }

    for (Map.Entry<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> entry :
        listChangeMap.entrySet()) {
      List<ListChangeRecord<?>> destinationChangeRecords = new ArrayList<>();
      for (ListChangeRecord<?> record : entry.getValue()) {
        Object destinationEntity = innerScoreDirector.lookUpWorkingObject(record.entity());
        List<Object> destinationValues =
            record.values().stream()
                .map(value -> value == null ? null : innerScoreDirector.lookUpWorkingObject(value))
                .toList();
        destinationChangeRecords.add(new ListChangeRecord<>(destinationEntity, destinationValues));
      }
      destinationListChangeMap.put(entry.getKey(), destinationChangeRecords);
    }

    return new SolutionSyncMove<>(destinationBasicChangeMap, destinationListChangeMap);
  }

  @Override
  public String getSimpleMoveTypeDescription() {
    return "SolutionSyncMove";
  }

  @Override
  public Collection<Object> getPlanningEntities() {
    if (basicChangeMap.isEmpty() && listChangeMap.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Object> entities = new LinkedHashSet<>();
    for (List<BasicChangeRecord<?>> changeRecords : basicChangeMap.values()) {
      for (BasicChangeRecord<?> record : changeRecords) {
        entities.add(record.entity());
      }
    }
    for (List<ListChangeRecord<?>> changeRecords : listChangeMap.values()) {
      for (ListChangeRecord<?> record : changeRecords) {
        entities.add(record.entity());
      }
    }
    return entities;
  }

  @Override
  public Collection<Object> getPlanningValues() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return "SolutionSyncMove{basicChanges="
        + basicChangeMap.size()
        + ", listChanges="
        + listChangeMap.size()
        + "}";
  }

  private static final class BasicChangeRecord<E> {
    private final E entity;
    private final Object value;

    private BasicChangeRecord(E entity, Object value) {
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

  private static final class ListChangeRecord<E> {
    private final E entity;
    private final List<Object> values;

    private ListChangeRecord(E entity, List<Object> values) {
      this.entity = Objects.requireNonNull(entity);
      this.values = values;
    }

    public E entity() {
      return entity;
    }

    public List<Object> values() {
      return values;
    }
  }
}
