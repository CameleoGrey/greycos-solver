# GreyCOS Solver Unified Edition Refactor Plan - Enhanced Version

## Executive Summary

This document outlines the comprehensive plan to refactor GreyCOS Solver from a split Community/Enterprise edition architecture into a single unified edition. All previously enterprise-only features will be available by default without requiring explicit enabling or enterprise licenses.

## Enhanced Analysis: Complete Enterprise Footprint

### Files to Delete (Confirmed)

#### 1. Enterprise Service Layer
```
core/src/main/java/ai/greycos/solver/core/enterprise/
  └── GreyCOSSolverEnterpriseService.java          (310 lines)

core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/
  └── DefaultGreyCOSSolverEnterpriseService.java   (467 lines)
```

#### 2. Enterprise Tests
```
core/src/test/java/ai/greycos/solver/core/enterprise/
  └── GreyCOSSolverEnterpriseServiceTest.java      (20 lines)
```

### Files to Modify (Complete List)

#### Core Implementation Files (12 files)

1. **DefaultSolverFactory.java**
   - Remove enterprise service calls
   - Keep multithreading logic

2. **DefaultLocalSearchPhaseFactory.java**
   - Remove enterprise references
   - Keep multithreading logic

3. **DefaultConstructionHeuristicPhaseFactory.java**
   - Remove enterprise references
   - Keep multithreading logic

4. **MoveSelectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `load()` calls for multistage selectors
   - Implement multistage logic directly

5. **EntitySelectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `loadOrFail(Feature.NEARBY_SELECTION)` calls
   - Implement nearby selection directly

6. **ValueSelectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `loadOrFail(Feature.NEARBY_SELECTION)` calls
   - Implement nearby selection directly

7. **SubListSelectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `loadOrFail(Feature.NEARBY_SELECTION)` calls
   - Implement nearby selection directly

8. **DestinationSelectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `loadOrFail(Feature.NEARBY_SELECTION)` calls
   - Implement nearby selection directly

9. **BavetConstraintStreamScoreDirectorFactory.java**
   - Remove `GreyCOSSolverEnterpriseService` import
   - Remove `loadOrFail(Feature.AUTOMATIC_NODE_SHARING)` calls
   - Implement node sharing directly

10. **DefaultConstraintProviderNodeSharer.java**
    - Remove `GreyCOSSolverEnterpriseService` import
    - Remove interface implementation
    - Make standalone class

11. **VariableListenerSupport.java**
    - Remove `GreyCOSSolverEnterpriseService` import
    - Remove `loadOrDefault()` calls
    - Implement topology graph logic directly

12. **AbstractPhaseFactory.java**
    - Remove enterprise class loading for partitioned search
    - Remove enterprise scope check

#### Configuration Files (8 files)

13. **SolverConfig.java**
    - Remove enterprise-related documentation
    - Keep all configuration options

14. **ConstructionHeuristicForagerConfig.java**
    - Remove enterprise feature documentation
    - Remove license warnings

15. **LocalSearchForagerConfig.java**
    - Remove enterprise feature documentation
    - Remove license warnings

16. **All Phase Config Classes**
    - Remove enterprise feature warnings
    - Keep configuration options

17. **All Selector Config Classes**
    - Remove enterprise feature warnings
    - Keep configuration options

#### Additional Files (3 files)

18. **DefaultGreyCOSSolverEnterpriseService.java** (already listed)
19. **GreyCOSSolverEnterpriseService.java** (already listed)
20. **GreyCOSSolverEnterpriseServiceTest.java** (already listed)

### Files to Create

1. **Updated package-info.java files** (if needed)
2. **Migration guide documentation**
3. **Updated integration tests**

## Detailed Implementation Steps by Feature

### Step 1: Remove Enterprise Service Layer

**Current Flow**:
```
User → GreyCOSSolverEnterpriseService.load() → 
ClassNotFoundException → DefaultGreyCOSSolverEnterpriseService → 
Feature checks → UnsupportedOperationException
```

**New Flow**:
```
User → Direct implementation → Feature available
```

**Files to Modify**:
- Delete: `GreyCOSSolverEnterpriseService.java`
- Delete: `DefaultGreyCOSSolverEnterpriseService.java`
- Delete: `GreyCOSSolverEnterpriseServiceTest.java`

**Impact**: All static factory methods removed, no more reflection-based loading

### Step 2: Integrate Multithreading

**Current State**: Multithreading requires enterprise edition detection

**New State**: Multithreading always available when `moveThreadCount` is configured

**Files to Modify**:
1. `DefaultSolverFactory.java`
   ```java
   // BEFORE
   var moveThreadCount = resolveMoveThreadCount(true);
   // Uses enterprise service internally
   
   // AFTER  
   var moveThreadCount = resolveMoveThreadCount(true);
   // Direct implementation, no enterprise checks
   ```

2. `DefaultLocalSearchPhaseFactory.java`
   - Remove any enterprise service references
   - Keep existing multithreading logic

3. `DefaultConstructionHeuristicPhaseFactory.java`
   - Remove any enterprise service references
   - Keep existing multithreading logic

**Validation**: Ensure `moveThreadCount` configuration works without enterprise license

