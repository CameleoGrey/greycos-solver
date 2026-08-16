package greycos.solver.core.impl.phase.event;

import java.util.OptionalInt;

import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.impl.phase.PhaseType;

import org.jspecify.annotations.NullMarked;

/** {@link EventProducerId} for when a {@link BestSolutionChangedEvent} is caused by a phase. */
@NullMarked
public record PhaseEventProducerId(PhaseType phaseType, int index) implements EventProducerId {
  @Override
  public String producerId() {
    return "%s (%d)".formatted(phaseType.getPhaseName(), index);
  }

  @Override
  public String simpleProducerName() {
    return phaseType.getPhaseName();
  }

  @Override
  public OptionalInt phaseIndex() {
    return OptionalInt.of(index);
  }

  @Override
  public String toString() {
    return producerId();
  }
}
