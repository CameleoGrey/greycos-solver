package greycos.solver.core.impl.islandmodel.resumable;

import java.util.OptionalInt;

import greycos.solver.core.api.solver.event.EventProducerId;

/** Logical identity of a best solution committed by a resumable hierarchy. */
public record ResumableSearchEventProducerId(
    int outerPhaseIndex, long modelRevision, long round, String sessionPath, long episode)
    implements EventProducerId {

  @Override
  public String producerId() {
    return "Resumable search ("
        + outerPhaseIndex
        + ")/revision-"
        + modelRevision
        + "/round-"
        + round
        + "/"
        + sessionPath
        + "/episode-"
        + episode;
  }

  @Override
  public String simpleProducerName() {
    return "Resumable search";
  }

  @Override
  public OptionalInt phaseIndex() {
    return OptionalInt.of(outerPhaseIndex);
  }
}
