# OptaPlanner Nearby Feature - Test Examples and Usage Patterns

This document provides comprehensive test examples and usage patterns for the nearby feature implementation.

## Basic Test Examples

### 1. Configuration Validation Tests

```java
@Test
void testNearbySelectionConfigValidation() {
    // Test missing origin selector
    NearbySelectionConfig config = new NearbySelectionConfig();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContaining("lacks an origin selector config");
    
    // Test multiple origin selectors
    config.setOriginEntitySelectorConfig(new EntitySelectorConfig());
    config.setOriginValueSelectorConfig(new ValueSelectorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContaining("multiple origin selector configs");
    
    // Test missing distance meter
    config = new NearbySelectionConfig();
    config.setOriginEntitySelectorConfig(new EntitySelectorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.validateNearby(SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL))
        .withMessageContaining("nearbyDistanceMeterClass");
}

@Test
void testNearbySelectionConfigInheritance() {
    NearbySelectionConfig parent = new NearbySelectionConfig()
        .withNearbyDistanceMeterClass(MyDistanceMeter.class)
        .withNearbySelectionDistributionType(NearbySelectionDistributionType.LINEAR);
    
    NearbySelectionConfig child = new NearbySelectionConfig()
        .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK);
    
    child.inherit(parent);
    
    assertThat(child.getNearbyDistanceMeterClass()).isEqualTo(MyDistanceMeter.class);
    assertThat(child.getNearbySelectionDistributionType()).isEqualTo(NearbySelectionDistributionType.BLOCK);
}
```

### 2. Distance Meter Tests

```java
@Test
void testCustomDistanceMeter() {
    NearbyDistanceMeter<Location, Location> distanceMeter = new LocationDistanceMeter();
    
    Location origin = new Location(0, 0);
    Location destination = new Location(3, 4);
    
    double distance = distanceMeter.getNearbyDistance(origin, destination);
    assertThat(distance).isEqualTo(5.0); // Euclidean distance
    
    // Test symmetry (if applicable)
    double reverseDistance = distanceMeter.getNearbyDistance(destination, origin);
    assertThat(reverseDistance).isEqualTo(distance);
}

@Test
void testDistanceMeterStateless() {
    NearbyDistanceMeter<Location, Location> meter1 = new LocationDistanceMeter();
    NearbyDistanceMeter<Location, Location> meter2 = new LocationDistanceMeter();
    
    Location origin = new Location(0, 0);
    Location destination = new Location(1, 1);
    
    double distance1 = meter1.getNearbyDistance(origin, destination);
    double distance2 = meter2.getNearbyDistance(origin, destination);
    
    assertThat(distance1).isEqualTo(distance2);
}
```

### 3. Random Distribution Tests

```java
@Test
void testLinearDistribution() {
    NearbyRandom random = new LinearDistributionNearbyRandom(10);
    
    Random testRandom = new Random(0);
    int[] counts = new int[10];
    
    // Generate many samples
    for (int i = 0; i < 10000; i++) {
        int index = random.nextInt(testRandom, 10);
        counts[index]++;
    }
    
    // Linear distribution should favor smaller indices
    for (int i = 1; i < 10; i++) {
        assertThat(counts[i-1]).isGreaterThan(counts[i]);
    }
}

@Test
void testBlockDistribution() {
    NearbyRandom random = new BlockDistributionNearbyRandom(2, 5, 0.5, 0.1);
    
    Random testRandom = new Random(0);
    int[] counts = new int[20];
    
    for (int i = 0; i < 10000; i++) {
        int index = random.nextInt(testRandom, 20);
        counts[index]++;
    }
    
    // Block distribution should create blocks of similar probability
    // This is a simplified test - actual block behavior is more complex
    assertThat(counts[0]).isGreaterThan(0);
    assertThat(counts[19]).isLessThan(counts[0]);
}

@Test
void testDistributionFactory() {
    NearbySelectionConfig config = new NearbySelectionConfig();
    config.setLinearDistributionSizeMaximum(5);
    
    NearbyRandom random = NearbyRandomFactory.create(config).buildNearbyRandom(true);
    assertThat(random).isInstanceOf(LinearDistributionNearbyRandom.class);
    
    // Test with no random selection
    NearbyRandom noRandom = NearbyRandomFactory.create(config).buildNearbyRandom(false);
    assertThat(noRandom).isNull();
}
```

