# Island Model Logging Solution Plan

## Architecture Update (2024)

**Important:** [`IslandModelPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/islandmodel/IslandModelPhaseConfig.java:36) now extends [`LocalSearchPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/localsearch/LocalSearchPhaseConfig.java:48). This means:

- Each island runs the **same local search configuration**
- Configuration is simplified: no need for nested `phaseConfigList`
- Local search options (move selector, acceptor, forager, etc.) are configured directly on `IslandModelPhaseConfig`

## Problem Statement

The island model uses `LocalSearchPhase` within island agents, but its logging doesn't distinguish between island contexts and direct solver contexts. This results in:

### Current Logging Behavior

| Component | Current Level | Desired Level | Issue |
|-----------|---------------|---------------|-------|
| `GlobalBestUpdater` (global best updates) | DEBUG | DEBUG | ✓ Correct |
| `IslandAgent` (agent lifecycle) | INFO | INFO | ✓ Correct |
| `LocalSearchPhase` (step details) | DEBUG | INFO (suppress DEBUG) | ✗ Too verbose |
| `LocalSearchPhase` (phase ended) | INFO | INFO | ✓ Correct |

### Root Cause

`LocalSearchPhase` is used both in single-threaded solvers and within island agents. Its logging at [`DefaultLocalSearchPhase.java:171-198`](core/src/main/java/ai/greycos/solver/core/impl/localsearch/DefaultLocalSearchPhase.java:171) doesn't distinguish between these contexts, so island agents get the same verbose DEBUG-level step logging as direct solvers.

## Solution: Island-Aware Phase Wrapper

Create an `IslandAwarePhaseWrapper` that intercepts and controls logging from phases running within island agents, while preserving island model-specific logging at appropriate levels.

### Architecture

```
IslandAgent (agentId=2, islandCount=4)
    └── IslandAwarePhaseWrapper
            ├── MDC context: agentId=2, count=4, context="Agent 2/4"
            └── LocalSearchPhase (wrapped, logging controlled)
                    ├── DEBUG logs: suppressed
                    ├── INFO logs: preserved
                    └── All operations: delegated
```

### Key Components

#### 1. IslandAwarePhaseWrapper<Solution_>

**Location:** `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAwarePhaseWrapper.java`

**Responsibilities:**
- Implements `Phase<Solution_>` interface to wrap any phase
- Delegates all phase operations to wrapped phase
- Controls logging output from wrapped phase
- Adds island/agent context to logs via MDC
- Provides selective logging control (suppress DEBUG, keep INFO)

**Constructor:**
```java
public IslandAwarePhaseWrapper(
    Phase<Solution_> wrappedPhase,
    int agentId,
    int islandCount)
```

**Key Methods:**
- All `Phase` interface methods delegate to `wrappedPhase`
- Each method wraps execution with MDC context management:
  ```java
  MDC.put("island.agentId", String.valueOf(agentId));
  MDC.put("island.count", String.valueOf(islandCount));
  MDC.put("island.context", "Agent " + (agentId + 1) + "/" + islandCount);
  try {
      return wrappedPhase.method(...);
  } finally {
      MDC.clear();
  }
  ```

**Logging Strategy:**
- **Global best updates:** Keep at DEBUG (as requested)
- **Island agent lifecycle:** Keep at INFO (as requested)
- **LocalSearch step details:** Suppress DEBUG, keep INFO
- **Phase ended:** Keep at INFO

#### 2. Modified DefaultIslandModelPhase.buildPhasesForAgent()

**Location:** `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/DefaultIslandModelPhase.java`

