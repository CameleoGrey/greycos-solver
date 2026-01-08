# Partitioned Search

## Overview

Partitioned Search is an advanced optimization technique in OptaPlanner that enables parallel solving of large-scale planning problems by dividing them into smaller, independent partitions. Each partition is solved concurrently in separate threads, and the results are merged back into a global solution.

### Key Benefits

- **Parallel Processing**: Utilizes multiple CPU cores to solve partitions simultaneously
- **Scalability**: Handles problems with millions of planning entities by breaking them into manageable chunks
- **Resource Control**: Limits CPU consumption to prevent system starvation
- **Flexibility**: Custom partitioning strategies for different problem types

### When to Use Partitioned Search

Partitioned Search is particularly effective for:

- Problems with a large number of planning entities (thousands to millions)
- Problems where entities can be logically grouped or partitioned
- Scenarios where solution quality can be improved through parallel exploration
- Environments with multi-core processors where CPU resources are available

### When NOT to Use Partitioned Search

Avoid Partitioned Search for:

- Small problems where overhead outweighs benefits
- Problems with strong interdependencies between entities
- Scenarios requiring real-time responsiveness (partitioning adds overhead)
- Single-core or resource-constrained environments

## Architecture

### Core Components

The Partitioned Search architecture consists of several key components:

![Partitioned Search Architecture](partitioned-search-architecture.png)

#### PartitionedSearchPhase

The main phase implementation that orchestrates the partitioned solving process.

- **Package**: `org.optaplanner.core.impl.partitionedsearch`
- **Interface**: [`PartitionedSearchPhase`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionedSearchPhase.java)
- **Implementation**: [`DefaultPartitionedSearchPhase`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhase.java)

Key responsibilities:

- Manages the overall partitioned search lifecycle
- Creates and coordinates partition solver threads
- Merges partition results back into the global solution
- Handles thread pool management and termination

#### SolutionPartitioner

The interface that defines how to split a solution into partitions.

- **Package**: `org.optaplanner.core.impl.partitionedsearch.partitioner`
- **Interface**: [`SolutionPartitioner`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java)

Key method:

```java
List<Solution_> splitWorkingSolution(ScoreDirector<Solution_> scoreDirector, 
                                     Integer runnablePartThreadLimit);
```

The partitioner must:

- Split planning entities into exactly one partition each
- Handle problem facts (can be shared or cloned across partitions)
- Support partition cloning (distinct from solution cloning)
- Return at least one partition

#### PartitionSolver

A specialized solver instance that solves a single partition.

- **Package**: `org.optaplanner.core.impl.partitionedsearch`
- **Class**: [`PartitionSolver`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java)

Characteristics:

- Runs in a separate thread
- Cannot be terminated early (controlled by parent)
- Does not support problem fact changes
- Executes its own phase configuration

#### PartitionQueue

A thread-safe queue that coordinates communication between partition threads and the main solver thread.

- **Package**: `org.optaplanner.core.impl.partitionedsearch.queue`
- **Class**: [`PartitionQueue`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java)

Features:

- Manages events from all partition threads
- Ensures only the latest move from each partition is processed
- Handles partition completion and exception propagation
- Provides ordered iteration over partition updates

#### PartitionChangeMove

A move that applies changes from a partition to the global solution.

- **Package**: `org.optaplanner.core.impl.partitionedsearch.scope`
- **Class**: [`PartitionChangeMove`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java)

Functionality:

- Captures variable assignments from a partition's best solution
- Rebases entities and values to the parent solver's context
- Applies changes atomically to the global solution
- Cannot be undone (designed for one-way application)

### Execution Flow

The partitioned search execution follows this sequence:

1. **Partition Creation**: The [`SolutionPartitioner`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java) splits the working solution into N partitions
2. **Thread Pool Creation**: A fixed thread pool is created with N threads
3. **Partition Solvers**: Each partition is assigned to a [`PartitionSolver`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/PartitionSolver.java) running in its own thread
4. **Parallel Solving**: All partitions solve concurrently using their configured phases
5. **Event Communication**: Each partition sends best solution updates via [`PartitionQueue`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java)
6. **Merge Process**: The main solver applies [`PartitionChangeMove`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/scope/PartitionChangeMove.java) to merge improvements
7. **Completion**: When all partitions finish, the phase ends with the merged best solution

### Thread Model

Partitioned Search uses a specific threading model:

