package greycos.solver.core.impl.cotwin.solution.descriptor;

import java.util.List;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.ListValueRangeStatistics;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.util.MathUtils;

public class ProblemScaleTracker<Solution_> {
  private final long logBase;
  private final List<ListValueRangeStatistics<Solution_>> listValueRangeStatistics;
  private long basicProblemScaleLog = 0L;
  private long cachedTotalProblemScaleLog = -1L;

  public ProblemScaleTracker(
      List<ListVariableDescriptor<Solution_>> listVariableDescriptors,
      ValueRangeManager<Solution_> valueRangeManager,
      long logBase) {
    this.logBase = logBase;
    this.listValueRangeStatistics =
        listVariableDescriptors.stream()
            .map(descriptor -> new ListValueRangeStatistics<>(descriptor, valueRangeManager))
            .toList();
  }

  public long getProblemScaleLog() {
    if (cachedTotalProblemScaleLog != -1L) {
      return cachedTotalProblemScaleLog;
    }
    cachedTotalProblemScaleLog = basicProblemScaleLog;
    for (var statistics : listValueRangeStatistics) {
      cachedTotalProblemScaleLog += statistics.computeListProblemScaleLog(logBase);
    }
    return cachedTotalProblemScaleLog;
  }

  public void addBasicProblemScale(long count) {
    if (count == 0) {
      // Log(0) = -infinity; also an invalid problem (since there are no
      // valid values for the variable, including null)
      return;
    }
    basicProblemScaleLog +=
        MathUtils.getScaledApproximateLog(MathUtils.LOG_PRECISION, logBase, count);
  }
}
