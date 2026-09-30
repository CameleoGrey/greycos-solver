package greycos.solver.core.impl.score.stream.collector;

import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

/** Common identity and state creation for the native floating-point collectors. */
public abstract class AbstractFloatingPointCollector<Mapper_> {

  protected final Mapper_ mapper;
  protected final boolean average;

  protected AbstractFloatingPointCollector(Mapper_ mapper, boolean average) {
    this.mapper = Objects.requireNonNull(mapper);
    this.average = average;
  }

  public final @NonNull Supplier<AbstractFloatingPointSlot.State> supplier() {
    return AbstractFloatingPointSlot.State::new;
  }

  @Override
  public final boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var that = (AbstractFloatingPointCollector<?>) object;
    return average == that.average && Objects.equals(mapper, that.mapper);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(mapper, average);
  }
}