- **Main Solver Thread**: Coordinates partitioned search phase, processes partition updates
- **Partition Threads**: One thread per partition, runs independently
- **Thread Pool**: Fixed-size pool with one thread per partition
- **Semaphore**: Optional limit on concurrently runnable threads (CPU throttling)

Thread naming convention: `OptaPool-{poolId}-PartThread-{partIndex}`

## Configuration

### XML Configuration

Configure partitioned search in your solver configuration XML:

```xml
<solver>
  <partitionedSearch>
    <solutionPartitionerClass>com.example.MyPartitioner</solutionPartitionerClass>
    <solutionPartitionerCustomProperties>
      <property name="partCount" value="4"/>
      <property name="minimumEntityCount" value="100"/>
    </solutionPartitionerCustomProperties>
    <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
    
    <!-- Phases to run on each partition -->
    <constructionHeuristic/>
    <localSearch>
      <termination>
        <stepCountLimit>1000</stepCountLimit>
      </termination>
    </localSearch>
  </partitionedSearch>
</solver>
```

### Java API Configuration

Configure using the Java API:

```java
SolverConfig solverConfig = SolverConfig.create()
    .withPhaseConfigList(
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(MyPartitioner.class)
            .withSolutionPartitionerCustomProperties(Map.of(
                "partCount", "4",
                "minimumEntityCount", "100"
            ))
            .withRunnablePartThreadLimit("AUTO")
            .withPhaseConfigs(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(
                        new TerminationConfig()
                            .withStepCountLimit(1000)
                    )
            )
    );
```

### Configuration Properties

#### solutionPartitionerClass

The class that implements [`SolutionPartitioner`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java) to split the solution.

- **Type**: `Class<? extends SolutionPartitioner<?>>`
- **Required**: Yes
- **Default**: None (must be specified)

#### solutionPartitionerCustomProperties

Custom properties to configure the partitioner instance.

- **Type**: `Map<String, String>`
- **Required**: No
- **Usage**: Setters on the partitioner class are called with these values

#### runnablePartThreadLimit

Limits the number of concurrently runnable partition threads to prevent CPU starvation.

- **Type**: `String`
- **Values**:
  - `null` or `"AUTO"`: Uses `availableProcessors - 2` (default)
  - `"UNLIMITED"`: No limit, all partitions run concurrently
  - Number: Specific limit (e.g., `"4"`)
- **Default**: `"AUTO"`

The number of threads created always equals the number of partitions. This limit only controls how many can be actively running at once.

#### phaseConfigList

The phases to execute on each partition.

- **Type**: `List<PhaseConfig>`
- **Required**: No
- **Default**: Construction Heuristic + Local Search (if not specified)

Common phases:

- [`ConstructionHeuristicPhaseConfig`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/constructionheuristic/ConstructionHeuristicPhaseConfig.java)
- [`LocalSearchPhaseConfig`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/localsearch/LocalSearchPhaseConfig.java)
- [`CustomPhaseConfig`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/phase/custom/CustomPhaseConfig.java)
- [`ExhaustiveSearchPhaseConfig`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/config/exhaustivesearch/ExhaustiveSearchPhaseConfig.java)

## Implementation

### Creating a Custom SolutionPartitioner

To implement partitioned search, you must create a class that implements [`SolutionPartitioner`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java):

