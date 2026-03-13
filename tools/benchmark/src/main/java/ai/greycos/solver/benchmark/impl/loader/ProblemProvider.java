package ai.greycos.solver.benchmark.impl.loader;

import jakarta.xml.bind.annotation.XmlSeeAlso;

import ai.greycos.solver.benchmark.impl.result.SubSingleBenchmarkResult;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;

/**
 * Subclasses need to implement {@link Object#equals(Object) equals()} and {@link Object#hashCode()
 * hashCode()} which are used by {@link
 * ai.greycos.solver.benchmark.impl.ProblemBenchmarksFactory#buildProblemBenchmarkList}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@XmlSeeAlso({InstanceProblemProvider.class, FileProblemProvider.class})
public interface ProblemProvider<Solution_> {

  /**
   * @return never null
   */
  String getProblemName();

  /**
   * @return never null
   */
  Solution_ readProblem();

  /**
   * @param solution never null
   * @param subSingleBenchmarkResult never null
   */
  void writeSolution(Solution_ solution, SubSingleBenchmarkResult subSingleBenchmarkResult);
}
