# Greycos Multithreading Implementation Plan

## Overview

This document provides a comprehensive step-by-step plan for implementing full multithreading support in the greycos solver. Based on the analysis of the current codebase, the implementation will focus on adding the missing core multithreading components while leveraging the existing infrastructure.

## Current State Analysis

### ✅ Already Implemented Infrastructure

The greycos solver has **partial multithreading infrastructure** already in place:

1. **Configuration Infrastructure**
   - `ChildThreadType.java` - Enum with PART_THREAD and MOVE_THREAD
   - `DefaultSolverThreadFactory.java` - Thread factory implementation
   - `ThreadUtils.java` - Thread pool management utilities
   - `SolverConfig` multithreading fields (moveThreadCount, moveThreadBufferSize, threadFactoryClass)
   - Constants: `MOVE_THREAD_COUNT_NONE`, `MOVE_THREAD_COUNT_AUTO`

2. **Move Thread Count Resolution**
   - `MoveThreadCountResolver` in `DefaultSolverFactory.java`
   - AUTO detection logic (leaves 2 processors, max 4 threads)
   - Proper validation and error handling
   - Integration with `HeuristicConfigPolicy.Builder`

3. **Thread Configuration Support**
   - `HeuristicConfigPolicy` with thread support fields
   - `buildThreadFactory()` method for creating thread factories
   - Proper thread prefix naming ("MoveThread", "PartThread")

4. **Termination Infrastructure**
   - `ChildThreadSupportingTermination` interface
   - `ChildThreadPlumbingTermination` implementation
   - Most termination classes implement `createChildThreadTermination()`

5. **Enterprise Integration**
   - `GreycosSolverEnterpriseService` interface
   - Multithreading delegated to Enterprise Edition
   - Proper feature detection and licensing checks

### ❌ Missing Critical Components

The implementation is **missing the core multithreading components**:

1. **Move Thread Operations** (CRITICAL)
   - `MoveThreadOperation.java` - Base operation class
   - `SetupOperation.java` - Initialize move thread
   - `DestroyOperation.java` - Cleanup move thread
   - `MoveEvaluationOperation.java` - Evaluate moves in parallel
   - `ApplyStepOperation.java` - Apply step changes across threads

2. **Move Thread Core Classes** (CRITICAL)
   - `MoveThreadRunner.java` - Core move thread implementation
   - `OrderByMoveIndexBlockingQueue.java` - Thread-safe result queue

3. **Multithreaded Deciders** (CRITICAL)
   - `MultiThreadedLocalSearchDecider.java` - Main multithreaded local search
   - `MultiThreadedConstructionHeuristicDecider.java` - Main multithreaded construction heuristic

4. **Phase Integration** (CRITICAL)
   - `DefaultLocalSearchPhaseFactory.buildDecider()` multithreading support
   - `DefaultConstructionHeuristicPhaseFactory.buildDecider()` multithreading support
   - Dynamic decider selection based on `moveThreadCount`

5. **Score Director Child Thread Support** (CRITICAL)
   - `InnerScoreDirector.createChildThreadScoreDirector()` method
   - `AbstractScoreDirector` child thread support
   - Score director cloning and configuration for child threads

6. **Thread Coordination Infrastructure** (IMPORTANT)
   - `PhaseToSolverTerminationBridge.java` - Bridge between phase and solver termination
   - Proper thread synchronization primitives
   - Move result ordering and aggregation
   - Exception propagation from child threads

7. **Testing Infrastructure** (IMPORTANT)
   - Comprehensive test suite for all components
   - Performance validation tests
   - Error handling tests
   - Integration tests

## Implementation Plan

### Phase 1: Core Move Thread Infrastructure (Priority 1)

#### Step 1.1: Create Move Thread Operations
**Files to Create:**
- `ai/greycos/solver/core/impl/heuristic/thread/MoveThreadOperation.java`
- `ai/greycos/solver/core/impl/heuristic/thread/SetupOperation.java`
- `ai/greycos/solver/core/impl/heuristic/thread/DestroyOperation.java`
- `ai/greycos/solver/core/impl/heuristic/thread/MoveEvaluationOperation.java`
- `ai/greycos/solver/core/impl/heuristic/thread/ApplyStepOperation.java`

**Implementation Details:**
- Base operation class with toString() method
- SetupOperation with InnerScoreDirector parameter
- DestroyOperation as empty implementation
- MoveEvaluationOperation with stepIndex, moveIndex, and Move parameters
- ApplyStepOperation with stepIndex, Move, and Score parameters