### 4. Distance Matrix Tests

```java
@Test
void testNearbyDistanceMatrix() {
    List<Location> destinations = Arrays.asList(
        new Location(1, 0),  // distance 1
        new Location(0, 1),  // distance 1
        new Location(2, 0),  // distance 2
        new Location(0, 2)   // distance 2
    );
    
    NearbyDistanceMatrix<Location, Location> matrix = new NearbyDistanceMatrix<>(
        new LocationDistanceMeter(),
        1,
        destinations.iterator(),
        origin -> destinations.size()
    );
    
    Location origin = new Location(0, 0);
    matrix.addAllDestinations(origin);
    
    // Destinations should be sorted by distance
    assertThat(matrix.getDestination(origin, 0)).isEqualTo(new Location(1, 0));
    assertThat(matrix.getDestination(origin, 1)).isEqualTo(new Location(0, 1));
    assertThat(matrix.getDestination(origin, 2)).isEqualTo(new Location(2, 0));
    assertThat(matrix.getDestination(origin, 3)).isEqualTo(new Location(0, 2));
}

@Test
void testDistanceMatrixMemoryOptimization() {
    NearbySelectionConfig config = new NearbySelectionConfig();
    config.setLinearDistributionSizeMaximum(5);
    
    NearbyRandom random = NearbyRandomFactory.create(config).buildNearbyRandom(true);
    assertThat(random.getOverallSizeMaximum()).isEqualTo(5);
    
    // Test with Integer.MAX_VALUE
    config.setLinearDistributionSizeMaximum(Integer.MAX_VALUE);
    NearbyRandom largeRandom = NearbyRandomFactory.create(config).buildNearbyRandom(true);
    assertThat(largeRandom.getOverallSizeMaximum()).isEqualTo(Integer.MAX_VALUE);
}
```

### 5. Selector Tests

```java
@Test
void testNearEntityNearbyValueSelector() {
    // Setup mock entities and values
    EntitySelector<TestSolution> entitySelector = mock(EntitySelector.class);
    ValueSelector<TestSolution> valueSelector = mock(ValueSelector.class);
    
    NearbyDistanceMeter<Entity, Value> distanceMeter = mock(NearbyDistanceMeter.class);
    NearbyRandom nearbyRandom = mock(NearbyRandom.class);
    
    NearEntityNearbyValueSelector<TestSolution> selector = 
        new NearEntityNearbyValueSelector<>(valueSelector, entitySelector, 
            distanceMeter, nearbyRandom, true);
    
    // Test basic properties
    assertThat(selector.getVariableDescriptor()).isEqualTo(valueSelector.getVariableDescriptor());
    assertThat(selector.isCountable()).isEqualTo(valueSelector.isCountable());
    assertThat(selector.isNeverEnding()).isTrue();
}

@Test
void testNearbySelectorEquality() {
    EntitySelector<TestSolution> entitySelector1 = mock(EntitySelector.class);
    EntitySelector<TestSolution> entitySelector2 = mock(EntitySelector.class);
    ValueSelector<TestSolution> valueSelector = mock(ValueSelector.class);
    
    NearbyDistanceMeter<Entity, Value> distanceMeter = mock(NearbyDistanceMeter.class);
    NearbyRandom nearbyRandom = mock(NearbyRandom.class);
    
    NearEntityNearbyValueSelector<TestSolution> selector1 = 
        new NearEntityNearbyValueSelector<>(valueSelector, entitySelector1, 
            distanceMeter, nearbyRandom, true);
    
    NearEntityNearbyValueSelector<TestSolution> selector2 = 
        new NearEntityNearbyValueSelector<>(valueSelector, entitySelector1, 
            distanceMeter, nearbyRandom, true);
    
    NearEntityNearbyValueSelector<TestSolution> selector3 = 
        new NearEntityNearbyValueSelector<>(valueSelector, entitySelector2, 
            distanceMeter, nearbyRandom, true);
    
    assertThat(selector1).isEqualTo(selector2);
    assertThat(selector1).isNotEqualTo(selector3);
}
```

## Usage Patterns

### 1. Vehicle Routing Problem (VRP)

