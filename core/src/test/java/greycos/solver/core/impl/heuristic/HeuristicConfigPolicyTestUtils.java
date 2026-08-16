package greycos.solver.core.impl.heuristic;

import java.util.Random;

import greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.solver.ClassInstanceCache;
import greycos.solver.core.impl.solver.random.MockRandomSource;
import greycos.solver.core.testcotwin.TestdataSolution;

public final class HeuristicConfigPolicyTestUtils {

  public static HeuristicConfigPolicy<TestdataSolution> buildHeuristicConfigPolicy() {
    return buildHeuristicConfigPolicy(TestdataSolution.buildSolutionDescriptor());
  }

  public static <Solution_> HeuristicConfigPolicy<Solution_> buildHeuristicConfigPolicy(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    return buildHeuristicConfigPolicy(solutionDescriptor, null);
  }

  public static <Solution_> HeuristicConfigPolicy<Solution_> buildHeuristicConfigPolicy(
      SolutionDescriptor<Solution_> solutionDescriptor, EntitySorterManner entitySorterManner) {
    return new HeuristicConfigPolicy.Builder<Solution_>()
        .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
        .withRandom(new MockRandomSource(new Random(0)))
        .withSolutionDescriptor(solutionDescriptor)
        .withClassInstanceCache(ClassInstanceCache.create())
        .withEntitySorterManner(entitySorterManner)
        .build();
  }

  private HeuristicConfigPolicyTestUtils() {}
}
