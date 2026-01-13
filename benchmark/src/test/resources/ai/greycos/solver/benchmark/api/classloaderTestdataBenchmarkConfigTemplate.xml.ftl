<?xml version="1.0" encoding="UTF-8"?>
<plannerBenchmark>
  <benchmarkDirectory>target/benchmarkTest/output</benchmarkDirectory>
  <warmUpSecondsSpentLimit>0</warmUpSecondsSpentLimit>
<#list ['FIRST_FIT', 'CHEAPEST_INSERTION'] as constructionHeuristicType>
  <solverBenchmark>
    <problemBenchmarks>
      <solutionFileIOClass>ai.greycos.solver.persistence.common.api.cotwin.solution.RigidTestdataSolutionFileIO</solutionFileIOClass>
      <inputSolutionFile>target/test/benchmarkTest/input.xml</inputSolutionFile>
    </problemBenchmarks>
    <solver>
      <!-- Using these classnames doesn't work because the className still differs from class.getName()-->
      <!--<solutionClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testcotwin.TestdataSolution</solutionClass>-->
      <!--<entityClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testcotwin.TestdataEntity</entityClass>-->
      <solutionClass>ai.greycos.solver.core.testcotwin.TestdataSolution</solutionClass>
      <entityClass>ai.greycos.solver.core.testcotwin.TestdataEntity</entityClass>

      <!-- Score configuration -->
      <scoreDirectorFactory>
        <!-- Using these classnames doesn't work because the className still differs from class.getName()-->
        <!-- <constraintProviderClass>divertThroughClassLoader.ai.greycos.solver.core.impl.testdata.testcotwin.TestdataConstraintProvider</constraintProviderClass>-->
        <constraintProviderClass>ai.greycos.solver.core.testcotwin.TestdataConstraintProvider</constraintProviderClass>
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
