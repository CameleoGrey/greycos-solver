# Node Sharing Reimplementation Plan for GreyCOS

## Executive Summary

This document outlines a plan to reimplement automatic node sharing for GreyCOS Solver, aligning the implementation with the official OptaPlanner documentation while maintaining GreyCOS's architectural goals.

## 1. Analysis of Official Documentation

### 1.1 Purpose and Benefits
- **Goal**: Share nodes in constraint stream networks when they are functionally equivalent
- **Benefit**: Significantly improve move evaluation speed by avoiding redundant operations
- **Use Case**: When a ConstraintProvider performs operations for multiple constraints (e.g., finding all shifts for an employee)

### 1.2 Configuration
- Enabled via XML configuration: `<constraintStreamAutomaticNodeSharing>true</constraintStreamAutomaticNodeSharing>`
- Located in `<scoreDirectorFactory>` section of `solverConfig.xml`

### 1.3 Restrictions for Automatic Node Sharing
The ConstraintProvider class must:
1. **Not be final** - to allow subclass generation
2. **Not have final methods** - to allow method overriding in generated subclass
3. **Not access protected classes, methods or fields** - to ensure accessibility from generated code
4. **Accept that debugging breakpoints will not work** - because the class is transformed

### 1.4 Functional Equivalence Criteria
Two building blocks are functionally equivalent when:
1. They represent the **same operation**
2. They have **functionally equivalent parent building blocks**
3. They have **functionally equivalent inputs**

### 1.5 The Lambda Problem
The JVM creates different instances of functionally equivalent lambdas, making reference equality unreliable. Example:
```java
// These create different lambda instances
UniConstraintStream<Shift> a(ConstraintFactory factory) {
    return factory.forEach(Shift.class)
        .filter(shift -> shift.getEmployee().getName().equals("Ann"));
}

UniConstraintStream<Shift> b(ConstraintFactory factory) {
    return factory.forEach(Shift.class)
        .filter(shift -> shift.getEmployee().getName().equals("Ann"));
}
```

### 1.6 Official Solution: Bytecode Transformation
When automatic node sharing is enabled, the ConstraintProvider class is transformed:
```java
// Before transformation
public class MyConstraintProvider implements ConstraintProvider {
    Constraint a(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
            .filter(shift -> shift.getEmployee().getName().equals("Ann"))
            .penalize(SimpleScore.ONE)
            .asConstraint("a");
    }
    
    Constraint b(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
            .filter(shift -> shift.getEmployee().getName().equals("Ann"))
            .penalize(SimpleScore.ONE)
            .asConstraint("b");
    }
}

// After transformation
public class MyConstraintProvider implements ConstraintProvider {
    private static final Predicate<Shift> $predicate1 = 
        shift -> shift.getEmployee().getName().equals("Ann");
    
    Constraint a(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
            .filter($predicate1)
            .penalize(SimpleScore.ONE)
            .asConstraint("a");
    }
    
    Constraint b(ConstraintFactory constraintFactory) {
        return factory.forEach(Shift.class)
            .filter($predicate1)
            .penalize(SimpleScore.ONE)
            .asConstraint("b");
    }
}
```

## 2. Current GreyCOS Implementation Analysis

### 2.1 Existing Architecture

#### Configuration
- `constraintStreamAutomaticNodeSharing` field exists in [`ScoreDirectorFactoryConfig`](core/src/main/java/ai/greycos/solver/core/config/score/director/ScoreDirectorFactoryConfig.java:50)
- Inherited from parent configuration via [`ConfigUtils.inheritOverwritableProperty()`](core/src/main/java/ai/greycos/solver/core/config/score/director/ScoreDirectorFactoryConfig.java:303-306)

#### Runtime Node Sharing
- [`BavetConstraintFactory.sharingStreamMap`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/BavetConstraintFactory.java:58-60): HashMap for deduplication
- [`BavetConstraintFactory.share()`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/BavetConstraintFactory.java:106-115): Uses `computeIfAbsent` to deduplicate streams
- [`BavetAbstractConstraintStream.shareAndAddChild()`](core/src/main/java/ai/greycos/solver/core/impl/bavet/common/BavetAbstractConstraintStream.java:96-99): Wrapper for sharing

