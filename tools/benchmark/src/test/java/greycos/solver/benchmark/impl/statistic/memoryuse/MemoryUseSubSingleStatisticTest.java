package greycos.solver.benchmark.impl.statistic.memoryuse;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import greycos.solver.benchmark.impl.statistic.AbstractSubSingleStatisticTest;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.assertj.core.api.SoftAssertions;

public final class MemoryUseSubSingleStatisticTest
    extends AbstractSubSingleStatisticTest<
        MemoryUseStatisticPoint, MemoryUseSubSingleStatistic<TestdataSolution>> {

  private static final long USED_MEMORY = 123_456_789L;
  private static final long MAX_MEMORY = 987_654_321L;

  @Override
  protected Function<SubSingleBenchmarkResult, MemoryUseSubSingleStatistic<TestdataSolution>>
      getSubSingleStatisticConstructor() {
    return MemoryUseSubSingleStatistic::new;
  }

  @Override
  protected List<MemoryUseStatisticPoint> getInputPoints() {
    return Collections.singletonList(
        new MemoryUseStatisticPoint(Long.MAX_VALUE, USED_MEMORY, MAX_MEMORY));
  }

  @Override
  protected void runTest(SoftAssertions assertions, List<MemoryUseStatisticPoint> outputPoints) {
    assertions
        .assertThat(outputPoints)
        .hasSize(1)
        .first()
        .matches(s -> s.getUsedMemory() == USED_MEMORY, "Used memory does not match.")
        .matches(s -> s.getMaxMemory() == MAX_MEMORY, "Max memory does not match.")
        .matches(s -> s.getTimeMillisSpent() == Long.MAX_VALUE, "Millis do not match.");
  }
}
