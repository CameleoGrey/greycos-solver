package greycos.solver.core.impl.move.builtin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.move.AbstractMove;
import greycos.solver.core.impl.move.PlanningEntityResolver;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningListVariableMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;
import greycos.solver.core.preview.api.move.MutableSolutionView;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Gathers every member of a {@link Sample} of a {@link PlanningListVariable list variable} -
 * wherever each one currently is, assigned or not and inserts them consecutively, in sample
 * iteration order, at one destination position. A {@code null} destination unassigns every member
 * instead of inserting them anywhere.
 *
 * <p>This is the list-variable equivalent of {@code MassChangeMove}: an assign is a move whose
 * members currently hold no position, and an unassign is a move whose destination is {@code null},
 * so neither needs a class of its own.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Entity_> the entity type, the class with the {@link PlanningEntity} annotation
 * @param <Value_> the variable type, the type of the property with the {@link PlanningListVariable}
 *     annotation
 */
@NullMarked
public final class MassListChangeMove<Solution_, Entity_, Value_> extends AbstractMove<Solution_>
    implements PlanningEntityResolver<Solution_> {

  private final PlanningListVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel;
  private final Sample<Value_> sample;
  private final @Nullable PositionInList destination;
  private @Nullable List<Object> planningEntities;

  public MassListChangeMove(
      PlanningListVariableMetaModel<Solution_, Entity_, Value_> variableMetaModel,
      Sample<Value_> sample,
      @Nullable PositionInList destination) {
    this.variableMetaModel = Objects.requireNonNull(variableMetaModel);
    this.sample = Objects.requireNonNull(sample);
    this.destination = destination;
  }

  public Sample<Value_> getSample() {
    return sample;
  }

  public @Nullable PositionInList getDestination() {
    return destination;
  }

  @Override
  public List<PlanningListVariableMetaModel<Solution_, Entity_, Value_>> variableMetaModels() {
    return List.of(variableMetaModel);
  }

  @Override
  public void execute(MutableSolutionView<Solution_> solutionView) {
    // Capture sources before removing values; they can no longer be recovered after unassignment.
    // Refresh on every execution, including repeated temporary evaluations and committed steps.
    planningEntities = List.copyOf(resolvePlanningEntities(solutionView));
    solutionView.massMoveValues(variableMetaModel, sample, destination);
  }

  @Override
  public MassListChangeMove<Solution_, Entity_, Value_> rebase(Lookup lookup) {
    var rebasedDestination = destination == null ? null : destination.rebase(lookup);
    return new MassListChangeMove<>(variableMetaModel, sample.rebase(lookup), rebasedDestination);
  }

  @Override
  public SequencedCollection<Object> getPlanningEntities() {
    return Objects.requireNonNull(planningEntities, "The move has not been executed yet.");
  }

  @Override
  public SequencedCollection<Object> resolvePlanningEntities(SolutionView<Solution_> solutionView) {
    var entities = new LinkedHashSet<Object>();
    for (var member : sample) {
      var entity = solutionView.getEntity(variableMetaModel, Objects.requireNonNull(member));
      if (entity != null) {
        entities.add(entity);
      }
    }
    if (destination != null) {
      entities.add(destination.entity());
    }
    return entities;
  }

  @Override
  public SequencedCollection<Object> getPlanningValues() {
    var valueList = new ArrayList<>(sample.size());
    for (var member : sample) {
      valueList.add(Objects.requireNonNull(member));
    }
    return valueList;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof MassListChangeMove<?, ?, ?> other
        && Objects.equals(variableMetaModel, other.variableMetaModel)
        && Objects.equals(destination, other.destination)
        && sampleEquals(other);
  }

  // Insertion is order-sensitive (execute() gathers members in sample iteration order),
  // so equality must be too: Move requires that equal moves produce the exact same solution.
  // A null destination unassigns every member instead, where order does not affect the
  // resulting solution, so Sample's own order-insensitive equality is correct there.
  private boolean sampleEquals(MassListChangeMove<?, ?, ?> other) {
    if (sample.size() != other.sample.size()) { // Cheap check to rule out unequal samples.
      return false;
    }
    if (destination == null) {
      return Objects.equals(sample, other.sample);
    }
    var iterator = sample.iterator();
    var otherIterator = other.sample.iterator();
    while (iterator.hasNext()) {
      if (!Objects.equals(iterator.next(), otherIterator.next())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    var hash = 31 + Objects.hashCode(variableMetaModel);
    hash = hash * 31 + Objects.hashCode(destination);
    if (destination == null) {
      hash = hash * 31 + Objects.hashCode(sample);
    } else {
      for (var member : sample) {
        hash = hash * 31 + Objects.hashCode(member);
      }
    }
    return hash;
  }

  @Override
  public String toString() {
    return sample + " -> " + destination;
  }
}