### Step 3: Integrate Nearby Selection

**Current State**: Nearby selection throws `UnsupportedOperationException` in community edition

**New State**: Nearby selection works when properly configured

**Files to Modify**:
1. `EntitySelectorFactory.java`
   ```java
   // BEFORE
   return GreyCOSSolverEnterpriseService.loadOrFail(
           GreyCOSSolverEnterpriseService.Feature.NEARBY_SELECTION)
       .applyNearbySelection(...);
   
   // AFTER
   return applyNearbySelectionDirectly(...);
   ```

2. `ValueSelectorFactory.java`
   - Remove enterprise service calls
   - Implement nearby selection logic directly

3. `SubListSelectorFactory.java`
   - Remove enterprise service calls
   - Implement nearby selection logic directly

4. `DestinationSelectorFactory.java`
   - Remove enterprise service calls
   - Implement nearby selection logic directly

**Implementation**: Move logic from `DefaultGreyCOSSolverEnterpriseService` to respective factories

### Step 4: Integrate Custom Foragers

**Current State**: Custom forager classes throw `UnsupportedOperationException`

**New State**: Custom forager classes work when configured

**Files to Modify**:
1. `ConstructionHeuristicForagerConfig.java`
   - Remove enterprise warnings
   - Keep configuration

2. `LocalSearchForagerConfig.java`
   - Remove enterprise warnings
   - Keep configuration

3. `ConstructionHeuristicForagerFactory.java`
   - Remove enterprise checks
   - Allow custom classes

4. `LocalSearchForagerFactory.java`
   - Remove enterprise checks
   - Allow custom classes

### Step 5: Integrate Multistage Move Selectors

**Current State**: Multistage selectors require enterprise edition

**New State**: Multistage selectors always available

**Files to Modify**:
1. `MoveSelectorFactory.java`
   ```java
   // BEFORE
   var enterpriseService = GreyCOSSolverEnterpriseService.load();
   return enterpriseService.buildBasicMultistageMoveSelectorFactory(...);
   
   // AFTER
   return new MultistageMoveSelectorFactory<>(moveSelectorConfig);
   ```

**Implementation**: Ensure `MultistageMoveSelectorFactory` and `ListMultistageMoveSelectorFactory` are in core

### Step 6: Integrate Partitioned Search

**Current State**: Partitioned search partially available with fallback

**New State**: Partitioned search fully available

**Files to Modify**:
1. `AbstractPhaseFactory.java`
   ```java
   // BEFORE
   try {
       Class.forName("ai.greycos.solver.enterprise.core.partitioned.PartitionedSearchPhaseScope");
   } catch (ClassNotFoundException e) {
       throw new IllegalStateException("...");
   }
   
   // AFTER
   // Remove enterprise class loading entirely
   ```

2. Ensure all partitioned search components are in core module

### Step 7: Integrate Automatic Node Sharing

**Current State**: Node sharing requires enterprise edition

**New State**: Node sharing always available when configured

**Files to Modify**:
1. `BavetConstraintStreamScoreDirectorFactory.java`
   ```java
   // BEFORE
   var enterpriseService = GreyCOSSolverEnterpriseService.loadOrFail(
       GreyCOSSolverEnterpriseService.Feature.AUTOMATIC_NODE_SHARING);
   Class<? extends ConstraintProvider> transformedClass = 
       enterpriseService.createNodeSharer().buildNodeSharedConstraintProvider(...);
   
   // AFTER
   // Implement node sharing directly or use existing DefaultConstraintProviderNodeSharer
   ```

2. `DefaultConstraintProviderNodeSharer.java`
   - Remove interface dependency
   - Make standalone implementation

3. `VariableListenerSupport.java`
   ```java
   // BEFORE
   GreyCOSSolverEnterpriseService.loadOrDefault(
       service -> service::buildTopologyGraph, () -> DefaultTopologicalOrderGraph::new)
   
   // AFTER
   () -> DefaultTopologicalOrderGraph::new
   ```

### Step 8: Remove License Checks

**Current State**: Enterprise license checks in multiple places

**New State**: No license checks

**Files to Clean**:
- Remove `EnterpriseLicenseException` class
- Remove all `loadOrFail()` calls
- Remove all license-related error messages
- Remove enterprise coordinate constants

### Step 9: Update Configuration Validation

**Current State**: Some configurations validated against enterprise features

**New State**: All configurations validated uniformly

**Files to Update**:
- `SolverConfig.java` - Remove enterprise warnings
- All phase config classes - Remove enterprise warnings
- All selector config classes - Remove enterprise warnings

### Step 10: Update Tests

**Current State**: Tests for both editions

**New State**: Tests for unified edition

**Test Updates**:
- Remove edition-specific tests
- Update existing tests to work without enterprise detection
- Add comprehensive integration tests
- Update test documentation

## Complete File Inventory

### Files to Delete (3 files)
```
core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java
core/src/main/java/ai/greycos/solver/core/impl/partitionedsearch/DefaultGreyCOSSolverEnterpriseService.java
core/src/test/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseServiceTest.java
```