#### Stream Equality
- Streams implement `equals()` and `hashCode()` for deduplication
- Example: [`BavetFilterUniConstraintStream`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/uni/BavetFilterUniConstraintStream.java:45-59):
  ```java
  @Override
  public int hashCode() {
      return Objects.hash(parent, predicate);
  }
  
  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      } else if (o instanceof BavetFilterUniConstraintStream<?, ?> other) {
          return parent == other.parent && predicate == other.predicate;
      } else {
          return false;
      }
  }
  ```
- Uses **reference equality** (`predicate == other.predicate`) for lambdas

#### Enterprise Integration
- [`GreyCOSSolverEnterpriseService.ConstraintProviderNodeSharer`](core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java:246-250): Interface for transforming ConstraintProvider classes
- [`GreyCOSSolverEnterpriseService.createNodeSharer()`](core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java:131): Creates node sharer (Enterprise feature)

#### Test Coverage
- Comprehensive test suite in [`AbstractUniConstraintStreamNodeSharingTest`](core/src/test/java/ai/greycos/solver/core/impl/score/stream/common/uni/AbstractUniConstraintStreamNodeSharingTest.java)
- Tests for: filter, join, ifExists, ifNotExists, groupBy, map, flattenLast, distinct, concat, precompute
- Tests verify that identical operations return the same stream instance (via `isSameAs()`)

### 2.2 Strengths of Current Implementation
1. **Clean separation**: Node sharing logic encapsulated in `BavetConstraintFactory`
2. **Comprehensive testing**: Extensive test coverage for various stream operations
3. **Runtime flexibility**: Works without requiring bytecode transformation
4. **Enterprise hook**: Infrastructure in place for transformation-based approach

### 2.3 Weaknesses of Current Implementation
1. **Lambda reference equality**: Relies on `predicate == other.predicate`, which JVM doesn't guarantee
2. **No bytecode transformation**: Doesn't transform ConstraintProvider to ensure lambda sharing
3. **Limited effectiveness**: Only works when user manually stores lambdas in variables
4. **Not fully documented**: Users may not understand why node sharing isn't working

### 2.4 Example of Current Limitation
```java
// Current implementation: These create TWO filter nodes
public class MyConstraintProvider implements ConstraintProvider {
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Shift for Ann"),
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Another constraint for Ann")
        };
    }
}
```

The two lambdas are different instances, so `predicate == other.predicate` returns `false`, and no sharing occurs.

## 3. Proposed Reimplementation Architecture

### 3.1 High-Level Approach

**Two-tier strategy**:
1. **Community Edition**: Enhanced runtime deduplication with better lambda handling
2. **Enterprise Edition**: Bytecode transformation for optimal node sharing

This allows:
- Community users to get improved node sharing without requiring Enterprise license
- Enterprise users to get maximum performance via bytecode transformation
- Gradual migration path from community to enterprise

### 3.2 Community Edition Improvements

#### 3.2.1 Enhanced Lambda Deduplication

**Problem**: Current implementation uses reference equality for lambdas

**Solution**: Implement a lambda cache that uses structural equality

```java
public class LambdaCache {
    private final Map<SerializedLambda, Object> lambdaCache = new ConcurrentHashMap<>();
    
    public <T> T cacheLambda(T lambda) {
        if (lambda == null) {
            return null;
        }
        SerializedLambda serialized = SerializedLambda.serialize(lambda);
        return (T) lambdaCache.computeIfAbsent(serialized, k -> lambda);
    }
}
```

**Implementation details**:
- Use `SerializedLambda` from `java.lang.invoke` to capture lambda structure
- Cache based on: captured class, method name, method signature, captured arguments
- Store in `ConstraintNodeBuildHelper` for reuse during node building

#### 3.2.2 Integration Points

