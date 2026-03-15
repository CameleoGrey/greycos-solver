package ai.greycos.solver.core.impl.move;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

import ai.greycos.solver.core.api.cotwin.lookup.Lookup;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

final class ListVariableBeforeChangeAction<Solution_, Entity_, Value_>
    implements ChangeAction<Solution_> {

  private static final Object[] EMPTY_OLD_VALUES = new Object[0];

  private final Entity_ entity;
  private final Object oldSingleValue;
  private final Object[] oldValueArray;
  private final List<Object> oldValueView;
  private final int oldValueCount;
  private final int fromIndex;
  private final int toIndex;
  private final ListVariableDescriptor<Solution_> variableDescriptor;

  ListVariableBeforeChangeAction(
      Entity_ entity,
      List<Object> listValue,
      int fromIndex,
      int toIndex,
      ListVariableDescriptor<Solution_> variableDescriptor) {
    this.entity = entity;
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.variableDescriptor = variableDescriptor;
    this.oldValueCount = toIndex - fromIndex;
    if (oldValueCount == 0) {
      oldSingleValue = null;
      oldValueArray = EMPTY_OLD_VALUES;
      oldValueView = List.of();
    } else if (oldValueCount == 1) {
      oldSingleValue = listValue.get(fromIndex);
      oldValueArray = EMPTY_OLD_VALUES;
      oldValueView = new ImmutableArraySliceView<>(new Object[] {oldSingleValue}, 1);
    } else {
      oldSingleValue = null;
      oldValueArray = snapshotOldValues(listValue, fromIndex, toIndex);
      oldValueView = new ImmutableArraySliceView<>(oldValueArray, oldValueCount);
    }
  }

  private ListVariableBeforeChangeAction(
      Entity_ entity,
      Object oldSingleValue,
      Object[] oldValueArray,
      List<Object> oldValueView,
      int oldValueCount,
      int fromIndex,
      int toIndex,
      ListVariableDescriptor<Solution_> variableDescriptor) {
    this.entity = entity;
    this.oldSingleValue = oldSingleValue;
    this.oldValueArray = oldValueArray;
    this.oldValueView = oldValueView;
    this.oldValueCount = oldValueCount;
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    this.variableDescriptor = variableDescriptor;
  }

  private static Object[] snapshotOldValues(List<Object> listValue, int fromIndex, int toIndex) {
    var size = toIndex - fromIndex;
    if (size == 0) {
      return EMPTY_OLD_VALUES;
    }
    var oldValueArray = new Object[size];
    for (var i = 0; i < size; i++) {
      oldValueArray[i] = listValue.get(fromIndex + i);
    }
    return oldValueArray;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void undo(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var valueList = (List<Value_>) variableDescriptor.getValue(entity);
    if (oldValueCount == 1) {
      valueList.add(fromIndex, (Value_) oldSingleValue);
    } else if (oldValueCount > 1) {
      valueList.addAll(fromIndex, (List<Value_>) oldValueView);
    } else {
      // No values to restore.
    }
    scoreDirector.afterListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
  }

  @Override
  @SuppressWarnings("unchecked")
  public ChangeAction<Solution_> rebase(Lookup lookup) {
    Object[] rebasedOldValueArray;
    Object rebasedOldSingleValue;
    List<Object> rebasedOldValueView;
    if (oldValueCount == 0) {
      rebasedOldValueArray = EMPTY_OLD_VALUES;
      rebasedOldSingleValue = null;
      rebasedOldValueView = List.of();
    } else if (oldValueCount == 1) {
      rebasedOldValueArray = EMPTY_OLD_VALUES;
      rebasedOldSingleValue = lookup.lookUpWorkingObject(oldSingleValue);
      rebasedOldValueView = new ImmutableArraySliceView<>(new Object[] {rebasedOldSingleValue}, 1);
    } else {
      rebasedOldValueArray = new Object[oldValueCount];
      for (var i = 0; i < oldValueCount; i++) {
        rebasedOldValueArray[i] = lookup.lookUpWorkingObject(oldValueArray[i]);
      }
      rebasedOldSingleValue = null;
      rebasedOldValueView = new ImmutableArraySliceView<>(rebasedOldValueArray, oldValueCount);
    }
    return new ListVariableBeforeChangeAction<>(
        (Entity_) lookup.lookUpWorkingObject(entity),
        rebasedOldSingleValue,
        rebasedOldValueArray,
        rebasedOldValueView,
        oldValueCount,
        fromIndex,
        toIndex,
        variableDescriptor);
  }

  public Entity_ entity() {
    return entity;
  }

  @SuppressWarnings("unchecked")
  public List<Value_> oldValue() {
    return (List<Value_>) oldValueView;
  }

  public int fromIndex() {
    return fromIndex;
  }

  public int toIndex() {
    return toIndex;
  }

  public ListVariableDescriptor<Solution_> variableDescriptor() {
    return variableDescriptor;
  }

  @Override
  public String toString() {
    return "ListVariableBeforeChangeAction[entity=%s, oldValue=%s, fromIndex=%d, toIndex=%d, variableDescriptor=%s]"
        .formatted(entity, oldValue(), fromIndex, toIndex, variableDescriptor);
  }

  private static final class ImmutableArraySliceView<Value_> extends AbstractList<Value_>
      implements RandomAccess {

    private final Object[] values;
    private final int size;

    private ImmutableArraySliceView(Object[] values, int size) {
      this.values = values;
      this.size = size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Value_ get(int index) {
      if (index < 0 || index >= size) {
        throw new IndexOutOfBoundsException(index);
      }
      return (Value_) values[index];
    }

    @Override
    public int size() {
      return size;
    }
  }
}
