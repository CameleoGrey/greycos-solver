package greycos.solver.core.impl.move;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.heuristic.move.AbstractMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.RevertableScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.score.director.VariableDescriptorCache;

public final class VariableChangeRecordingScoreDirector<Solution_, Score_ extends Score<Score_>>
    implements RevertableScoreDirector<Solution_> {

  private final InnerScoreDirector<Solution_, Score_> backingScoreDirector;
  private List<ChangeAction<Solution_>> variableChanges;
  private boolean variableChangesExposed;

  /*
   * The fromIndex of afterListVariableChanged must match the fromIndex of its beforeListVariableChanged call.
   * Otherwise this will happen in the undo move:
   *
   * // beforeListVariableChanged(0, 3);
   * [1, 2, 3, 4]
   * change
   * [1, 2, 3]
   * // afterListVariableChanged(2, 3)
   * // Start Undo
   * // Undo afterListVariableChanged(2, 3)
   * [1, 2, 3] -> [1, 2]
   * // Undo beforeListVariableChanged(0, 3);
   * [1, 2, 3, 4, 1, 2]
   *
   * This map exists to ensure that this is the case.
   */
  private final Map<Object, Integer> cache;

  public VariableChangeRecordingScoreDirector(ScoreDirector<Solution_> backingScoreDirector) {
    this(backingScoreDirector, true);
  }

  public VariableChangeRecordingScoreDirector(
      ScoreDirector<Solution_> backingScoreDirector, boolean requiresIndexCache) {
    this.backingScoreDirector = (InnerScoreDirector<Solution_, Score_>) backingScoreDirector;
    this.cache = requiresIndexCache ? new IdentityHashMap<>() : null;
    this.variableChanges = new ArrayList<>();
  }

  private VariableChangeRecordingScoreDirector(
      InnerScoreDirector<Solution_, Score_> backingScoreDirector,
      List<ChangeAction<Solution_>> variableChanges,
      Map<Object, Integer> cache) {
    this.backingScoreDirector = backingScoreDirector;
    this.variableChanges = variableChanges;
    this.variableChangesExposed = true;
    this.cache = cache;
  }

  @Override
  public greycos.solver.core.preview.api.move.Move<Solution_> createUndoMove() {
    // The undo move retains the current list; undoChanges() must not clear it.
    variableChangesExposed = true;
    return new RecordedUndoMove<>(variableChanges);
  }

  @Override
  public void undoChanges() {
    var changeCount = variableChanges.size();
    if (changeCount > 0) {
      for (var i = changeCount - 1; i >= 0; i--) {
        variableChanges.get(i).undo(backingScoreDirector);
      }
      Objects.requireNonNull(backingScoreDirector).updateShadowVariables();
    }
    if (variableChangesExposed) {
      variableChanges = new ArrayList<>();
      variableChangesExposed = false;
    } else {
      variableChanges.clear();
    }
    if (cache != null) {
      cache.clear();
    }
  }

  @Override
  public void beforeVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    variableChanges.add(
        new VariableChangeAction<>(
            entity, variableDescriptor.getValue(entity), variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.beforeVariableChanged(variableDescriptor, entity);
    }
  }

  @Override
  public void afterVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    if (backingScoreDirector != null) {
      backingScoreDirector.afterVariableChanged(variableDescriptor, entity);
    }
  }

  @Override
  public void beforeListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    // List is fromIndex, fromIndex, since the undo action for afterListVariableChange will clear
    // the affected list
    if (cache != null) {
      cache.put(entity, fromIndex);
    }
    var list = variableDescriptor.getValue(entity);
    variableChanges.add(
        new ListVariableBeforeChangeAction<>(
            entity, (List<Object>) list, fromIndex, toIndex, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.beforeListVariableChanged(
          variableDescriptor, entity, fromIndex, toIndex);
    }
  }

  @Override
  public void afterListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    if (cache != null) {
      Integer requiredFromIndex = cache.remove(entity);
      if (requiredFromIndex != fromIndex) {
        throw new IllegalArgumentException(
            """
                                The fromIndex of afterListVariableChanged (%d) must match the fromIndex of its beforeListVariableChanged counterpart (%d).
                                Maybe check implementation of your %s."""
                .formatted(fromIndex, requiredFromIndex, AbstractMove.class.getSimpleName()));
      }
    }
    variableChanges.add(
        new ListVariableAfterChangeAction<>(entity, fromIndex, toIndex, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.afterListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
    }
  }

  @Override
  public void beforeListVariableElementAssigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    variableChanges.add(new ListVariableBeforeAssignmentAction<>(element, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.beforeListVariableElementAssigned(variableDescriptor, element);
    }
  }

  @Override
  public void afterListVariableElementAssigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    variableChanges.add(new ListVariableAfterAssignmentAction<>(element, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.afterListVariableElementAssigned(variableDescriptor, element);
    }
  }

  @Override
  public void beforeListVariableElementUnassigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    variableChanges.add(new ListVariableBeforeUnassignmentAction<>(element, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.beforeListVariableElementUnassigned(variableDescriptor, element);
    }
  }

  @Override
  public void afterListVariableElementUnassigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    variableChanges.add(new ListVariableAfterUnassignmentAction<>(element, variableDescriptor));
    if (backingScoreDirector != null) {
      backingScoreDirector.afterListVariableElementUnassigned(variableDescriptor, element);
    }
  }

  // For other operations, call the delegate's method.

  @Override
  public SolutionDescriptor<Solution_> getSolutionDescriptor() {
    return Objects.requireNonNull(backingScoreDirector).getSolutionDescriptor();
  }

  @Override
  public ValueRangeManager<Solution_> getValueRangeManager() {
    return getBacking().getValueRangeManager();
  }

  /** Returns the score director to which events are delegated. */
  public InnerScoreDirector<Solution_, Score_> getBacking() {
    return backingScoreDirector;
  }

  /**
   * The {@code VariableChangeRecordingScoreDirector} score director includes two main tasks:
   * tracking any variable change and firing events to a delegated score director. This method
   * returns a copy of the score director that only tracks variable changes without firing any
   * delegated score director events.
   */
  public VariableChangeRecordingScoreDirector<Solution_, Score_> getNonDelegating() {
    // Another recorder may retain this list, or create an undo move from it.
    variableChangesExposed = true;
    return new VariableChangeRecordingScoreDirector<>(null, variableChanges, cache);
  }

  @Override
  public Solution_ getWorkingSolution() {
    return Objects.requireNonNull(backingScoreDirector).getWorkingSolution();
  }

  @Override
  public VariableDescriptorCache<Solution_> getVariableDescriptorCache() {
    return Objects.requireNonNull(backingScoreDirector).getVariableDescriptorCache();
  }

  @Override
  public void updateShadowVariables() {
    variableChanges.add(UpdateShadowVariablesAction.instance());
    if (backingScoreDirector != null) {
      backingScoreDirector.updateShadowVariables();
    }
  }

  @Override
  public <E> E lookUpWorkingObject(E externalObject) {
    return Objects.requireNonNull(backingScoreDirector).lookUpWorkingObject(externalObject);
  }
}