```java
package com.example;

import java.util.ArrayList;
import java.util.List;
import org.optaplanner.core.api.score.director.ScoreDirector;
import org.optaplanner.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import com.example.domain.MyEntity;
import com.example.domain.MySolution;

public class MyPartitioner implements SolutionPartitioner<MySolution> {

    // Configurable properties (set via custom properties)
    private int partCount = 4;
    private int minimumEntityCount = 50;

    // Setters for custom properties
    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }

    public void setMinimumEntityCount(int minimumEntityCount) {
        this.minimumEntityCount = minimumEntityCount;
    }

    @Override
    public List<MySolution> splitWorkingSolution(
            ScoreDirector<MySolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        
        MySolution originalSolution = scoreDirector.getWorkingSolution();
        List<MyEntity> allEntities = originalSolution.getEntityList();
        
        // Adjust part count based on entity count
        int actualPartCount = partCount;
        if (allEntities.size() / actualPartCount < minimumEntityCount) {
            actualPartCount = Math.max(1, allEntities.size() / minimumEntityCount);
        }
        
        List<MySolution> partitions = new ArrayList<>(actualPartCount);
        
        // Create partition solutions
        for (int i = 0; i < actualPartCount; i++) {
            MySolution partition = new MySolution();
            // Clone problem facts (shared across partitions)
            partition.setValueList(new ArrayList<>(originalSolution.getValueList()));
            // Entities will be added per partition
            partition.setEntityList(new ArrayList<>());
            partitions.add(partition);
        }
        
        // Distribute entities across partitions
        int partIndex = 0;
        for (MyEntity entity : allEntities) {
            MySolution partition = partitions.get(partIndex);
            
            // Clone the entity for this partition
            MyEntity partitionEntity = new MyEntity(
                entity.getId(),
                entity.getSomeProperty()
            );
            
            // Preserve current assignment if exists
            if (entity.getValue() != null) {
                // Find the corresponding value in this partition
                MyValue partitionValue = findValueInPartition(
                    partition, entity.getValue().getId()
                );
                partitionEntity.setValue(partitionValue);
            }
            
            partition.getEntityList().add(partitionEntity);
            partIndex = (partIndex + 1) % actualPartCount;
        }
        
        return partitions;
    }

    private MyValue findValueInPartition(MySolution partition, Long valueId) {
        return partition.getValueList().stream()
            .filter(v -> v.getId().equals(valueId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Value not found in partition: " + valueId
            ));
    }
}
```

### Partitioning Strategies

Different problems require different partitioning strategies:

#### Round-Robin Partitioning

Distributes entities evenly across partitions in sequence.

- **Pros**: Simple, balanced distribution
- **Cons**: May not respect entity relationships
- **Best for**: Independent entities with no strong relationships

```java
int partIndex = 0;
for (MyEntity entity : allEntities) {
    partitions.get(partIndex).getEntityList().add(cloneEntity(entity));
    partIndex = (partIndex + 1) % partCount;
}
```

#### Geographic/Region-Based Partitioning

Groups entities by geographic region or logical grouping.

- **Pros**: Minimizes cross-partition constraints
- **Cons**: May create unbalanced partitions
- **Best for**: Vehicle routing, territory assignment

```java
Map<String, List<MyEntity>> entitiesByRegion = allEntities.stream()
    .collect(Collectors.groupingBy(MyEntity::getRegion));

int partIndex = 0;
for (List<MyEntity> regionEntities : entitiesByRegion.values()) {
    MySolution partition = partitions.get(partIndex);
    for (MyEntity entity : regionEntities) {
        partition.getEntityList().add(cloneEntity(entity));
    }
    partIndex = (partIndex + 1) % partCount;
}
```

#### Range-Based Partitioning

Divides entities based on a numeric range or ordering.

- **Pros**: Predictable distribution
- **Cons**: May not handle skewed data well
- **Best for**: Time-based problems, ordered entities

```java
int entitiesPerPartition = allEntities.size() / partCount;
for (int i = 0; i < partCount; i++) {
    int start = i * entitiesPerPartition;
    int end = (i == partCount - 1) ? allEntities.size() : (i + 1) * entitiesPerPartition;
    List<MyEntity> partitionEntities = allEntities.subList(start, end);
    partitions.get(i).getEntityList().addAll(
        partitionEntities.stream()
            .map(this::cloneEntity)
            .collect(Collectors.toList())
    );
}
```

#### Constraint-Based Partitioning

Groups entities to minimize cross-partition constraint violations.

- **Pros**: Optimizes for solution quality
- **Cons**: Complex to implement, may be slow
- **Best for**: Problems with complex inter-entity relationships

### Example: Cloud Balancing Partitioner

The Cloud Balancing example demonstrates a practical partitioning implementation:

```java
// From: optaplanner-examples/src/main/java/org/optaplanner/examples/cloudbalancing/optional/partitioner/CloudBalancePartitioner.java
public class CloudBalancePartitioner implements SolutionPartitioner<CloudBalance> {

    private int partCount = 4;
    private int minimumProcessListSize = 25;

    @Override
    public List<CloudBalance> splitWorkingSolution(
            ScoreDirector<CloudBalance> scoreDirector,
            Integer runnablePartThreadLimit) {
        
        CloudBalance originalSolution = scoreDirector.getWorkingSolution();
        List<CloudComputer> originalComputerList = originalSolution.getComputerList();
        List<CloudProcess> originalProcessList = originalSolution.getProcessList();
        
        // Adjust part count based on process count
        int actualPartCount = partCount;
        if (originalProcessList.size() / actualPartCount < minimumProcessListSize) {
            actualPartCount = originalProcessList.size() / minimumProcessListSize;
        }
        
        List<CloudBalance> partList = new ArrayList<>(actualPartCount);
        
        // Create partition solutions
        for (int i = 0; i < actualPartCount; i++) {
            CloudBalance partSolution = new CloudBalance(
                originalSolution.getId(),
                new ArrayList<>(originalComputerList.size() / actualPartCount + 1),
                new ArrayList<>(originalProcessList.size() / actualPartCount + 1)
            );
            partList.add(partSolution);
        }
        
        // Distribute computers round-robin
        int partIndex = 0;
        Map<Long, Pair<Integer, CloudComputer>> idToPartIndexAndComputerMap = new HashMap<>();
        for (CloudComputer originalComputer : originalComputerList) {
            CloudBalance part = partList.get(partIndex);
            CloudComputer computer = new CloudComputer(
                originalComputer.getId(),
                originalComputer.getCpuPower(),
                originalComputer.getMemory(),
                originalComputer.getNetworkBandwidth(),
                originalComputer.getCost()
            );
            part.getComputerList().add(computer);
            idToPartIndexAndComputerMap.put(computer.getId(), Pair.of(partIndex, computer));
            partIndex = (partIndex + 1) % partList.size();
        }
        
        // Distribute processes round-robin, preserving assignments
        partIndex = 0;
        for (CloudProcess originalProcess : originalProcessList) {
            CloudBalance part = partList.get(partIndex);
            CloudProcess process = new CloudProcess(
                originalProcess.getId(),
                originalProcess.getRequiredCpuPower(),
                originalProcess.getRequiredMemory(),
                originalProcess.getRequiredNetworkBandwidth()
            );
            
            // Preserve computer assignment
            if (originalProcess.getComputer() != null) {
                Pair<Integer, CloudComputer> partIndexAndComputer = 
                    idToPartIndexAndComputerMap.get(originalProcess.getComputer().getId());
                if (partIndex != partIndexAndComputer.getKey()) {
                    throw new IllegalStateException(
                        "Process's computer belongs to another partition"
                    );
                }
                process.setComputer(partIndexAndComputer.getValue());
            }
            
            part.getProcessList().add(process);
            partIndex = (partIndex + 1) % partList.size();
        }
        
        return partList;
    }
}
```

## Integration

### Multi-Phase Solver Configuration

Partitioned search can be combined with other phases:

```xml
<solver>
  <!-- Phase 1: Construction Heuristic on full problem -->
  <constructionHeuristic>
    <termination>
      <stepCountLimit>10000</stepCountLimit>
    </termination>
  </constructionHeuristic>
  
  <!-- Phase 2: Partitioned Search for refinement -->
  <partitionedSearch>
    <solutionPartitionerClass>com.example.MyPartitioner</solutionPartitionerClass>
    <runnablePartThreadLimit>AUTO</runnablePartThreadLimit>
    <localSearch>
      <termination>
        <stepCountLimit>5000</stepCountLimit>
      </termination>
    </localSearch>
  </partitionedSearch>
  
  <!-- Phase 3: Final Local Search on merged solution -->
  <localSearch>
    <termination>
      <timeSpentLimit>30s</timeSpentLimit>
    </termination>
  </localSearch>
</solver>
```

### Solver Event Listeners

Monitor partitioned search progress using event listeners:

```java
Solver<MySolution> solver = solverFactory.buildSolver();

solver.addEventListener(event -> {
    if (event instanceof BestSolutionChangedEvent) {
        BestSolutionChangedEvent<MySolution> bestEvent = 
            (BestSolutionChangedEvent<MySolution>) event;
        MySolution newBestSolution = bestEvent.getNewBestSolution();
        System.out.println("New best score: " + newBestSolution.getScore());
    }
});
```

### Phase Lifecycle Listeners

Add listeners for detailed phase events:

```java
((DefaultSolver<MySolution>) solver).addPhaseLifecycleListener(
    new PhaseLifecycleListenerAdapter<MySolution>() {
        @Override
        public void phaseStarted(AbstractPhaseScope<MySolution> phaseScope) {
            if (phaseScope instanceof PartitionedSearchPhaseScope) {
                PartitionedSearchPhaseScope<MySolution> psScope = 
                    (PartitionedSearchPhaseScope<MySolution>) phaseScope;
                System.out.println("Partitioned search started with " + 
                    psScope.getPartCount() + " partitions");
            }
        }
        
        @Override
        public void phaseEnded(AbstractPhaseScope<MySolution> phaseScope) {
            if (phaseScope instanceof PartitionedSearchPhaseScope) {
                System.out.println("Partitioned search ended");
            }
        }
    }
);
```

### Problem Fact Changes

Partitioned search supports problem fact changes:

```java
Solver<MySolution> solver = solverFactory.buildSolver();

// Start solving in background
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<MySolution> future = executor.submit(() -> solver.solve(problem));

// Add problem fact change after solving starts
solver.addProblemChange((workingSolution, scoreDirector) -> {
    MyValue newValue = new MyValue("new-value");
    workingSolution.getValueList().add(newValue);
    scoreDirector.beforeProblemFactAdded(newValue);
});

// Get final solution
MySolution bestSolution = future.get();
```

## Best Practices

### Partition Size Guidelines

- **Minimum**: 25-50 entities per partition for meaningful optimization
- **Optimal**: 100-500 entities per partition for most problems
- **Maximum**: 1000+ entities per partition may reduce parallelism benefits

Calculate optimal partition count:

```java
int optimalPartCount = Math.min(
    Runtime.getRuntime().availableProcessors() - 2,
    totalEntityCount / 100  // 100 entities per partition
);
```

### CPU Resource Management

Use `runnablePartThreadLimit` to prevent CPU starvation:

- **Production servers**: Use `"AUTO"` or specific limit (e.g., `"4"`)
- **Dedicated solver machines**: Use `"UNLIMITED"` for maximum performance
- **Shared environments**: Use conservative limits (e.g., `"2"`)

### Entity Distribution

Ensure balanced entity distribution:

- Avoid partitions with significantly different entity counts
- Consider entity relationships when partitioning
- Validate that each entity appears in exactly one partition

### Problem Fact Handling

- **Shared facts**: Clone problem facts that are read-only
- **Mutable facts**: Ensure thread-safety or clone per partition
- **Large fact sets**: Consider memory overhead of cloning

### Termination Configuration

Configure appropriate termination for partition phases:

```java
LocalSearchPhaseConfig localSearchConfig = new LocalSearchPhaseConfig()
    .withTerminationConfig(
        new TerminationConfig()
            .withStepCountLimit(1000)  // Per partition
            .withScoreCalculationCountLimit(100000)
    );
```

### Testing and Validation

Test partitioners thoroughly:

```java
@Test
void testPartitioner() {
    MySolution solution = createTestSolution(1000);
    ScoreDirector<MySolution> scoreDirector = 
        scoreDirectorFactory.buildScoreDirector();
    scoreDirector.setWorkingSolution(solution);
    
    MyPartitioner partitioner = new MyPartitioner();
    partitioner.setPartCount(4);
    
    List<MySolution> partitions = partitioner.splitWorkingSolution(
        scoreDirector, null
    );
    
    // Validate partition count
    assertThat(partitions).hasSize(4);
    
    // Validate all entities are distributed
    int totalEntities = partitions.stream()
        .mapToInt(p -> p.getEntityList().size())
        .sum();
    assertThat(totalEntities).isEqualTo(solution.getEntityList().size());
    
    // Validate no entity is in multiple partitions
    Set<Long> entityIds = partitions.stream()
        .flatMap(p -> p.getEntityList().stream())
        .map(MyEntity::getId)
        .collect(Collectors.toSet());
    assertThat(entityIds).hasSize(totalEntities);
}
```

## Limitations and Considerations

### Solution Quality

Partitioned search may produce different solution quality compared to solving the entire problem at once:

- **Potential degradation**: Cross-partition constraints are not directly optimized
- **Potential improvement**: Parallel exploration can find better local optima
- **Mitigation**: Use partitioned search as an intermediate phase, followed by full-problem local search

### Memory Usage

Each partition requires its own solution clone:

- **Memory overhead**: Approximately `partCount * solutionSize`
- **Large problems**: May require careful memory management
- **Mitigation**: Use appropriate partition counts and entity sizes

### Thread Safety

Ensure thread-safety in custom code:

- **Partitioner**: Called from main thread, no concurrency concerns
- **Problem facts**: Must be thread-safe if shared across partitions
- **Custom phases**: Must be thread-safe if used in partitioned search

### Termination Behavior

- **Early termination**: All partitions are signaled to terminate
- **Asynchronous termination**: Some partitions may continue briefly after signal
- **Score calculation count**: Maxed across partitions, not summed

### Unsupported Features

The following features are not supported in partitioned search:

- Move thread count within partitions (each partition runs single-threaded)
- Custom phase commands that modify the solution structure
- Real-time constraint streaming during partition solving

## Troubleshooting

### Common Issues

#### Issue: Partitions have very different entity counts

**Symptoms**: Uneven solving times, some partitions finish much faster

**Solutions**:

- Implement smarter partitioning logic
- Use range-based or geographic partitioning
- Add minimum entity count validation

```java
if (partition.getEntityList().size() < minimumEntityCount) {
    throw new IllegalStateException(
        "Partition " + partIndex + " has too few entities: " + 
        partition.getEntityList().size()
    );
}
```

#### Issue: Out of memory errors

**Symptoms**: `OutOfMemoryError` during partitioned search

**Solutions**:

- Reduce partition count
- Increase JVM heap size: `-Xmx4g`
- Optimize problem fact cloning
- Use entity filtering to reduce problem size

#### Issue: Poor solution quality

**Symptoms**: Final solution worse than non-partitioned solving

**Solutions**:

- Add a final local search phase on the merged solution
- Increase partition size for better local optimization
- Review partitioning strategy for cross-partition constraints
- Extend termination time for partition phases

#### Issue: CPU starvation of other processes

**Symptoms**: System becomes unresponsive during solving

**Solutions**:

- Set `runnablePartThreadLimit` to a conservative value
- Use `"AUTO"` instead of `"UNLIMITED"`
- Run solver at lower OS priority

```xml
<runnablePartThreadLimit>2</runnablePartThreadLimit>
```

#### Issue: Exceptions in partition threads

**Symptoms**: `IllegalStateException` with "partIndex" message

**Solutions**:

- Check partitioner for null references
- Validate entity-value relationships before partitioning
- Ensure problem facts are properly cloned

```java
try {
    MySolution solution = solver.solve(problem);
} catch (IllegalStateException e) {
    if (e.getMessage().contains("partIndex")) {
        // Check partitioner implementation
        logger.error("Partition error", e);
    }
    throw e;
}
```

### Debugging

Enable debug logging for partitioned search:

```properties
# Log partitioned search phase details
logging.level.org.optaplanner.core.impl.partitionedsearch=DEBUG

# Log partition queue events
logging.level.org.optaplanner.core.impl.partitionedsearch.queue=TRACE

# Log partition solver lifecycle
logging.level.org.optaplanner.core.impl.solver.AbstractSolver=DEBUG
```

### Performance Monitoring

Monitor partitioned search performance:

```java
solver.addEventListener(event -> {
    if (event instanceof BestSolutionChangedEvent) {
        BestSolutionChangedEvent<?> bestEvent = 
            (BestSolutionChangedEvent<?>) event;
        logger.info("New best score: {}, time: {}ms",
            bestEvent.getNewBestSolution().getScore(),
            bestEvent.getTimeMillisSpent());
    }
});

// After solving
logger.info("Total score calculation count: {}", 
    solver.getScoreCalculationCount());
logger.info("Solving time: {}ms", 
    solver.getSolvingDuration().toMillis());
```

## Advanced Topics

### Nested Partitioned Search

Partitioned search can be nested (partitioned search within partitioned search), though this is rarely beneficial and adds significant complexity.

### Custom Partition Queue

For advanced use cases, you can extend [`PartitionQueue`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/queue/PartitionQueue.java) to implement custom event handling or merge strategies.

### Dynamic Partition Count

Adjust partition count based on problem size:

```java
public class AdaptivePartitioner implements SolutionPartitioner<MySolution> {
    
    private int basePartCount = 4;
    private int entitiesPerPartition = 100;
    
    @Override
    public List<MySolution> splitWorkingSolution(
            ScoreDirector<MySolution> scoreDirector,
            Integer runnablePartThreadLimit) {
        
        MySolution solution = scoreDirector.getWorkingSolution();
        int entityCount = solution.getEntityList().size();
        
        // Calculate optimal partition count
        int optimalPartCount = Math.min(
            basePartCount,
            Math.max(1, entityCount / entitiesPerPartition)
        );
        
        // Respect thread limit if provided
        if (runnablePartThreadLimit != null) {
            optimalPartCount = Math.min(optimalPartCount, runnablePartThreadLimit);
        }
        
        return createPartitions(solution, optimalPartCount);
    }
}
```

