package greycos.solver.math.impl.cotwin;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A solve-local assignment; all mathematical definitions remain immutable. */
@PlanningEntity
@NullMarked
public final class MathDecision {

  private final int id;
  private final ValueRange<Long> valueRange;
  private final boolean pinned;
  private @Nullable Long value;

  public MathDecision() {
    this(0, new InclusiveLongValueRange(0, 0), null, true);
  }

  public MathDecision(int id, long lower, long upper, long value, boolean pinned) {
    this(id, new InclusiveLongValueRange(lower, upper), value, pinned);
  }

  MathDecision(int id, ValueRange<Long> valueRange, @Nullable Long value, boolean pinned) {
    this.id = id;
    this.valueRange = valueRange;
    this.value = value;
    this.pinned = pinned;
  }

  @PlanningId
  public int getId() {
    return id;
  }

  @PlanningVariable(valueRangeProviderRefs = "mathDomain")
  public @Nullable Long getValue() {
    return value;
  }

  public void setValue(@Nullable Long value) {
    this.value = value;
  }

  @ValueRangeProvider(id = "mathDomain")
  public ValueRange<Long> getValueRange() {
    return valueRange;
  }

  @PlanningPin
  public boolean isPinned() {
    return pinned;
  }

  MathDecision copy() {
    return new MathDecision(id, valueRange, value, pinned);
  }

  @Override
  public String toString() {
    return "Decision(%d=%s)".formatted(id, value);
  }
}
