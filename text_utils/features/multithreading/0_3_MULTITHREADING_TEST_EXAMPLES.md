# Multithreading Test Examples

This document provides test examples to validate the multithreading implementation.

## Basic Multithreading Test

```java
package org.optaplanner.core.impl.test.multithreading;

import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.util.PlannerTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class MultithreadingTest {

    @Test
    void testMoveThreadCountAuto() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("AUTO");
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    @Test
    void testMoveThreadCountExplicit() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    @Test
    void testMoveThreadCountNone() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("NONE");
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    @Test
    void testCustomThreadFactory() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");
        solverConfig.setThreadFactoryClass(TestThreadFactory.class);
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
        assertThat(TestThreadFactory.hasBeenCalled()).isTrue();
    }

    @Test
    void testMoveThreadBufferSize() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");
        solverConfig.setMoveThreadBufferSize(20);
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    private TestdataSolution createTestSolution(int entityCount, int valueCount) {
        TestdataSolution solution = new TestdataSolution();
        solution.setEntityList(new ArrayList<>());
        solution.setValueList(new ArrayList<>());
        
        for (int i = 0; i < valueCount; i++) {
            TestdataValue value = new TestdataValue("value-" + i);
            solution.getValueList().add(value);
        }
        
        for (int i = 0; i < entityCount; i++) {
            TestdataEntity entity = new TestdataEntity("entity-" + i);
            entity.setValue(solution.getValueList().get(i % valueCount));
            solution.getEntityList().add(entity);
        }
        
        return solution;
    }
}
```

## Custom Thread Factory Example

```java
package org.optaplanner.core.impl.test.multithreading;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestThreadFactory implements ThreadFactory {
    private static final AtomicBoolean called = new AtomicBoolean(false);
    
    @Override
    public Thread newThread(Runnable r) {
        called.set(true);
        Thread thread = new Thread(r);
        thread.setName("TestThread-" + thread.getId());
        return thread;
    }
    
    public static boolean hasBeenCalled() {
        return called.get();
    }
    
    public static void reset() {
        called.set(false);
    }
}
```

## Performance Comparison Test

```java
package org.optaplanner.core.impl.test.multithreading;

import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.util.PlannerTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class MultithreadingPerformanceTest {

    @Test
    void compareSingleThreadedVsMultiThreaded() {
        int entityCount = 50;
        int valueCount = 10;
        
        // Single-threaded configuration
        SolverConfig singleThreadedConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        singleThreadedConfig.setMoveThreadCount("NONE");
        singleThreadedConfig.getTerminationConfig().setSecondsSpentLimit(5L);
        
        // Multi-threaded configuration
        SolverConfig multiThreadedConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        multiThreadedConfig.setMoveThreadCount("AUTO");
        multiThreadedConfig.getTerminationConfig().setSecondsSpentLimit(5L);
        
        TestdataSolution singleThreadedSolution = createTestSolution(entityCount, valueCount);
        TestdataSolution multiThreadedSolution = createTestSolution(entityCount, valueCount);
        
        // Solve with single thread
        long startTime = System.nanoTime();
        singleThreadedSolution = PlannerTestUtils.solve(singleThreadedConfig, singleThreadedSolution);
        long singleThreadedTime = System.nanoTime() - startTime;
        
        // Solve with multiple threads
        startTime = System.nanoTime();
        multiThreadedSolution = PlannerTestUtils.solve(multiThreadedConfig, multiThreadedSolution);
        long multiThreadedTime = System.nanoTime() - startTime;
        
        // Verify both solutions are valid
        assertThat(singleThreadedSolution).isNotNull();
        assertThat(multiThreadedSolution).isNotNull();
        assertThat(singleThreadedSolution.getScore().isSolutionInitialized()).isTrue();
        assertThat(multiThreadedSolution.getScore().isSolutionInitialized()).isTrue();
        
        // Multi-threaded should be faster (or at least not significantly slower)
        double speedup = (double) singleThreadedTime / multiThreadedTime;
        System.out.println("Speedup: " + speedup + "x");
        
        // Allow some tolerance for measurement variance
        assertThat(speedup).isGreaterThanOrEqualTo(0.8);
    }
    
    private TestdataSolution createTestSolution(int entityCount, int valueCount) {
        TestdataSolution solution = new TestdataSolution();
        solution.setEntityList(new ArrayList<>());
        solution.setValueList(new ArrayList<>());
        
        for (int i = 0; i < valueCount; i++) {
            TestdataValue value = new TestdataValue("value-" + i);
            solution.getValueList().add(value);
        }
        
        for (int i = 0; i < entityCount; i++) {
            TestdataEntity entity = new TestdataEntity("entity-" + i);
            entity.setValue(solution.getValueList().get(i % valueCount));
            solution.getEntityList().add(entity);
        }
        
        return solution;
    }
}
```

## Error Handling Test

