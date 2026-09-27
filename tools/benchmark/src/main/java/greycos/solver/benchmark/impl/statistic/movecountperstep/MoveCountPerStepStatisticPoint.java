package greycos.solver.benchmark.impl.statistic.movecountperstep;

import greycos.solver.benchmark.impl.statistic.StatisticPoint;

public class MoveCountPerStepStatisticPoint extends StatisticPoint {

  private final long timeMillisSpent;
  private final long acceptedMoveCount;
  private final long selectedMoveCount;

  public MoveCountPerStepStatisticPoint(
      long timeMillisSpent, long acceptedMoveCount, long selectedMoveCount) {
    this(timeMillisSpent, acceptedMoveCount, selectedMoveCount, null);
  }

  public MoveCountPerStepStatisticPoint(
      long timeMillisSpent, long acceptedMoveCount, long selectedMoveCount, String source) {
    super(source);
    this.timeMillisSpent = timeMillisSpent;
    this.acceptedMoveCount = acceptedMoveCount;
    this.selectedMoveCount = selectedMoveCount;
  }

  public long getTimeMillisSpent() {
    return timeMillisSpent;
  }

  public long getAcceptedMoveCount() {
    return acceptedMoveCount;
  }

  public long getSelectedMoveCount() {
    return selectedMoveCount;
  }

  @Override
  public String toCsvLine() {
    return buildCsvLineWithLongs(timeMillisSpent, acceptedMoveCount, selectedMoveCount);
  }
}