**Changes:**
```java
private List<Phase<Solution_>> buildPhasesForAgent() {
    // IslandModelPhaseConfig now extends LocalSearchPhaseConfig, so each island runs
    // same local search configuration with independent random seeds and solution states
    var childConfigPolicy = configPolicy.createChildThreadConfigPolicy(ChildThreadType.MOVE_THREAD);

    // Build a single LocalSearchPhase from island model's local search config
    // and wrap it with IslandAwarePhaseWrapper
    List<Phase<Solution_>> phases = PhaseFactory.buildPhases(
        islandModelConfig, childConfigPolicy, bestSolutionRecaller, solverTermination);
    
    // Wrap each phase with IslandAwarePhaseWrapper
    List<Phase<Solution_>> wrappedPhases = new ArrayList<>();
    for (Phase<Solution_> phase : phases) {
        wrappedPhases.add(new IslandAwarePhaseWrapper<>(
            phase, agentId, islandCount));
    }
    
    return wrappedPhases;
}
```

#### 3. Enhanced GlobalBestUpdater

**Location:** `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/GlobalBestUpdater.java`

**Changes:**
- Add island/agent context to log messages
- Keep logging at DEBUG level (as requested)
- Example: `"Agent 2/4 updated global best (score: -100hard, time: 5ms, step: 123)"`

**Current log:**
```java
LOGGER.debug(
    "Agent {} updated global best (score: {}, time spent: {} ms, step index: {})",
    agentId, bestScore.raw(), timeSpentMs, stepScope.getStepIndex());
```

**Enhanced log:**
```java
LOGGER.debug(
    "Agent {}/{} updated global best (score: {}, time spent: {} ms, step index: {})",
    agentId + 1, islandCount, bestScore.raw(), timeSpentMs, stepScope.getStepIndex());
```

#### 4. Enhanced IslandAgent

**Location:** `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAgent.java`

**Changes:**
- Add island/agent context to INFO-level logs
- Keep logging at INFO level (as requested)
- Example: `"Agent 2/4 started with 1 phases"`

**Current logs:**
```java
LOGGER.info("Agent {} started with {} phases", agentId, phases.size());
LOGGER.info("Agent {} terminated", agentId);
```

**Enhanced logs:**
```java
LOGGER.info("Agent {}/{} started with {} phases", agentId + 1, islandCount, phases.size());
LOGGER.info("Agent {}/{} terminated", agentId + 1, islandCount);
```

## MDC Context Strategy

Add the following MDC keys to all logs from island agents:

| MDC Key | Value | Example |
|---------|-------|---------|
| `island.agentId` | Agent ID (0-indexed) | `"2"` |
| `island.count` | Total island count | `"4"` |
| `island.context` | Human-readable context | `"Agent 3/4"` |

### Benefits of MDC

1. **Rich context in logs:** All logs include island/agent information
2. **Flexible filtering:** Log frameworks can filter based on MDC values
3. **Easy correlation:** Logs can be correlated across agents
4. **Standard approach:** MDC is a standard SLF4J feature

### Log Pattern Example

With MDC context, log patterns can include island information:
```xml
<pattern>[%d{HH:mm:ss.SSS}] [%thread] [%X{island.context}] %-5level %logger{36} - %msg%n</pattern>
```

Output:
```
[14:23:45.678] [pool-1-thread-2] [Agent 3/4] DEBUG i.g.s.c.i.i.GlobalBestUpdater - Agent 3/4 updated global best (score: -100hard, time: 5ms, step: 123)
```

## Logging Control Strategy

### Option A: MDC-based Filtering (Recommended)

Let the logging framework filter based on MDC values:

**Logback Example:**
```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
        <evaluator class="ch.qos.logback.classic.boolex.JaninoEventEvaluator">
            <expression>
                (mdc.get("island.context") != null) && 
                (level == DEBUG) && 
                (logger.contains("LocalSearch"))
            </expression>
        </evaluator>
        <onMatch>DENY</onMatch>
    </filter>
    <encoder>
        <pattern>[%d{HH:mm:ss.SSS}] [%thread] [%X{island.context}] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

**Log4j2 Example:**
```xml
<PatternLayout pattern="[%d{HH:mm:ss.SSS}] [%t] [%X{island.context}] %-5level %logger{36} - %msg%n"/>

