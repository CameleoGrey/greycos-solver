package ai.greycos.solver.core.testcotwin.clone.cloneable;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import ai.greycos.solver.core.impl.cotwin.solution.cloner.PlanningCloneable;

public class PlanningCloneableMap<K, V> extends AbstractMap<K, V>
    implements PlanningCloneable<PlanningCloneableMap<K, V>> {
  private final Map<K, V> backingMap;

  public PlanningCloneableMap() {
    this.backingMap = new HashMap<>();
  }

  @Override
  public PlanningCloneableMap<K, V> createNewInstance() {
    return new PlanningCloneableMap<>();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return backingMap.entrySet();
  }

  @Override
  public V put(K key, V value) {
    return backingMap.put(key, value);
  }
}