1. **BavetConstraintFactory**: Add lambda cache
   ```java
   private final LambdaCache lambdaCache = new LambdaCache();
   
   public <A> Predicate<A> cachePredicate(Predicate<A> predicate) {
       return lambdaCache.cacheLambda(predicate);
   }
   ```

2. **Stream constructors**: Cache lambdas before storing
   ```java
   public BavetFilterUniConstraintStream(
       BavetConstraintFactory<Solution_> constraintFactory,
       BavetAbstractUniConstraintStream<Solution_, A> parent,
       Predicate<A> predicate) {
       super(constraintFactory, parent);
       this.predicate = constraintFactory.cachePredicate(predicate);
       // ...
   }
   ```

3. **Equality checks**: Use cached lambdas for comparison
   - Existing `equals()` methods already work with reference equality
   - Since we cache identical lambdas, they'll have the same reference

#### 3.2.3 Predicate Supplier Pattern

Current implementation already uses this pattern for some cases:

```java
// From BavetConstraintFactory.java:200-206
private record PredicateSupplier<Solution_, A>(Predicate<A> suppliedPredicate)
    implements Function<ConstraintNodeBuildHelper<Solution_, ?>, Predicate<A>> {
    public Predicate<A> apply(ConstraintNodeBuildHelper<Solution_, ?> helper) {
        return suppliedPredicate;
    }
}
```

**Enhancement**: Extend this pattern to all lambda types:
- `PredicateSupplier` for predicates
- `FunctionSupplier` for mapping functions
- `JoinerSupplier` for joiners
- `CollectorSupplier` for collectors

#### 3.2.4 Limitations of Community Approach
- Still requires some manual lambda caching for optimal results
- Not as effective as bytecode transformation
- May miss some sharing opportunities

### 3.3 Enterprise Edition: Bytecode Transformation

#### 3.3.1 Transformation Goals

Transform the ConstraintProvider class to:
1. Extract all lambda expressions into static final fields
2. Replace lambda expressions with field references
3. Maintain original behavior and semantics
4. Preserve method signatures and visibility