<Logger name="ai.greycos.solver.core.impl.localsearch" level="INFO">
    <Filters>
        <ThreadContextMapFilter onMatch="ACCEPT" onMismatch="NEUTRAL">
            <KeyValuePair key="island.context" value="null"/>
        </ThreadContextMapFilter>
    </Filters>
</Logger>
```

### Option B: Programmatic Filtering

Override logging in wrapper to suppress DEBUG:

```java
public void stepEnded(AbstractStepScope<Solution_> stepScope) {
    // Temporarily suppress DEBUG logs from wrapped phase
    var originalLevel = LoggerContext.getContext(false)
        .getLogger(wrappedPhase.getClass().getName())
        .getLevel();
    
    try {
        // Suppress DEBUG
        LoggerContext.getContext(false)
            .getLogger(wrappedPhase.getClass().getName())
            .setLevel(Level.INFO);
        
        wrappedPhase.stepEnded(stepScope);
    } finally {
        // Restore original level
        LoggerContext.getContext(false)
            .getLogger(wrappedPhase.getClass().getName())
            .setLevel(originalLevel);
    }
}
```

**Decision:** Use Option A for flexibility, with Option B as fallback if needed.

## File Changes Summary

### 1. Architecture Changes

#### IslandModelPhaseConfig.java

**Changes:**
- Now extends [`LocalSearchPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/localsearch/LocalSearchPhaseConfig.java:48) instead of [`PhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/phase/PhaseConfig.java:30)
- Removed `phaseConfigList` field and related methods (deprecated)
- Inherits all local search configuration options (move selector, acceptor, forager, etc.)
- Each island runs the same local search configuration

#### DefaultIslandModelPhase.java

**Changes:**
- Replaced `wrappedPhaseConfigList` with `islandModelConfig`
- Updated `buildPhasesForAgent()` to build phases from inherited `LocalSearchPhaseConfig`
- Simplified phase creation: builds single `LocalSearchPhase` from island model config

#### DefaultIslandModelPhaseFactory.java

**Changes:**
- Removed `buildWrappedPhases()` method (no longer needed)
- Updated `buildPhase()` to pass `islandModelConfig` directly to builder
- Simplified factory logic

### 2. New File

**File:** `core/src/main/java/ai/greycos/solver/core/impl/islandmodel/IslandAwarePhaseWrapper.java`

**Purpose:** Wrapper class that adds island context and controls logging

**Key Features:**
- Implements `Phase<Solution_>` interface
- Delegates all operations to wrapped phase
- Manages MDC context lifecycle
- Optionally filters DEBUG logs

### 3. Modified Files for Logging

#### DefaultIslandModelPhase.java (Logging)

**Changes:**
- Update `buildPhasesForAgent()` to wrap phases with `IslandAwarePhaseWrapper`
- Pass `agentId` and `islandCount` to wrapper

#### IslandAgent.java

**Changes:**
- Enhance INFO-level log messages with island context
- Format: `"Agent {}/{}/{} message"`, agentId + 1, islandCount, message

#### GlobalBestUpdater.java

**Changes:**
- Add `islandCount` field to constructor
- Enhance DEBUG-level log messages with island context
- Format: `"Agent {}/{} updated global best..."`, agentId + 1, islandCount

## Benefits

1. **Simplified configuration:** [`IslandModelPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/islandmodel/IslandModelPhaseConfig.java:36) extends [`LocalSearchPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/localsearch/LocalSearchPhaseConfig.java:48), eliminating need for nested `phaseConfigList`
2. **Separation of concerns:** Wrapper handles logging control, phase handles solving
3. **No changes to existing phases:** `LocalSearchPhase` remains unchanged
4. **Rich context:** All logs include island/agent information via MDC
5. **Flexible configuration:** Users can adjust logging via framework config
6. **Clear logging levels:** DEBUG for global updates, INFO for agent events
7. **Minimal performance impact:** MDC is lightweight, delegation is cheap
8. **Backward compatible:** Non-island contexts unaffected

## Potential Issues & Mitigations

| Issue | Impact | Mitigation |
|-------|--------|------------|
| MDC context leak | Logs from other threads get wrong context | Use try-finally to clear MDC context after delegation |
| Performance overhead of wrapper | Slight overhead per method call | Wrapper is thin delegation, minimal overhead (~5-10ns) |
| Nested phases (unlikely but possible) | Double-wrapping causes issues | Wrapper checks if already wrapped, avoids double-wrapping |
| Logging framework compatibility | Some frameworks may not support MDC | MDC is standard SLF4J feature, works with all implementations |
| Phase lifecycle events | Wrapper may miss events | Wrapper implements Phase interface, receives all events |

## Testing Strategy

### Unit Tests

1. **Wrapper delegation tests:** Verify wrapper delegates correctly to wrapped phase
2. **MDC context tests:** Verify context is set/cleared correctly
3. **Logging tests:** Verify DEBUG logs are suppressed in island context

### Integration Tests

1. **Island model tests:** Run island model with multiple agents
2. **Log verification:** Check DEBUG vs INFO levels in different contexts
3. **MDC context verification:** Verify all logs include island context

### Performance Tests

1. **Wrapper overhead:** Measure overhead of wrapper (expected < 1%)
2. **MDC overhead:** Measure overhead of MDC operations (expected < 0.1%)
3. **End-to-end:** Compare island model performance before/after

### Test Cases

```java
@Test
public void testWrapperDelegatesPhaseOperations() {
    Phase<CloudBalance> mockPhase = mock(Phase.class);
    IslandAwarePhaseWrapper<CloudBalance> wrapper = 
        new IslandAwarePhaseWrapper<>(mockPhase, 0, 4);
    
    wrapper.solve(mockSolverScope);
    verify(mockPhase).solve(mockSolverScope);
}

@Test
public void testMdcContextIsSetAndCleared() {
    Phase<CloudBalance> mockPhase = mock(Phase.class);
    IslandAwarePhaseWrapper<CloudBalance> wrapper = 
        new IslandAwarePhaseWrapper<>(mockPhase, 0, 4);
    
    wrapper.solve(mockSolverScope);
    // Verify MDC was set during execution
    // Verify MDC is cleared after execution
}

@Test
public void testDebugLogsSuppressedInIslandContext() {
    // Test that DEBUG logs from LocalSearchPhase are suppressed
    // when running within island model
}
```

## Implementation Steps

1. ✅ Analyze current logging problem in island model
2. ✅ Create implementation plan for Island-Specific Phase Wrapper solution
3. ✅ Design the wrapper architecture
4. ✅ Refactor IslandModelPhaseConfig to extend LocalSearchPhaseConfig
5. ✅ Simplify DefaultIslandModelPhase to use inherited config
6. ✅ Update example configuration to use simplified API
7. ✅ Update logging solution plan for new architecture
8. ⏳ Implement IslandAwarePhaseWrapper class
9. ⏳ Modify DefaultIslandModelPhase to use wrappers
10. ⏳ Update GlobalBestUpdater logging level
11. ⏳ Add island/agent context to logs
12. ⏳ Test the implementation

## Configuration Examples

### Simplified Island Model Configuration (New API)

With [`IslandModelPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/islandmodel/IslandModelPhaseConfig.java:36) extending [`LocalSearchPhaseConfig`](core/src/main/java/ai/greycos/solver/core/config/localsearch/LocalSearchPhaseConfig.java:48), configuration is now simpler:

```java
// Before (old API with phaseConfigList)
new IslandModelPhaseConfig()
    .withIslandCount(4)
    .withMigrationFrequency(100)
    .withReceiveGlobalUpdateFrequency(50)
    .withPhaseConfigList(List.of(
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuRatio(0.2))
            .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(10000))));

// After (new API - direct configuration)
new IslandModelPhaseConfig()
    .withIslandCount(4)
    .withMigrationFrequency(100)
    .withReceiveGlobalUpdateFrequency(50)
    .withAcceptorConfig(new LocalSearchAcceptorConfig().withEntityTabuRatio(0.2))
    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(10000));
```

### Logback Configuration

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>[%d{HH:mm:ss.SSS}] [%thread] [%X{island.context}] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- Suppress DEBUG from LocalSearchPhase in island context -->
    <logger name="ai.greycos.solver.core.impl.localsearch" level="INFO"/>
    
    <!-- Keep DEBUG for GlobalBestUpdater -->
    <logger name="ai.greycos.solver.core.impl.islandmodel.GlobalBestUpdater" level="DEBUG"/>
    
    <!-- Keep INFO for IslandAgent -->
    <logger name="ai.greycos.solver.core.impl.islandmodel.IslandAgent" level="INFO"/>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

### Log4j2 Configuration