#### Step 1.2: Create Move Thread Runner
**Files to Create:**
- `ai/greycos/solver/core/impl/heuristic/thread/MoveThreadRunner.java`

**Implementation Details:**
- Implement Runnable interface
- Handle operation queue processing
- Manage score director lifecycle
- Exception handling and propagation
- Support for move thread barriers and synchronization

#### Step 1.3: Create Ordered Result Queue
**Files to Create:**
- `ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueue.java`

**Implementation Details:**
- Thread-safe queue for move results
- Ensure moves are processed in correct order
- Handle exception propagation from threads
- Support for move result aggregation

### Phase 2: Multithreaded Deciders (Priority 1)

#### Step 2.1: Create MultiThreadedLocalSearchDecider
**Files to Create:**
- `ai/greycos/solver/core/impl/localsearch/decider/MultiThreadedLocalSearchDecider.java`

**Implementation Details:**
- Extend LocalSearchDecider
- Manage move thread pool
- Handle move selection and evaluation
- Integrate with existing local search infrastructure
- Support for phaseStarted, phaseEnded, and decideNextStep methods

#### Step 2.2: Create MultiThreadedConstructionHeuristicDecider
**Files to Create:**
- `ai/greycos/solver/core/impl/constructionheuristic/decider/MultiThreadedConstructionHeuristicDecider.java`

**Implementation Details:**
- Extend ConstructionHeuristicDecider
- Similar structure to local search decider
- Handle construction heuristic move evaluation
- Support for construction heuristic specific requirements

### Phase 3: Score Director Support (Priority 1)

#### Step 3.1: Add Child Thread Support to Score Directors
**Files to Modify:**
- `ai/greycos/solver/core/impl/score/director/InnerScoreDirector.java`
- `ai/greycos/solver/core/impl/score/director/AbstractScoreDirector.java`

**Implementation Details:**
- Add `createChildThreadScoreDirector()` method
- Support child thread score director creation
- Handle child thread-specific configuration
- Proper score director cloning and isolation

### Phase 4: Phase Factory Integration (Priority 1)

#### Step 4.1: Update Local Search Phase Factory
**Files to Modify:**
- `ai/greycos/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java`

**Implementation Details:**
- Update `buildDecider()` method
- Create multithreaded decider when moveThreadCount > 0
- Configure thread factory and buffer sizes
- Set up proper assertion flags
- Remove Enterprise Edition dependency for basic multithreading

#### Step 4.2: Update Construction Heuristic Phase Factory
**Files to Modify:**
- `ai/greycos/solver/core/impl/constructionheuristic/DefaultConstructionHeuristicPhaseFactory.java`

**Implementation Details:**
- Update `buildDecider()` method
- Similar structure to local search factory
- Handle construction heuristic specific requirements
- Remove Enterprise Edition dependency

### Phase 5: Thread Coordination Infrastructure (Priority 2)

#### Step 5.1: Create Phase-to-Solver Termination Bridge
**Files to Create:**
- `ai/greycos/solver/core/impl/solver/termination/PhaseToSolverTerminationBridge.java`

**Implementation Details:**
- Bridge between phase termination and solver termination
- Handle child thread termination delegation
- Critical for proper termination coordination

#### Step 5.2: Enhance Thread Synchronization
**Implementation Details:**
- Add proper thread synchronization primitives
- Implement move result ordering and aggregation
- Add exception propagation from child threads
- Ensure thread-safe operation handling

### Phase 6: Testing Infrastructure (Priority 2)

#### Step 6.1: Create Basic Functionality Tests
**Files to Create:**
- `ai/greycos/solver/core/impl/test/multithreading/MultithreadingTest.java`
- `ai/greycos/solver/core/impl/test/multithreading/TestThreadFactory.java`

**Implementation Details:**
- Test moveThreadCount AUTO configuration
- Test explicit moveThreadCount values
- Test moveThreadCount NONE configuration
- Test custom thread factory integration

#### Step 6.2: Create Performance Tests
**Files to Create:**
- `ai/greycos/solver/core/impl/test/multithreading/MultithreadingPerformanceTest.java`

**Implementation Details:**
- Compare single-threaded vs multi-threaded performance
- Validate performance improvements
- Test scalability with different thread counts