#### 3.3.2 Transformation Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  ConstraintProvider (Original)                              │
│  - defineConstraints()                                       │
│  - constraintA() with lambda1                               │
│  - constraintB() with lambda1 (identical)                   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Bytecode Analyzer                                          │
│  - Scan methods for lambda expressions                      │
│  - Serialize lambda structure                               │
│  - Identify identical lambdas                               │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Lambda Deduplicator                                       │
│  - Group identical lambdas                                 │
│  - Assign unique field names ($predicate1, $function2, etc.)│
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Bytecode Generator                                        │
│  - Add static final fields for lambdas                     │
│  - Replace lambda creation with field references             │
│  - Generate subclass: Original$NodeShared                  │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ConstraintProvider$NodeShared (Transformed)              │
│  + static final Predicate<Shift> $predicate1               │
│  - constraintA() uses $predicate1                          │
│  - constraintB() uses $predicate1 (SHARED!)               │
└─────────────────────────────────────────────────────────────┘
```

#### 3.3.3 Technology Stack

**Option 1: ASM (Recommended)**
- Lightweight, widely used bytecode manipulation library
- Already used by GreyCOS (via Gizmo)
- Fine-grained control over bytecode generation
- Good documentation and community support

**Option 2: Byte Buddy**
- Higher-level API, easier to use
- Good for simple transformations
- May be less efficient for complex lambda extraction

**Option 3: Javassist**
- Source-level bytecode manipulation
- Easier to understand than ASM
- Less performant than ASM

**Recommendation**: Use ASM for consistency with existing Gizmo implementation

#### 3.3.4 Transformation Steps

**Step 1: Class Analysis**
```java
public class ConstraintProviderAnalyzer {
    public LambdaAnalysis analyze(Class<? extends ConstraintProvider> providerClass) {
        // Read class file
        ClassReader reader = new ClassReader(providerClass.getName());
        
        // Visit methods to find lambdas
        LambdaFindingVisitor visitor = new LambdaFindingVisitor();
        reader.accept(visitor, ClassReader.SKIP_DEBUG);
        
        // Group identical lambdas
        return visitor.groupIdenticalLambdas();
    }
}
```

**Step 2: Lambda Detection**
- Scan for `invokedynamic` instructions (used for lambda creation)
- Extract bootstrap method and arguments
- Capture lambda metafactory parameters:
  - Functional interface type
  - Implementation method
  - Method type
  - Captured arguments

**Step 3: Lambda Deduplication**
```java
public class LambdaDeduplicator {
    public Map<LambdaKey, String> deduplicateLambdas(List<LambdaInfo> lambdas) {
        Map<LambdaKey, List<LambdaInfo>> grouped = lambdas.stream()
            .collect(Collectors.groupingBy(LambdaInfo::getKey));
        
        Map<LambdaKey, String> fieldNames = new HashMap<>();
        int fieldIndex = 1;
        
        for (LambdaKey key : grouped.keySet()) {
            String fieldName = "$lambda" + fieldIndex++;
            fieldNames.put(key, fieldName);
        }
        
        return fieldNames;
    }
}
```

**Step 4: Bytecode Generation**
```java
public class NodeSharingTransformer {
    public byte[] transform(byte[] originalClass, LambdaAnalysis analysis) {
        ClassReader reader = new ClassReader(originalClass);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        
        // Add static final fields for lambdas
        FieldAddingVisitor fieldVisitor = new FieldAddingVisitor(writer, analysis);
        
        // Replace lambda creation with field references
        LambdaReplacingVisitor replacingVisitor = 
            new LambdaReplacingVisitor(fieldVisitor, analysis);
        
        reader.accept(replacingVisitor, 0);
        return writer.toByteArray();
    }
}
```

**Step 5: Class Loading**
```java
public class NodeSharedClassLoader extends ClassLoader {
    public Class<? extends ConstraintProvider> loadNodeSharedClass(
            Class<? extends ConstraintProvider> original) {
        byte[] transformed = transformer.transform(original);
        return defineClass(
            original.getName() + "$NodeShared",
            transformed,
            0,
            transformed.length
        ).asSubclass(ConstraintProvider.class);
    }
}
```

#### 3.3.5 Handling Complex Cases

**Captured Variables**:
```java
// Before
String employeeName = "Ann";
Predicate<Shift> predicate = shift -> 
    shift.getEmployee().getName().equals(employeeName);

// After - cannot be shared if employeeName varies
private static final String $captured1 = "Ann";
private static final Predicate<Shift> $predicate1 = 
    shift -> shift.getEmployee().getName().equals($captured1);
```

**Method References**:
```java
// Before
Predicate<Shift> predicate = Shift::isAssigned;

// After
private static final Predicate<Shift> $predicate1 = Shift::isAssigned;
```

**Nested Lambdas**:
```java
// Before
Function<Shift, Predicate<Employee>> mapper = 
    shift -> emp -> emp.getName().equals(shift.getEmployeeName());

// After
private static final BiFunction<Shift, Employee, Boolean> $function1 = 
    (shift, emp) -> emp.getName().equals(shift.getEmployeeName());
```

#### 3.3.6 Integration with Existing Code

**Update ConstraintProviderNodeSharer**:
```java
public class DefaultConstraintProviderNodeSharer 
        implements GreyCOSSolverEnterpriseService.ConstraintProviderNodeSharer {
    
    private final NodeSharingTransformer transformer;
    private final NodeSharedClassLoader classLoader;
    
    @Override
    public <T extends ConstraintProvider> Class<T> buildNodeSharedConstraintProvider(
            Class<T> constraintProviderClass) {
        byte[] transformed = transformer.transform(constraintProviderClass);
        return classLoader.defineClass(
            constraintProviderClass.getName() + "$NodeShared",
            transformed
        ).asSubclass(constraintProviderClass);
    }
}
```

**Update BavetConstraintSessionFactory**:
```java
public BavetConstraintSessionFactory(
        SolutionDescriptor<Solution_> solutionDescriptor,
        EnvironmentMode environmentMode,
        ConstraintProviderNodeSharer nodeSharer,
        boolean constraintStreamAutomaticNodeSharing) {
    
    Class<? extends ConstraintProvider> providerClass = 
        constraintProviderClass;
    
    if (constraintStreamAutomaticNodeSharing && nodeSharer != null) {
        providerClass = nodeSharer.buildNodeSharedConstraintProvider(
            constraintProviderClass
        );
    }
    
    this.constraintProviderClass = providerClass;
    // ...
}
```

### 3.4 Configuration Integration

#### 3.4.1 Update ScoreDirectorFactoryConfig
```java
public class ScoreDirectorFactoryConfig<Solution_> {
    
