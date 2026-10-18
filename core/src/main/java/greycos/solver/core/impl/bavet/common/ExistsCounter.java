package greycos.solver.core.impl.bavet.common;

import java.util.IdentityHashMap;

import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleState;

import org.jspecify.annotations.Nullable;

public final class ExistsCounter<Tuple_ extends Tuple>
    extends AbstractPropagationMetadataCarrier<Tuple_> {

  final Tuple_ leftTuple;
  TupleState state = TupleState.DEAD; // It's the node's job to mark a new instance as CREATING.
  int countRight = 0;
  @Nullable Provenance provenance;

  ExistsCounter(Tuple_ leftTuple) {
    this.leftTuple = leftTuple;
  }

  @Override
  public Tuple_ getTuple() {
    return leftTuple;
  }

  @Override
  public TupleState getState() {
    return state;
  }

  @Override
  public void setState(TupleState state) {
    this.state = state;
  }

  @Override
  public String toString() {
    return "Counter(" + leftTuple + ")";
  }

  /** Allocated only for an explicitly enabled provenance session. */
  static final class Provenance {
    final LineageHandle output;
    final @Nullable LineageHandle witnesses;
    @Nullable IdentityHashMap<Tuple, LineageHandle> witnessHandles;
    @Nullable WitnessGroup group;

    Provenance(LineageHandle left, boolean shouldExist) {
      witnesses = shouldExist ? LineageHandle.mutable() : null;
      output = shouldExist ? LineageHandle.union(left, witnesses) : LineageHandle.union(left);
    }
  }

  /** Shared only by non-filtering counters with the same complete equality key. */
  static final class WitnessGroup {
    final Object key;
    final LineageHandle witnesses = LineageHandle.mutable();
    int leftCount;
    int rightCount;

    WitnessGroup(Object key) {
      this.key = key;
    }
  }
}
