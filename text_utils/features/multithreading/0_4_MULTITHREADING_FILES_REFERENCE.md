# Multithreading Implementation Files Reference

This document provides a comprehensive reference of all files that need to be created, modified, or referenced for implementing multithreading in OptaPlanner.

## Core Thread Infrastructure Files

### 1. Thread Type and Configuration
- **`org/optaplanner/core/impl/solver/thread/ChildThreadType.java`**
  - Enum defining thread types (MOVE_THREAD, PART_THREAD)
  - Used for thread categorization and termination coordination

### 2. Thread Factory Infrastructure
- **`org/optaplanner/core/impl/solver/thread/DefaultSolverThreadFactory.java`**
  - Default thread factory implementation
  - Creates properly named and configured threads
  - Thread pool naming convention: "OptaPool-{poolNumber}-{threadPrefix}-{threadNumber}"

- **`org/optaplanner/core/config/solver/SolverConfig.java`**
  - Add thread factory configuration fields:
    - `threadFactoryClass`
    - `moveThreadCount`
    - `moveThreadBufferSize`
  - Add getter/setter methods and with() methods
  - Add constants: `MOVE_THREAD_COUNT_NONE`, `MOVE_THREAD_COUNT_AUTO`

### 3. Thread Utilities
- **`org/optaplanner/core/impl/solver/thread/ThreadUtils.java`**
  - Thread pool management utilities
  - Safe shutdown and cleanup methods
  - Exception handling for thread operations

## Move Thread Infrastructure Files

### 4. Move Thread Operations
- **`org/optaplanner/core/impl/heuristic/thread/MoveThreadOperation.java`**
  - Base class for all move thread operations
  - Abstract operation interface

- **`org/optaplanner/core/impl/heuristic/thread/SetupOperation.java`**
  - Initialize move thread with score director
  - Create child thread score director

- **`org/optaplanner/core/impl/heuristic/thread/DestroyOperation.java`**
  - Cleanup and shutdown move thread
  - Signal thread termination

- **`org/optaplanner/core/impl/heuristic/thread/MoveEvaluationOperation.java`**
  - Evaluate move score in parallel
  - Thread-safe move evaluation

- **`org/optaplanner/core/impl/heuristic/thread/ApplyStepOperation.java`**
  - Apply step changes across all threads
  - Synchronized step application

### 5. Move Thread Runner
- **`org/optaplanner/core/impl/heuristic/thread/MoveThreadRunner.java`**
  - Core move thread implementation
  - Handles operation queue processing
  - Manages score director lifecycle
  - Exception handling and propagation

### 6. Move Result Queue
- **`org/optaplanner/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java`**
  - Thread-safe queue for move results
  - Ensures moves are processed in correct order
  - Handles exception propagation from threads

## Termination Infrastructure Files

### 7. Child Thread Termination
- **`org/optaplanner/core/impl/solver/termination/ChildThreadPlumbingTermination.java`**
  - Coordinates child thread termination
  - Handles early termination requests
  - Thread-safe termination signaling

### 8. Phase-to-Solver Termination Bridge
- **`org/optaplanner/core/impl/solver/termination/PhaseToSolverTerminationBridge.java`**
  - Bridges phase termination to solver termination
  - Handles child thread termination delegation
  - Critical for proper termination coordination

## Multithreaded Deciders

### 9. Local Search Multithreading
- **`org/optaplanner/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java`**
  - Multithreaded local search implementation
  - Manages move thread pool
  - Handles move selection and evaluation
  - Integrates with existing local search infrastructure

### 10. Construction Heuristic Multithreading
- **`org/optaplanner/core/impl/constructionheuristic/decider/MultiThreadedConstructionHeuristicDecider.java`**
  - Multithreaded construction heuristic implementation
  - Similar structure to local search decider
  - Handles construction heuristic move evaluation

## Configuration and Policy Files

### 11. Heuristic Configuration Policy
- **`org/optaplanner/core/impl/heuristic/HeuristicConfigPolicy.java`**
  - Add move thread configuration support
  - Add thread factory builder method
  - Add getters for move thread settings
  - Thread type-specific thread factory creation

### 12. Move Thread Count Resolution
- **`org/optaplanner/core/impl/solver/DefaultSolverFactory.java`**
  - Add `MoveThreadCountResolver` inner class
  - Implement move thread count resolution logic
  - Handle AUTO, explicit counts, and NONE
  - CPU count detection and validation

## Phase Factory Updates

### 13. Local Search Phase Factory
- **`org/optaplanner/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java`**
  - Update `buildDecider()` method
  - Create multithreaded decider when moveThreadCount > 0
  - Configure thread factory and buffer sizes
  - Set up proper assertion flags

### 14. Construction Heuristic Phase Factory
- **`org/optaplanner/core/impl/constructionheuristic/DefaultConstructionHeuristicPhaseFactory.java`**
  - Update `buildDecider()` method
  - Similar structure to local search factory
  - Handle construction heuristic specific requirements

## Score Director Support

