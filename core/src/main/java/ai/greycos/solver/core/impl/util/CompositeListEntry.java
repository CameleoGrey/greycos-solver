package ai.greycos.solver.core.impl.util;

import java.util.List;

import ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType;

import org.jspecify.annotations.NullMarked;

/**
 * Allows storing the same tuple in multiple downstream indexers. Needed by {@link
 * JoinerType#CONTAINING}, {@link JoinerType#CONTAINING_ANY_OF} and related joiners.
 */
@NullMarked
public record CompositeListEntry<Key_, T>(T element, List<Pair<Key_, ListEntry<T>>> children)
    implements ListEntry<T> {

  @Override
  public boolean isRemoved() {
    return children.stream().allMatch(child -> child.value().isRemoved());
  }

  @Override
  public T getElement() {
    return element;
  }

  @Override
  public String toString() {
    return element.toString();
  }
}