```java
public class VRPDistanceMeter implements NearbyDistanceMeter<Customer, Customer> {
    @Override
    public double getNearbyDistance(Customer origin, Customer destination) {
        return origin.getLocation().getDistanceTo(destination.getLocation());
    }
}

// Configuration for VRP
NearbySelectionConfig nearbyConfig = new NearbySelectionConfig()
    .withOriginEntitySelectorConfig(EntitySelectorConfig.newMimicSelectorConfig("vehicleSelector"))
    .withNearbyDistanceMeterClass(VRPDistanceMeter.class)
    .withNearbySelectionDistributionType(NearbySelectionDistributionType.LINEAR)
    .withLinearDistributionSizeMaximum(10);

// Use in customer assignment
ValueSelectorConfig customerSelector = new ValueSelectorConfig()
    .withVariableName("vehicle")
    .withNearbySelectionConfig(nearbyConfig);
```

### 2. Exam Timetabling

```java
public class ExamDistanceMeter implements NearbyDistanceMeter<Exam, Period> {
    @Override
    public double getNearbyDistance(Exam origin, Period destination) {
        // Distance based on time proximity and room compatibility
        int timeDistance = Math.abs(origin.getPeriod().getDayIndex() - destination.getDayIndex()) * 24
                         + Math.abs(origin.getPeriod().getHourOfDay() - destination.getHourOfDay());
        
        // Penalize incompatible rooms
        if (!destination.getRoom().isCompatibleWith(origin.getRoom())) {
            timeDistance += 1000;
        }
        
        return timeDistance;
    }
}

// Configuration for exam scheduling
NearbySelectionConfig nearbyConfig = new NearbySelectionConfig()
    .withOriginEntitySelectorConfig(EntitySelectorConfig.newMimicSelectorConfig("examSelector"))
    .withNearbyDistanceMeterClass(ExamDistanceMeter.class)
    .withNearbySelectionDistributionType(NearbySelectionDistributionType.BLOCK)
    .withBlockDistributionSizeMinimum(1)
    .withBlockDistributionSizeMaximum(5)
    .withBlockDistributionSizeRatio(0.3);
```

### 3. Cloud Balancing

```java
public class CloudDistanceMeter implements NearbyDistanceMeter<Process, Computer> {
    @Override
    public double getNearbyDistance(Process origin, Computer destination) {
        // Distance based on resource compatibility
        double cpuDistance = Math.abs(origin.getRequiredCpuPower() - destination.getAvailableCpuPower());
        double memoryDistance = Math.abs(origin.getRequiredMemory() - destination.getAvailableMemory());
        double networkDistance = Math.abs(origin.getRequiredNetworkBandwidth() - destination.getAvailableNetworkBandwidth());
        
        return cpuDistance + memoryDistance + networkDistance;
    }
}

// Configuration for cloud balancing
NearbySelectionConfig nearbyConfig = new NearbySelectionConfig()
    .withOriginEntitySelectorConfig(EntitySelectorConfig.newMimicSelectorConfig("processSelector"))
    .withNearbyDistanceMeterClass(CloudDistanceMeter.class)
    .withNearbySelectionDistributionType(NearbySelectionDistributionType.PARABOLIC)
    .withParabolicDistributionSizeMaximum(20);
```

### 4. Nurse Rostering

```java
public class NurseDistanceMeter implements NearbyDistanceMeter<Nurse, Shift> {
    @Override
    public double getNearbyDistance(Nurse origin, Shift destination) {
        // Distance based on skill matching and preference
        double skillDistance = calculateSkillDistance(origin, destination);
        double preferenceDistance = calculatePreferenceDistance(origin, destination);
        double contractDistance = calculateContractDistance(origin, destination);
        
        return skillDistance + preferenceDistance + contractDistance;
    }
    
    private double calculateSkillDistance(Nurse nurse, Shift shift) {
        // Implementation based on required vs available skills
        return 0.0;
    }
    
    private double calculatePreferenceDistance(Nurse nurse, Shift shift) {
        // Implementation based on nurse preferences
        return 0.0;
    }
    
    private double calculateContractDistance(Nurse nurse, Shift shift) {
        // Implementation based on contract rules
        return 0.0;
    }
}
```

## Performance Testing

### 1. Memory Usage Testing