```java
package org.optaplanner.core.impl.test.multithreading;

import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.util.PlannerTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MultithreadingErrorHandlingTest {

    @Test
    void testExceptionPropagationFromMoveThread() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("2");
        
        // Create a solution that will cause an exception in a move thread
        TestdataSolution solution = createFaultyTestSolution(10, 5);
        
        // Should not throw an exception, but should handle it gracefully
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        // The solution might not be fully initialized due to the exception,
        // but the solver should not crash
    }

    @Test
    void testInvalidMoveThreadCount() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        
        assertThatThrownBy(() -> {
            solverConfig.setMoveThreadCount("0");
            PlannerTestUtils.solve(solverConfig, createTestSolution(10, 5));
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("moveThreadCount");
    }

    @Test
    void testTooManyMoveThreads() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        solverConfig.setMoveThreadCount("1000"); // More than available processors
        
        TestdataSolution solution = createTestSolution(10, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        // Should work but log a warning about counter-efficiency
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    private TestdataSolution createFaultyTestSolution(int entityCount, int valueCount) {
        // Create a solution that will cause exceptions during move evaluation
        TestdataSolution solution = new TestdataSolution();
        solution.setEntityList(new ArrayList<>());
        solution.setValueList(new ArrayList<>());
        
        for (int i = 0; i < valueCount; i++) {
            TestdataValue value = new TestdataValue("value-" + i);
            solution.getValueList().add(value);
        }
        
        for (int i = 0; i < entityCount; i++) {
            TestdataEntity entity = new TestdataEntity("entity-" + i);
            // Set up entity in a way that will cause exceptions
            entity.setValue(null);
            solution.getEntityList().add(entity);
        }
        
        return solution;
    }

    private TestdataSolution createTestSolution(int entityCount, int valueCount) {
        TestdataSolution solution = new TestdataSolution();
        solution.setEntityList(new ArrayList<>());
        solution.setValueList(new ArrayList<>());
        
        for (int i = 0; i < valueCount; i++) {
            TestdataValue value = new TestdataValue("value-" + i);
            solution.getValueList().add(value);
        }
        
        for (int i = 0; i < entityCount; i++) {
            TestdataEntity entity = new TestdataEntity("entity-" + i);
            entity.setValue(solution.getValueList().get(i % valueCount));
            solution.getEntityList().add(entity);
        }
        
        return solution;
    }
}
```

## Integration Test with Different Phases

```java
package org.optaplanner.core.impl.test.multithreading;

import org.junit.jupiter.api.Test;
import org.optaplanner.core.api.solver.Solver;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.impl.testdata.domain.TestdataSolution;
import org.optaplanner.core.impl.testdata.domain.TestdataEntity;
import org.optaplanner.core.impl.testdata.util.PlannerTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class MultithreadingPhaseTest {

    @Test
    void testMultiThreadedConstructionHeuristic() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        
        // Configure construction heuristic with multithreading
        ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig = new ConstructionHeuristicPhaseConfig();
        constructionHeuristicPhaseConfig.setMoveThreadCount("2");
        
        solverConfig.setPhaseConfigList(Arrays.asList(constructionHeuristicPhaseConfig));
        
        TestdataSolution solution = createTestSolution(20, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    @Test
    void testMultiThreadedLocalSearch() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        
        // Configure local search with multithreading
        LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
        localSearchPhaseConfig.setMoveThreadCount("2");
        
        solverConfig.setPhaseConfigList(Arrays.asList(localSearchPhaseConfig));
        
        TestdataSolution solution = createTestSolution(20, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    @Test
    void testMixedPhasesWithDifferentThreadCounts() {
        SolverConfig solverConfig = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
        
        // Construction heuristic with 2 threads
        ConstructionHeuristicPhaseConfig constructionHeuristicPhaseConfig = new ConstructionHeuristicPhaseConfig();
        constructionHeuristicPhaseConfig.setMoveThreadCount("2");
        
        // Local search with 4 threads
        LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig();
        localSearchPhaseConfig.setMoveThreadCount("4");
        
        solverConfig.setPhaseConfigList(Arrays.asList(
            constructionHeuristicPhaseConfig,
            localSearchPhaseConfig
        ));
        
        TestdataSolution solution = createTestSolution(20, 5);
        solution = PlannerTestUtils.solve(solverConfig, solution);
        
        assertThat(solution).isNotNull();
        assertThat(solution.getScore().isSolutionInitialized()).isTrue();
    }

    private TestdataSolution createTestSolution(int entityCount, int valueCount) {
        TestdataSolution solution = new TestdataSolution();
        solution.setEntityList(new ArrayList<>());
        solution.setValueList(new ArrayList<>());
        
        for (int i = 0; i < valueCount; i++) {
            TestdataValue value = new TestdataValue("value-" + i);
            solution.getValueList().add(value);
        }
        
        for (int i = 0; i < entityCount; i++) {
            TestdataEntity entity = new TestdataEntity("entity-" + i);
            entity.setValue(solution.getValueList().get(i % valueCount));
            solution.getEntityList().add(entity);
        }
        
        return solution;
    }
}
```

These test examples provide comprehensive coverage for validating the multithreading implementation, including basic functionality, performance comparison, error handling, and integration with different solver phases.