    private Boolean constraintStreamAutomaticNodeSharing;
    
    public Boolean getConstraintStreamAutomaticNodeSharing() {
        return constraintStreamAutomaticNodeSharing;
    }
    
    public void setConstraintStreamAutomaticNodeSharing(
            Boolean constraintStreamAutomaticNodeSharing) {
        this.constraintStreamAutomaticNodeSharing = 
            constraintStreamAutomaticNodeSharing;
    }
}
```

#### 3.4.2 XML Configuration Support
```xml
<solver>
  <scoreDirectorFactory>
    <constraintProviderClass>org.acme.MyConstraintProvider</constraintProviderClass>
    <constraintStreamAutomaticNodeSharing>true</constraintStreamAutomaticNodeSharing>
  </scoreDirectorFactory>
</solver>
```

#### 3.4.3 Java API Configuration
```java
SolverFactory<Solution> solverFactory = SolverFactory.create(solverConfig)
    .withConstraintStreamAutomaticNodeSharing(true);
```

### 3.5 Validation and Error Handling

#### 3.5.1 Restriction Validation
```java
public class NodeSharingValidator {
    public void validate(Class<? extends ConstraintProvider> providerClass) {
        if (Modifier.isFinal(providerClass.getModifiers())) {
            throw new IllegalArgumentException(
                "ConstraintProvider class " + providerClass.getName() + 
                " must not be final for automatic node sharing"
            );
        }
        
        for (Method method : providerClass.getDeclaredMethods()) {
            if (Modifier.isFinal(method.getModifiers())) {
                throw new IllegalArgumentException(
                    "ConstraintProvider method " + method.getName() + 
                    " must not be final for automatic node sharing"
                );
            }
        }
        
        // Check for protected access
        if (usesProtectedMembers(providerClass)) {
            throw new IllegalArgumentException(
                "ConstraintProvider class " + providerClass.getName() + 
                " must not access protected members for automatic node sharing"
            );
        }
    }
}
```

#### 3.5.2 Graceful Degradation
```java
try {
    Class<? extends ConstraintProvider> nodeSharedClass = 
        nodeSharer.buildNodeSharedConstraintProvider(providerClass);
    return nodeSharedClass;
} catch (Exception e) {
    LOGGER.warn(
        "Failed to create node-shared ConstraintProvider: " + e.getMessage() + 
        ". Falling back to original class."
    );
    return providerClass;
}
```

### 3.6 Testing Strategy

#### 3.6.1 Unit Tests

**Lambda Cache Tests**:
```java
class LambdaCacheTest {
    @Test
    void identicalLambdasShouldReturnSameInstance() {
        Predicate<String> lambda1 = s -> s.length() > 0;
        Predicate<String> lambda2 = s -> s.length() > 0;
        
        LambdaCache cache = new LambdaCache();
        Predicate<String> cached1 = cache.cacheLambda(lambda1);
        Predicate<String> cached2 = cache.cacheLambda(lambda2);
        
        assertThat(cached1).isSameAs(cached2);
    }
    
