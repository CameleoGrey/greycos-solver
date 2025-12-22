<?xml version="1.0" encoding="UTF-8"?>
<plannerBenchmark>
  <benchmarkDirectory>target/benchmarkTest/output</benchmarkDirectory>
  <warmUpSecondsSpentLimit>0</warmUpSecondsSpentLimit>
<#list ['FIRST_FIT', 'CHEAPEST_INSERTION'] as constructionHeuristicType>
  <solverBenchmark>
    <problemBenchmarks>
      <solutionFileIOClass>ai.greycos.solver.persistence.common.api.domain.solution.RigidTestdataSolutionFileIO</solutionFileIOClass>
      <inputSolutionFile>target/test/benchmarkTest/input.xml</inputSolutionFile>
    </problemBenchmarks>
    <solver>
      <!-- Using these classnames doesn't work because the className still differs from class.getName()-->
      <!--<solutionClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testdomain.TestdataSolution</solutionClass>-->
      <!--<entityClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testdomain.TestdataEntity</entityClass>-->
      <solutionClass>ai.greycos.solver.core.testdomain.TestdataSolution</solutionClass>
      <entityClass>ai.greycos.solver.core.testdomain.TestdataEntity</entityClass>

      <!-- Score configuration -->
      <scoreDirectorFactory>
        <!-- Using these classnames doesn't work because the className still differs from class.getName()-->
        <!-- <constraintProviderClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testdomain.TestdataConstraintProvider</constraintProviderClass>-->
        <constraintProviderClass>ai.greycos.solver.core.testdomain.TestdataConstraintProvider</constraintProviderClass>
      </scoreDirectorFactory>
      <termination>
        <secondsSpentLimit>0</secondsSpentLimit>
      </termination>
      <constructionHeuristic>
        <constructionHeuristicType>${constructionHeuristicType}</constructionHeuristicType>
      </constructionHeuristic>
    </solver>
  </solverBenchmark>
</#list>
</plannerBenchmark>