### Partition-Specific Constraints

Implement constraints that behave differently in partitioned contexts:

```java
public class PartitionAwareConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
            // Regular constraints
            entityValueConflict(constraintFactory),
            
            // Partition-aware constraint (if needed)
            partitionBalance(constraintFactory)
        };
    }
    
    private Constraint partitionBalance(ConstraintFactory constraintFactory) {
        // Only active in partitioned search
        return constraintFactory.from(MyEntity.class)
            .groupBy(MyEntity::getPartitionId, count())
            .filter((partitionId, count) -> count < 10)
            .penalize("Small partition")
            .asHard(100);
    }
}
```

## API Reference

### Core Interfaces

#### SolutionPartitioner

```java
public interface SolutionPartitioner<Solution_> {
    List<Solution_> splitWorkingSolution(
        ScoreDirector<Solution_> scoreDirector,
        Integer runnablePartThreadLimit
    );
}
```

#### PartitionedSearchPhase

```java
public interface PartitionedSearchPhase<Solution_> extends Phase<Solution_> {
    // Inherits all Phase methods
}
```

### Configuration Classes

#### PartitionedSearchPhaseConfig

```java
public class PartitionedSearchPhaseConfig extends PhaseConfig<PartitionedSearchPhaseConfig> {
    // Properties
    private Class<? extends SolutionPartitioner<?>> solutionPartitionerClass;
    private Map<String, String> solutionPartitionerCustomProperties;
    private String runnablePartThreadLimit;
    private List<PhaseConfig> phaseConfigList;
    
    // With methods
    public PartitionedSearchPhaseConfig withSolutionPartitionerClass(
        Class<? extends SolutionPartitioner<?>> solutionPartitionerClass
    );
    
    public PartitionedSearchPhaseConfig withRunnablePartThreadLimit(
        String runnablePartThreadLimit
    );
    
    public PartitionedSearchPhaseConfig withPhaseConfigs(
        PhaseConfig... phaseConfigs
    );
}
```

### Scope Classes

#### PartitionedSearchPhaseScope

```java
public class PartitionedSearchPhaseScope<Solution_> 
        extends AbstractPhaseScope<Solution_> {
    public Integer getPartCount();
    public PartitionedSearchStepScope<Solution_> getLastCompletedStepScope();
}
```

#### PartitionedSearchStepScope

```java
public class PartitionedSearchStepScope<Solution_> 
        extends AbstractStepScope<Solution_> {
    public PartitionChangeMove<Solution_> getStep();
    public String getStepString();
}
```

### Event Types

#### PartitionChangedEventType

```java
public enum PartitionChangedEventType {
    MOVE,           // New best solution from partition
    FINISHED,        // Partition completed solving
    EXCEPTION_THROWN  // Partition threw an exception
}
```

## Examples

See the following examples for complete implementations:

- **Cloud Balancing**: [`CloudBalancePartitioner`](../../optaplanner-examples/src/main/java/org/optaplanner/examples/cloudbalancing/optional/partitioner/CloudBalancePartitioner.java)
- **Test Partitioner**: [`TestdataSolutionPartitioner`](../../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/partitionedsearch/TestdataSolutionPartitioner.java)
- **Integration Tests**: [`DefaultPartitionedSearchPhaseTest`](../../core/optaplanner-core-impl/src/test/java/org/optaplanner/core/impl/partitionedsearch/DefaultPartitionedSearchPhaseTest.java)

## Summary

Partitioned Search is a powerful technique for solving large-scale planning problems in parallel. Key points to remember:

- Implement a custom [`SolutionPartitioner`](../../core/optaplanner-core-impl/src/main/java/org/optaplanner/core/impl/partitionedsearch/partitioner/SolutionPartitioner.java) to split your problem
- Configure partition count and thread limits appropriately
- Use balanced partitioning strategies for best performance
- Consider solution quality trade-offs
- Monitor memory usage and CPU consumption
- Test partitioners thoroughly before production use

For further information, refer to the OptaPlanner source code and examples.