    @Test
    void differentLambdasShouldReturnDifferentInstances() {
        Predicate<String> lambda1 = s -> s.length() > 0;
        Predicate<String> lambda2 = s -> s -> s.isEmpty();
        
        LambdaCache cache = new LambdaCache();
        Predicate<String> cached1 = cache.cacheLambda(lambda1);
        Predicate<String> cached2 = cache.cacheLambda(lambda2);
        
        assertThat(cached1).isNotSameAs(cached2);
    }
}
```

**Bytecode Transformation Tests**:
```java
class NodeSharingTransformerTest {
    @Test
    void shouldExtractIdenticalLambdas() {
        Class<?> transformed = transformer.transform(
            TestConstraintProvider.class
        );
        
        // Verify static fields exist
        Field[] fields = transformed.getDeclaredFields();
        assertThat(fields).anyMatch(f -> 
            f.getName().equals("$predicate1") && 
            Modifier.isStatic(f.getModifiers()) &&
            Modifier.isFinal(f.getModifiers())
        );
    }
    
    @Test
    void shouldReplaceLambdaWithFieldReference() {
        Class<?> transformed = transformer.transform(
            TestConstraintProvider.class
        );
        
        // Verify methods use field references
        Method method = transformed.getDeclaredMethod("constraintA");
        byte[] bytecode = method.getCode();
        // Check for GETSTATIC instruction instead of invokedynamic
    }
}
```

#### 3.6.2 Integration Tests

**Node Sharing Effectiveness Test**:
```java
class NodeSharingIntegrationTest {
    @Test
    void shouldShareFilterNodes() {
        SolverConfig config = new SolverConfig()
            .withConstraintStreamAutomaticNodeSharing(true);
        
        Solver<TestSolution> solver = SolverFactory.create(config).buildSolver();
        
        // Verify node count in network
        NodeNetwork network = solver.getScoreDirector()
            .getConstraintSessionFactory()
            .getNodeNetwork();
        
        // Should have fewer nodes than without sharing
        assertThat(network.getNodeCount()).isLessThan(
            getBaselineNodeCount()
        );
    }
}
```

**Performance Benchmark**:
```java
class NodeSharingBenchmark {
    @Benchmark
    void withoutNodeSharing() {
        SolverConfig config = new SolverConfig()
            .withConstraintStreamAutomaticNodeSharing(false);
        // Run solver and measure time
    }
    
