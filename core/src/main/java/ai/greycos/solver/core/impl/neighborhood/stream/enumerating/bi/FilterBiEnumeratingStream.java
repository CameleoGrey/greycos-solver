package ai.greycos.solver.core.impl.neighborhood.stream.enumerating.bi;

import java.util.Objects;

import ai.greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.EnumeratingStreamFactory;
import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.common.DataNodeBuildHelper;
import ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating.function.BiEnumeratingPredicate;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class FilterBiEnumeratingStream<Solution_, A, B>
    extends AbstractBiEnumeratingStream<Solution_, A, B> {

  private final BiEnumeratingPredicate<Solution_, A, B> filter;

  public FilterBiEnumeratingStream(
      EnumeratingStreamFactory<Solution_> enumeratingStreamFactory,
      AbstractBiEnumeratingStream<Solution_, A, B> parent,
      BiEnumeratingPredicate<Solution_, A, B> filter) {
    super(enumeratingStreamFactory, parent);
    this.filter = Objects.requireNonNull(filter, "The filter cannot be null.");
  }

  @Override
  public void buildNode(DataNodeBuildHelper<Solution_> buildHelper) {
    var predicate = filter.toBiPredicate(buildHelper.getSessionContext().solutionView());
    buildHelper.<BiTuple<A, B>>putInsertUpdateRetract(
        this,
        childStreamList,
        tupleLifecycle -> TupleLifecycle.conditionally(tupleLifecycle, predicate));
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, filter);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof FilterBiEnumeratingStream<?, ?, ?> other
        && parent == other.parent
        && filter == other.filter;
  }

  @Override
  public String toString() {
    return "Filter() with " + childStreamList.size() + " children";
  }
}