```java
@Test
void testDistanceMatrixMemoryUsage() {
    int entityCount = 1000;
    int valueCount = 100;
    
    // Create test data
    List<Entity> entities = createEntities(entityCount);
    List<Value> values = createValues(valueCount);
    
    // Measure memory before
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();
    long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
    
    // Create distance matrix
    NearbyDistanceMatrix<Entity, Value> matrix = new NearbyDistanceMatrix<>(
        new TestDistanceMeter(),
        entityCount,
        values.iterator(),
        origin -> valueCount
    );
    
    entities.forEach(matrix::addAllDestinations);
    
    // Measure memory after
    long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
    long memoryUsed = memoryAfter - memoryBefore;
    
    // Memory should be reasonable (not excessive)
    long expectedMemory = entityCount * valueCount * 16; // Rough estimate
    assertThat(memoryUsed).isLessThan(expectedMemory * 2);
}
```

### 2. Performance Testing

```java
@Test
void testNearbySelectionPerformance() {
    int entityCount = 1000;
    int valueCount = 100;
    
    // Setup selectors
    EntitySelector<TestSolution> entitySelector = createEntitySelector(entityCount);
    ValueSelector<TestSolution> valueSelector = createValueSelector(valueCount);
    
    NearbyDistanceMeter<Entity, Value> distanceMeter = new TestDistanceMeter();
    NearbyRandom nearbyRandom = new LinearDistributionNearbyRandom(10);
    
    NearEntityNearbyValueSelector<TestSolution> selector = 
        new NearEntityNearbyValueSelector<>(valueSelector, entitySelector, 
            distanceMeter, nearbyRandom, true);
    
    // Measure selection time
    long startTime = System.nanoTime();
    
    for (int i = 0; i < 10000; i++) {
        Entity entity = entitySelector.iterator().next();
        selector.iterator(entity).next();
    }
    
    long endTime = System.nanoTime();
    long duration = endTime - startTime;
    
    // Should complete in reasonable time
    assertThat(duration).isLessThan(TimeUnit.SECONDS.toNanos(10));
}
```

## Integration Testing

### 1. Full Solver Integration

```java
@Test
void testNearbySelectionInSolver() {
    // Create solver configuration with nearby selection
    SolverConfig solverConfig = new SolverConfig()
        .withSolutionClass(TestSolution.class)
        .withEntityClasses(TestEntity.class)
        .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
            .withEasyScoreCalculatorClass(TestEasyScoreCalculator.class))
        .withConstructionHeuristicPhase(new ConstructionHeuristicPhaseConfig())
        .withLocalSearchPhase(new LocalSearchPhaseConfig()
            .withMoveSelectorConfig(new ChangeMoveSelectorConfig()
                .withValueSelectorConfig(new ValueSelectorConfig()
                    .withNearbySelectionConfig(createNearbyConfig()))));
    
    Solver<TestSolution> solver = SolverFactory.create(solverConfig).buildSolver();
    
    TestSolution solution = createTestSolution();
    TestSolution solvedSolution = solver.solve(solution);
    
    // Verify solution is improved
    assertThat(solvedSolution.getScore()).isGreaterThanOrEqualTo(solution.getScore());
}
```

### 2. Configuration File Testing

```xml
<!-- solverConfig.xml -->
<solver>
    <solutionClass>com.example.TestSolution</solutionClass>
    <entityClass>com.example.TestEntity</entityClass>
    
    <scoreDirectorFactory>
        <easyScoreCalculatorClass>com.example.TestEasyScoreCalculator</easyScoreCalculatorClass>
    </scoreDirectorFactory>
    
    <localSearch>
        <moveSelector>
            <changeMoveSelector>
                <valueSelector>
                    <nearbySelection>
                        <originEntitySelector>
                            <mimicSelectorRef>entitySelector</mimicSelectorRef>
                        </originEntitySelector>
                        <nearbyDistanceMeterClass>com.example.TestDistanceMeter</nearbyDistanceMeterClass>
                        <nearbySelectionDistributionType>LINEAR</nearbySelectionDistributionType>
                        <linearDistributionSizeMaximum>10</linearDistributionSizeMaximum>
                    </nearbySelection>
                </valueSelector>
            </changeMoveSelector>
        </moveSelector>
    </localSearch>
</solver>
```

These test examples and usage patterns provide a comprehensive guide for testing and implementing the nearby feature in various optimization scenarios.