    @Benchmark
    void withNodeSharing() {
        SolverConfig config = new SolverConfig()
            .withConstraintStreamAutomaticNodeSharing(true);
        // Run solver and measure time
    }
}
```

#### 3.6.3 Existing Test Compatibility

Ensure all existing tests in `AbstractUniConstraintStreamNodeSharingTest` continue to pass:
- Filter tests
- Join tests
- IfExists tests
- GroupBy tests
- Map/FlattenLast tests
- Distinct/Concat tests
- Precompute tests

## 4. Implementation Phases

### Phase 1: Community Edition Enhancements (Week 1-2)

**Goals**: Improve runtime node sharing without bytecode transformation

**Tasks**:
1. Implement `LambdaCache` class
2. Integrate `LambdaCache` into `BavetConstraintFactory`
3. Update stream constructors to cache lambdas
4. Add unit tests for `LambdaCache`
5. Verify existing tests still pass
6. Document community edition limitations

**Deliverables**:
- `LambdaCache` implementation
- Updated `BavetConstraintFactory`
- Test suite for lambda caching
- Documentation updates

### Phase 2: Bytecode Analysis (Week 3-4)

**Goals**: Build infrastructure for analyzing ConstraintProvider classes

**Tasks**:
1. Implement `ConstraintProviderAnalyzer`
2. Implement `LambdaFindingVisitor` (ASM)
3. Implement `LambdaInfo` data structure
4. Implement `LambdaKey` for deduplication
5. Add unit tests for lambda detection
6. Test with various lambda patterns

**Deliverables**:
- Bytecode analysis framework
- Lambda detection and serialization
- Test coverage for analysis

### Phase 3: Bytecode Transformation (Week 5-6)

**Goals**: Transform ConstraintProvider classes to enable node sharing

**Tasks**:
1. Implement `LambdaDeduplicator`
2. Implement `FieldAddingVisitor` (ASM)
3. Implement `LambdaReplacingVisitor` (ASM)
4. Implement `NodeSharingTransformer`
5. Implement `NodeSharedClassLoader`
6. Add unit tests for transformation
7. Test with complex lambda patterns

**Deliverables**:
- Complete bytecode transformation pipeline
- Transformed class generation
- Comprehensive test coverage

### Phase 4: Enterprise Integration (Week 7-8)

**Goals**: Integrate transformation into Enterprise edition

**Tasks**:
1. Implement `DefaultConstraintProviderNodeSharer`
2. Update `GreyCOSSolverEnterpriseService`
3. Update `BavetConstraintSessionFactory` to use node sharer
4. Implement `NodeSharingValidator`
5. Add configuration integration
6. Add error handling and logging
7. Test with real ConstraintProviders

**Deliverables**:
- Enterprise node sharing implementation
- Configuration support
- Validation and error handling

### Phase 5: Testing and Documentation (Week 9-10)

**Goals**: Comprehensive testing and documentation

**Tasks**:
1. Run full test suite
2. Add integration tests
3. Create performance benchmarks
4. Write user documentation
5. Write developer documentation
6. Create examples and tutorials
7. Update migration guide

**Deliverables**:
- Complete test coverage
- Performance benchmarks
- User documentation
- Developer documentation
- Examples and tutorials

## 5. Risk Assessment and Mitigation

### 5.1 Technical Risks

**Risk 1: Bytecode transformation bugs**
- **Impact**: High - could cause runtime errors
- **Mitigation**: 
  - Extensive unit testing
  - Fallback to original class on failure
  - Logging and diagnostics

**Risk 2: Lambda serialization complexity**
- **Impact**: Medium - may not capture all lambda patterns
- **Mitigation**:
  - Support common patterns first
  - Document limitations
  - Gradual enhancement

**Risk 3: Performance overhead**
- **Impact**: Medium - transformation time
- **Mitigation**:
  - Cache transformed classes
  - Lazy transformation
  - Benchmark and optimize

**Risk 4: Compatibility issues**
- **Impact**: High - may break existing code
- **Mitigation**:
  - Maintain backward compatibility
  - Extensive testing
  - Beta testing period

### 5.2 Project Risks

**Risk 1: Timeline overruns**
- **Impact**: Medium - delayed release
- **Mitigation**:
  - Phased approach
  - Regular milestones
  - Scope management

**Risk 2: Resource constraints**
- **Impact**: Medium - incomplete implementation
- **Mitigation**:
  - Prioritize features
  - Community edition first
  - Enterprise edition later

## 6. Success Criteria

### 6.1 Functional Requirements

- [ ] Node sharing works for filter operations
- [ ] Node sharing works for map operations
- [ ] Node sharing works for join operations
- [ ] Node sharing works for groupBy operations
- [ ] Node sharing works for ifExists/ifNotExists operations
- [ ] Configuration option works correctly
- [ ] Validation catches invalid ConstraintProviders
- [ ] Graceful degradation on transformation failure

### 6.2 Performance Requirements

- [ ] Node sharing reduces node count by 20%+ in typical use cases
- [ ] Move evaluation speed improves by 10%+ in typical use cases
- [ ] Transformation overhead < 100ms for typical ConstraintProviders
- [ ] No performance regression when disabled

### 6.3 Quality Requirements

- [ ] All existing tests pass
- [ ] New test coverage > 80%
- [ ] Zero critical bugs in beta testing
- [ ] Documentation is clear and complete

### 6.4 Compatibility Requirements

- [ ] Backward compatible with existing ConstraintProviders
- [ ] Works with all score types
- [ ] Works with all constraint stream operations
- [ ] Compatible with existing GreyCOS features

## 7. Future Enhancements

### 7.1 Advanced Lambda Patterns

- Support for complex captured variable scenarios
- Support for method references with bound receivers
- Support for lambda serialization across class loaders

### 7.2 Performance Optimizations

- Incremental transformation for hot reload
- Parallel transformation for large ConstraintProviders
- Caching of transformation results

### 7.3 Developer Experience

- IDE plugin for visualizing node sharing
- Diagnostic tools for identifying sharing opportunities
- Warnings for missed sharing opportunities

### 7.4 Community Edition Enhancements

- Compile-time lambda deduplication via annotation processing
- Source code transformation instead of bytecode transformation
- Heuristic-based lambda sharing

## 8. Conclusion

This plan provides a comprehensive roadmap for reimplementing automatic node sharing in GreyCOS Solver. The two-tier approach ensures that:

1. **Community users** get improved node sharing through enhanced runtime deduplication
2. **Enterprise users** get maximum performance through bytecode transformation
3. **Existing code** remains compatible and functional
4. **Future enhancements** can be added incrementally

The phased implementation allows for iterative development, testing, and refinement, reducing risk and ensuring a high-quality final product.

## Appendix A: Key Files Reference

### Configuration
- [`ScoreDirectorFactoryConfig.java`](core/src/main/java/ai/greycos/solver/core/config/score/director/ScoreDirectorFactoryConfig.java) - Configuration class

### Core Implementation
- [`BavetConstraintFactory.java`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/BavetConstraintFactory.java) - Constraint stream factory with node sharing
- [`BavetAbstractConstraintStream.java`](core/src/main/java/ai/greycos/solver/core/impl/bavet/common/BavetAbstractConstraintStream.java) - Base constraint stream class
- [`ConstraintNodeBuildHelper.java`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/common/ConstraintNodeBuildHelper.java) - Node building helper

### Stream Examples
- [`BavetFilterUniConstraintStream.java`](core/src/main/java/ai/greycos/solver/core/impl/score/stream/bavet/uni/BavetFilterUniConstraintStream.java) - Filter stream example

### Enterprise Integration
- [`GreyCOSSolverEnterpriseService.java`](core/src/main/java/ai/greycos/solver/core/enterprise/GreyCOSSolverEnterpriseService.java) - Enterprise service interface

### Tests
- [`AbstractUniConstraintStreamNodeSharingTest.java`](core/src/test/java/ai/greycos/solver/core/impl/score/stream/common/uni/AbstractUniConstraintStreamNodeSharingTest.java) - Node sharing tests

### Documentation
- [`official_docs_description_ns.md`](text_utils/features/node_sharing/official_docs_description_ns.md) - Official OptaPlanner documentation

## Appendix B: Example Code

### B.1 Community Edition Example

```java
public class CommunityConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            // These two filters will share a node
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Shift for Ann"),
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Another constraint for Ann")
        };
    }
}
```

### B.2 Enterprise Edition Example

```java
public class EnterpriseConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            // After transformation, these share the same static field
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Shift for Ann"),
            factory.forEach(Shift.class)
                .filter(shift -> shift.getEmployee().getName().equals("Ann"))
                .penalize("Another constraint for Ann")
        };
    }
}