```xml
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="[%d{HH:mm:ss.SSS}] [%t] [%X{island.context}] %-5level %logger{36} - %msg%n"/>
        </Console>
    </Appenders>
    
    <Loggers>
        <!-- Suppress DEBUG from LocalSearchPhase in island context -->
        <Logger name="ai.greycos.solver.core.impl.localsearch" level="INFO"/>
        
        <!-- Keep DEBUG for GlobalBestUpdater -->
        <Logger name="ai.greycos.solver.core.impl.islandmodel.GlobalBestUpdater" level="DEBUG"/>
        
        <!-- Keep INFO for IslandAgent -->
        <Logger name="ai.greycos.solver.core.impl.islandmodel.IslandAgent" level="INFO"/>
        
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

## Expected Logging Output

### Before Implementation

```
14:23:45.678 INFO  IslandAgent - Agent 2 started with 1 phases
14:23:45.679 DEBUG IslandAgent - Agent 2 running phase: LocalSearchPhase
14:23:45.680 DEBUG LocalSearchPhase - LS step (0), time spent (0), score (-1000hard),    best score (-1000hard), accepted/selected move count (100/1000), picked move (Process[1] -> Computer[2]).
14:23:45.681 DEBUG LocalSearchPhase - LS step (1), time spent (1), score (-1000hard),    best score (-1000hard), accepted/selected move count (50/1000), picked move (Process[2] -> Computer[3]).
14:23:45.690 DEBUG GlobalBestUpdater - Agent 2 updated global best (score: -1000hard, time spent: 10 ms, step index: 10)
14:23:45.700 INFO  LocalSearchPhase - Local Search phase (0) ended: time spent (20), best score (-950hard), move evaluation speed (50000/sec), step total (10).
14:23:45.701 INFO  IslandAgent - Agent 2 terminated
```

### After Implementation

```
14:23:45.678 INFO  IslandAgent - Agent 3/4 started with 1 phases
14:23:45.679 DEBUG IslandAgent - Agent 3/4 running phase: LocalSearchPhase
14:23:45.690 DEBUG GlobalBestUpdater - Agent 3/4 updated global best (score: -1000hard, time spent: 10 ms, step index: 10)
14:23:45.700 INFO  LocalSearchPhase - Local Search phase (0) ended: time spent (20), best score (-950hard), move evaluation speed (50000/sec), step total (10).
14:23:45.701 INFO  IslandAgent - Agent 3/4 terminated
```

**Changes:**
- Agent logs include context: `"Agent 3/4"` instead of `"Agent 2"`
- LocalSearch step DEBUG logs suppressed
- Global best update logs include context
- Phase ended INFO logs preserved

## Conclusion

The Island-Aware Phase Wrapper solution provides a clean, maintainable approach to controlling logging in the island model context. By wrapping phases and using MDC for context, we achieve:

- **Clear separation of concerns** between logging control and phase logic
- **Rich context** in all island-related logs
- **Flexible configuration** via logging framework
- **Minimal code changes** to existing components
- **Backward compatibility** with non-island contexts

This solution addresses the original problem while maintaining code quality and providing flexibility for future enhancements.
