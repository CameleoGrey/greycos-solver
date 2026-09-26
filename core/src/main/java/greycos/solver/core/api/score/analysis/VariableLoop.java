package greycos.solver.core.api.score.analysis;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.NullMarked;

/**
 * A set of entity-variable pairs that form a cycle.
 *
 * @param involvedVariableSet
 */
@NullMarked
public record VariableLoop(Set<EntityVariablePair> involvedVariableSet) {
  public VariableLoop {
    involvedVariableSet = Collections.unmodifiableSet(new LinkedHashSet<>(involvedVariableSet));
  }

  /** Get the set of involved entities in the cycle */
  @SuppressWarnings("unchecked")
  public <T> Set<T> entitySet() {
    return (Set<T>)
        involvedVariableSet.stream().map(EntityVariablePair::entity).collect(Collectors.toSet());
  }

  @Override
  public String toString() {
    return involvedVariableSet.stream()
        .map(EntityVariablePair::toString)
        .collect(Collectors.joining(", ", "[", "]"));
  }
}