### 15. Inner Score Director
- **`org/optaplanner/core/impl/score/director/InnerScoreDirector.java`**
  - Add `createChildThreadScoreDirector()` method
  - Support child thread score director creation
  - Handle child thread-specific configuration

### 16. Abstract Score Director
- **`org/optaplanner/core/impl/score/director/AbstractScoreDirector.java`**
  - Add child thread type parameter
  - Support child thread score director creation
  - Handle child thread lifecycle

## Termination Support Files

### 17. Termination Interface Updates
- **`org/optaplanner/core/impl/solver/termination/Termination.java`**
  - Add `createChildThreadTermination()` method
  - Support child thread termination delegation

### 18. Basic Plumbing Termination
- **`org/optaplanner/core/impl/solver/termination/BasicPlumbingTermination.java`**
  - Implement child thread termination support
  - Handle daemon mode and early termination

### 19. Composite Termination Updates
- **`org/optaplanner/core/impl/solver/termination/AbstractCompositeTermination.java`**
- **`org/optaplanner/core/impl/solver/termination/AndCompositeTermination.java`**
- **`org/optaplanner/core/impl/solver/termination/OrCompositeTermination.java`**
  - Update to support child thread termination
  - Delegate to child terminations

## Test Files

### 20. Multithreading Tests
- **`org/optaplanner/core/impl/test/multithreading/MultithreadingTest.java`**
  - Basic functionality tests
  - Different thread count configurations
  - Custom thread factory tests

- **`org/optaplanner/core/impl/test/multithreading/MultithreadingPerformanceTest.java`**
  - Performance comparison tests
  - Single vs multi-threaded performance
  - Scalability testing

- **`org/optaplanner/core/impl/test/multithreading/MultithreadingErrorHandlingTest.java`**
  - Exception handling tests
  - Error propagation validation
  - Invalid configuration tests

- **`org/optaplanner/core/impl/test/multithreading/MultithreadingPhaseTest.java`**
  - Integration tests with different phases
  - Mixed phase configurations
  - Phase-specific multithreading tests

### 21. Custom Thread Factory Test
- **`org/optaplanner/core/impl/test/multithreading/TestThreadFactory.java`**
  - Custom thread factory implementation
  - Thread naming and configuration
  - Factory invocation tracking

## Configuration Examples

### 22. Configuration Usage Examples
- **`org/optaplanner/core/impl/test/multithreading/ConfigurationExamples.java`**
  - High-performance configurations
  - Conservative configurations
  - Memory-constrained configurations
  - Custom thread factory examples

## Additional Support Files

### 23. Partition Thread Support (Optional)
- **`org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java`**
  - Already exists but needs PART_THREAD support
  - Update to use ChildThreadType.PART_THREAD
  - Handle partition thread coordination

### 24. Memory and Performance Monitoring
- **`org/optaplanner/core/impl/solver/thread/MemoryMonitor.java`** (Optional)
  - Memory usage monitoring
  - Performance metrics collection
  - Thread pool health monitoring

## File Modification Summary

### Files to Create (20+ files):
1. `ChildThreadType.java`
2. `DefaultSolverThreadFactory.java`
3. `ThreadUtils.java`
4. `MoveThreadOperation.java`
5. `SetupOperation.java`
6. `DestroyOperation.java`
7. `MoveEvaluationOperation.java`
8. `ApplyStepOperation.java`
9. `MoveThreadRunner.java`
10. `OrderByMoveIndexBlockingQueue.java`
11. `ChildThreadPlumbingTermination.java`
12. `PhaseToSolverTerminationBridge.java`
13. `MultiThreadedLocalSearchDecider.java`
14. `MultiThreadedConstructionHeuristicDecider.java`
15. `MoveThreadCountResolver.java` (inner class)
16. Test files (6+ files)
17. Configuration examples

### Files to Modify (8+ files):
1. `SolverConfig.java` - Add thread configuration
2. `HeuristicConfigPolicy.java` - Add thread support
3. `DefaultSolverFactory.java` - Add resolution logic
4. `DefaultLocalSearchPhaseFactory.java` - Add multithreading support
5. `DefaultConstructionHeuristicPhaseFactory.java` - Add multithreading support
6. `InnerScoreDirector.java` - Add child thread support
7. `AbstractScoreDirector.java` - Add child thread support
8. `Termination.java` - Add child thread interface

## Implementation Order

1. **Core Infrastructure** (Files 1-6): Thread types, factories, utilities
2. **Move Thread Operations** (Files 7-10): Operation classes and runner
3. **Termination Infrastructure** (Files 11-12): Child thread termination
4. **Multithreaded Deciders** (Files 13-14): Main decider implementations
5. **Configuration Support** (Files 15-16): Policy and resolution logic
6. **Phase Factory Updates** (Files 17-18): Integration with existing phases
7. **Score Director Support** (Files 19-20): Child thread score directors
8. **Termination Updates** (Files 21-23): Complete termination support
9. **Testing** (Files 24+): Comprehensive test suite

This reference provides a complete roadmap for implementing multithreading support in OptaPlanner, ensuring all necessary components are properly integrated and tested.