// Transformed to:
public class EnterpriseConstraintProvider$NodeShared 
        implements ConstraintProvider {
    
    private static final Predicate<Shift> $predicate1 = 
        shift -> shift.getEmployee().getName().equals("Ann");
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            factory.forEach(Shift.class)
                .filter($predicate1)  // Uses shared field
                .penalize("Shift for Ann"),
            factory.forEach(Shift.class)
                .filter($predicate1)  // Uses shared field
                .penalize("Another constraint for Ann")
        };
    }
}
```

## Appendix C: Performance Metrics

### C.1 Baseline Metrics (Without Node Sharing)

- Node count: 100
- Move evaluation time: 100ms
- Memory usage: 10MB

### C.2 Expected Metrics (With Node Sharing)

**Community Edition**:
- Node count: 80-90 (10-20% reduction)
- Move evaluation time: 90-95ms (5-10% improvement)
- Memory usage: 9MB (10% reduction)

**Enterprise Edition**:
- Node count: 60-70 (30-40% reduction)
- Move evaluation time: 80-85ms (15-20% improvement)
- Memory usage: 8MB (20% reduction)

### C.3 Transformation Overhead

- Class analysis time: < 10ms
- Transformation time: < 50ms
- Class loading time: < 10ms
- Total overhead: < 100ms (one-time cost)

---

**Document Version**: 1.0  
**Last Updated**: 2024  
**Author**: GreyCOS Solver Team  
**Status**: Planning Phase