### Files to Modify (20+ files)
```
core/src/main/java/ai/greycos/solver/core/impl/solver/DefaultSolverFactory.java
core/src/main/java/ai/greycos/solver/core/impl/localsearch/DefaultLocalSearchPhaseFactory.java
core/src/main/java/ai/greycos/solver/core/impl/constructionheuristic/DefaultConstructionHeuristicPhaseFactory.java
core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/move/MoveSelectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/entity/EntitySelectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/value/ValueSelectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/list/SubListSelectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/heuristic/selector/list/DestinationSelectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/score/director/stream/BavetConstraintStreamScoreDirectorFactory.java
core/src/main/java/ai/greycos/solver/core/impl/nodesharing/DefaultConstraintProviderNodeSharer.java
core/src/main/java/ai/greycos/solver/core/impl/domain/variable/listener/support/VariableListenerSupport.java
core/src/main/java/ai/greycos/solver/core/impl/phase/AbstractPhaseFactory.java
core/src/main/java/ai/greycos/solver/core/config/solver/SolverConfig.java
core/src/main/java/ai/greycos/solver/core/config/constructionheuristic/decider/forager/ConstructionHeuristicForagerConfig.java
core/src/main/java/ai/greycos/solver/core/config/localsearch/decider/forager/LocalSearchForagerConfig.java
core/src/main/java/ai/greycos/solver/core/config/constructionheuristic/ConstructionHeuristicPhaseConfig.java
core/src/main/java/ai/greycos/solver/core/config/localsearch/LocalSearchPhaseConfig.java
core/src/main/java/ai/greycos/solver/core/config/partitionedsearch/PartitionedSearchPhaseConfig.java
core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/MultistageMoveSelectorConfig.java
core/src/main/java/ai/greycos/solver/core/config/heuristic/selector/move/generic/list/ListMultistageMoveSelectorConfig.java
```

### Files to Update Tests (30+ files)
```
core/src/test/java/ai/greycos/solver/core/api/solver/SolverManagerTest.java
core/src/test/java/ai/greycos/solver/core/api/solver/SolverManagerThrottlingTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingTestUtils.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingIntegrationTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingPhaseTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/ConfigurationValidationTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/ThreadCoordinationTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/StepSynchronizationTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/EnhancedFeaturesTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MemoryAndResourceManagementTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingErrorHandlingTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingPerformanceTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/RuntimeConfigurationTest.java
core/src/test/java/ai/greycos/solver/core/impl/test/multithreading/MultithreadingTest.java
core/src/test/java/ai/greycos/solver/core/impl/heuristic/thread/OrderByMoveIndexBlockingQueueTest.java
core/src/test/java/ai/greycos/solver/core/impl/localsearch/decider/MultiThreadedLocalSearchDeciderStepSyncTest.java
core/src/test/java/ai/greycos/solver/core/impl/partitionedsearch/PartitionedSearchTest.java
```

## Implementation Order

### Week 1-2: Core Unification
1. Delete enterprise service files
2. Update core factory classes
3. Remove license checks
4. Basic compilation verification

### Week 3-4: Feature Integration
1. Multithreading integration
2. Nearby selection integration
3. Custom forager integration
4. Multistage selector integration

### Week 5-6: Advanced Features
1. Partitioned search integration
2. Node sharing integration
3. Configuration cleanup
4. Performance verification

### Week 7-8: Testing & Documentation
1. Update all tests
2. Create integration tests
3. Update documentation
4. Migration guide

### Week 9-10: Polish & Verification
1. Performance testing
2. Backward compatibility verification
3. Final documentation updates
4. Release preparation

## Risk Mitigation

### Risk 1: Breaking Changes
**Mitigation**: 
- Maintain backward compatibility in configuration
- Provide migration guide
- Test extensively with existing configurations

### Risk 2: Performance Impact
**Mitigation**: 
- Profile before/after
- Ensure no regression in core functionality
- Optimize integration points

### Risk 3: Missing Features
**Mitigation**: 
- Comprehensive feature audit
- Ensure all enterprise features properly integrated
- Test all feature combinations

### Risk 4: Documentation Gaps
**Mitigation**: 
- Update all documentation
- Provide clear examples
- Migration guide for enterprise users

## Success Criteria

1. ✅ No enterprise service loading mechanism
2. ✅ All features available without license checks
3. ✅ Single codebase for all features
4. ✅ Backward compatible configuration
5. ✅ All tests passing
6. ✅ Documentation updated
7. ✅ Performance maintained or improved
8. ✅ Clear migration path for users

## Verification Checklist

- [ ] All enterprise service references removed
- [ ] All features work without enterprise detection
- [ ] Configuration backward compatible
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Performance tests show no regression
- [ ] Documentation updated
- [ ] Migration guide created
- [ ] Examples updated
- [ ] Build succeeds without enterprise dependencies

## Conclusion

This enhanced refactor plan provides a complete roadmap to unify GreyCOS Solver into a single edition. The plan addresses all identified enterprise features, provides detailed implementation steps, and includes comprehensive risk mitigation strategies.

The result will be a simpler, more maintainable codebase that is easier for users to understand and for developers to extend, with all features available by default without licensing complexity.