package ai.greycos.solver.core.testcotwin.clone.cloneable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.impl.cotwin.solution.cloner.PlanningCloneable;

public class PlanningCloneableList<T> extends AbstractList<T>
    implements PlanningCloneable<PlanningCloneableList<T>> {
  private final List<T> backingList;

  public PlanningCloneableList() {
    this.backingList = new ArrayList<>();
  }

  @Override
  public PlanningCloneableList<T> createNewInstance() {
    return new PlanningCloneableList<>();
  }

  @Override
  public T get(int i) {
    return backingList.get(i);
  }

  @Override
  public T set(int i, T item) {
    return backingList.set(i, item);
  }

  @Override
  public void add(int i, T item) {
    backingList.add(i, item);
  }

  @Override
  public T remove(int i) {
    return backingList.remove(i);
  }

  @Override
  public int size() {
    return backingList.size();
  }
}