#### Step 6.3: Create Error Handling Tests
**Files to Create:**
- `ai/greycos/solver/core/impl/test/multithreading/MultithreadingErrorHandlingTest.java`

**Implementation Details:**
- Test exception propagation from move threads
- Test invalid configuration handling
- Test thread failure recovery

#### Step 6.4: Create Integration Tests
**Files to Create:**
- `ai/greycos/solver/core/impl/test/multithreading/MultithreadingPhaseTest.java`

**Implementation Details:**
- Test integration with different solver phases
- Test mixed phase configurations
- Test phase-specific multithreading

### Phase 7: Enhanced Features (Priority 3)

#### Step 7.1: Add Memory Monitoring
**Implementation Details:**
- Add memory usage monitoring in move threads
- Implement memory pressure detection
- Add memory-efficient thread management

#### Step 7.2: Add Performance Metrics
**Implementation Details:**
- Collect performance metrics from move threads
- Implement performance monitoring dashboard
- Add performance optimization suggestions

#### Step 7.3: Add Dynamic Thread Adjustment
**Implementation Details:**
- Implement dynamic thread count adjustment
- Add adaptive thread pool sizing
- Support for runtime thread configuration changes

## Implementation Dependencies

### Critical Path Dependencies

1. **Move Thread Operations** → **Move Thread Runner** → **Multithreaded Deciders**
2. **Score Director Support** → **Multithreaded Deciders**
3. **Phase Factory Integration** → **Multithreaded Deciders**
4. **Thread Coordination** → **Multithreaded Deciders**

### Testing Dependencies

1. **Core Infrastructure** → **Basic Functionality Tests**
2. **Multithreaded Deciders** → **Performance Tests**
3. **Error Handling** → **Error Handling Tests**
4. **Integration** → **Integration Tests**

## Validation Strategy

### Unit Testing
- Test each component in isolation
- Validate thread safety of shared components
- Test exception handling and propagation

### Integration Testing
- Test component interactions
- Validate end-to-end multithreading functionality
- Test with different solver configurations

### Performance Testing
- Benchmark single-threaded vs multi-threaded performance
- Test scalability with different problem sizes
- Validate performance improvements

### Regression Testing
- Ensure existing functionality remains intact
- Test backward compatibility
- Validate configuration migration

## Risk Mitigation

### Thread Safety Risks
- **Mitigation**: Extensive thread safety testing
- **Mitigation**: Use of proven thread-safe data structures
- **Mitigation**: Proper synchronization primitives

### Performance Risks
- **Mitigation**: Performance benchmarking at each phase
- **Mitigation**: Adaptive thread pool sizing
- **Mitigation**: Memory usage monitoring

### Compatibility Risks
- **Mitigation**: Backward compatibility testing
- **Mitigation**: Gradual rollout strategy
- **Mitigation**: Configuration validation

## Success Criteria

### Functional Success Criteria
- ✅ Multithreading works with moveThreadCount > 0
- ✅ Performance improvement over single-threaded solving
- ✅ Thread-safe operation handling
- ✅ Proper exception propagation and handling

### Performance Success Criteria
- ✅ Linear performance scaling with thread count (up to 4 threads)
- ✅ No memory leaks in long-running multithreaded solving
- ✅ Proper resource cleanup and thread pool management

### Quality Success Criteria
- ✅ Comprehensive test coverage (>80%)
- ✅ No regression in existing functionality
- ✅ Proper documentation and examples
- ✅ Clear error messages and diagnostics

## Timeline Estimate

### Phase 1-4 (Core Implementation): 4-6 weeks
- Core infrastructure and deciders
- Integration with existing codebase
- Basic testing and validation

### Phase 5-6 (Enhancement and Testing): 2-3 weeks
- Thread coordination and synchronization
- Comprehensive testing suite
- Performance optimization

### Phase 7 (Advanced Features): 1-2 weeks
- Memory monitoring and performance metrics
- Dynamic thread adjustment
- Documentation and examples

**Total Estimated Timeline: 7-11 weeks**

## Next Steps

1. **Start with Phase 1**: Implement core move thread infrastructure
2. **Validate each phase**: Test thoroughly before proceeding
3. **Iterative development**: Implement, test, validate, repeat
4. **Documentation**: Document each component as it's implemented
5. **Community feedback**: Engage with community for testing and feedback

This implementation plan provides a comprehensive roadmap for adding full multithreading support to the greycos solver, leveraging the existing infrastructure while adding the missing critical components.