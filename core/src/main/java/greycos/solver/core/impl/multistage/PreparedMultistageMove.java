package greycos.solver.core.impl.multistage;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.move.InnerMutableSolutionView;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

/** Immutable mutation journal selected by one completed multistage evaluation. */
public final class PreparedMultistageMove<Solution_> implements Move<Solution_> {
  private final List<List<ResolvedMutation<Solution_>>> operations;
  private final String description;

  PreparedMultistageMove(List<List<ResolvedMutation<Solution_>>> operations, String description) {
    this.operations = operations.stream().map(List::copyOf).toList();
    this.description = description;
  }

  @Override
  public void execute(MutableSolutionView<Solution_> view) {
    var director = ((InnerMutableSolutionView<Solution_>) view).getScoreDirector();
    for (var operation : operations) {
      ResolvedMutation.applyAll(operation, director);
      director.updateShadowVariables();
    }
  }

  @Override
  public Move<Solution_> rebase(Lookup lookup) {
    return new PreparedMultistageMove<>(
        operations.stream()
            .map(operation -> operation.stream().map(mutation -> mutation.rebase(lookup)).toList())
            .toList(),
        description);
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    var entities = new LinkedHashSet<Object>();
    operations.forEach(operation -> operation.forEach(mutation -> entities.add(mutation.entity())));
    return entities;
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    var values = new LinkedHashSet<Object>();
    operations.forEach(
        operation ->
            operation.forEach(
                mutation -> {
                  if (mutation.fromIndex() < 0) values.add(mutation.value());
                  else {
                    values.addAll(mutation.inserted());
                    values.addAll(mutation.unassigned());
                  }
                }));
    return values;
  }

  @Override
  public String describe() {
    return description;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof PreparedMultistageMove<?> that
        && description.equals(that.description)
        && operations.equals(that.operations);
  }

  @Override
  public int hashCode() {
    return 31 * description.hashCode() + operations.hashCode();
  }

  @Override
  public String toString() {
    return description + operations;
  }
}
