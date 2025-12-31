# Greycos Solver - Preview Features Research Report

**Date:** January 2025
**Project:** Greycos Solver (fork of TimeFold Solver Community Edition)
**License:** Apache 2.0

---

## Executive Summary

Greycos Solver offers preview features that are under development and not yet considered stable. These features are developed to the same standard as the rest of the solver, but their APIs may change without prior notice. Users are encouraged to try these features and provide feedback.

**Note:** DIVERSIFIED_LATE_ACCEPTANCE has been promoted from preview feature status to a standard feature and is now enabled by default without requiring preview feature configuration.

---

## Preview Features Overview

### 1. PLANNING_SOLUTION_DIFF

**Status:** Preview Feature  
**Purpose:** Compare two planning solutions and identify differences  
**Implementation:** [`PlanningSolutionDiff.java`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/solution/diff/PlanningSolutionDiff.java)

#### Description
Provides detailed comparison between two planning solutions, identifying which planning entities have changed and which planning variables have different values.

#### API Components
- **[`PlanningSolutionDiff`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/solution/diff/PlanningSolutionDiff.java)**: Main interface for solution comparison
- **[`PlanningEntityDiff`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/solution/diff/PlanningEntityDiff.java)**: Differences at entity level
- **[`PlanningVariableDiff`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/solution/diff/PlanningVariableDiff.java)**: Differences at variable level

#### Usage Example
```java
// Enable preview feature
solverConfig.withPreviewFeature(PreviewFeature.PLANNING_SOLUTION_DIFF);

// Compare solutions
PlanningSolutionDiff diff = solutionManager.diff(oldSolution, newSolution);

// Analyze differences
List<PlanningEntityDiff> entityDiffs = diff.getEntityDiffs();
```

#### Use Cases
- Solution change tracking
- Debugging solver behavior
- Comparing results from different solver runs
- Incremental solution analysis

#### Implementation Notes
- Requires preview feature to be enabled
- Works with both basic and list variables
- Provides detailed change information including:
  - Which entities changed
  - Which variables changed
  - Old and new values

---

### 2. NEIGHBORHOODS (Active Research Project)

**Status:** Active Research Project (exception to preview feature rules)  
**Purpose:** Simplify creation of custom moves, eventually replacing move selectors  
**Warning:** Under heavy development, undocumented, incomplete API

#### Important Disclaimer
Unlike other preview features, Neighborhoods is an **active research project** that is not yet ready for production use or feedback. The API and feature set are incomplete and can change or be removed at any time.

#### API Structure

##### Core Interfaces
Located in [`ai.greycos.solver.core.preview.api`](core/src/main/java/ai/greycos/solver/core/preview/api/package-info.java):

1. **Move API** ([`ai.greycos.solver.core.preview.api.move`](core/src/main/java/ai/greycos/solver/core/preview/api/move/package-info.java))
   - [`Move<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/move/Move.java): Represents a change to planning variables
   - [`MutableSolutionView<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/move/MutableSolutionView.java): Exposes mutative operations on variables
   - [`SolutionView<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/move/SolutionView.java): Read-only access to solution state
   - [`Rebaser`](core/src/main/java/ai/greycos/solver/core/preview/api/move/Rebaser.java): Handles move translation across solver threads

2. **Neighborhood API** ([`ai.greycos.solver.core.preview.api.neighborhood`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/Neighborhood.java))
   - [`Neighborhood`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/Neighborhood.java): Container for move definitions
   - [`NeighborhoodBuilder<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/NeighborhoodBuilder.java): Builder pattern for neighborhoods
   - [`NeighborhoodProvider<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/NeighborhoodProvider.java): Interface for custom neighborhood definitions

3. **Move Stream API** ([`ai.greycos.solver.core.preview.api.neighborhood.stream`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/stream/MoveStreamFactory.java))
   - [`MoveStream<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/stream/MoveStream.java): Iterable source of moves
   - [`MoveStreamFactory<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/stream/MoveStreamFactory.java): Factory for creating move streams
   - [`EnumeratingStream`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/stream/enumerating/EnumeratingStream.java): Full enumeration of moves
   - [`SamplingStream`](core/src/main/java/ai/greycos/solver/core/preview/api/neighborhood/stream/sampling/SamplingStream.java): Random sampling of moves

4. **Meta-Model API** ([`ai.greycos.solver.core.preview.api.domain.metamodel`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/metamodel/PlanningSolutionMetaModel.java))
   - [`PlanningSolutionMetaModel<Solution_>`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/metamodel/PlanningSolutionMetaModel.java): Solution-level metadata
   - [`PlanningEntityMetaModel<Solution_, Entity_>`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/metamodel/PlanningEntityMetaModel.java): Entity-level metadata
   - [`PlanningVariableMetaModel<Solution_, Entity_, Value_>`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/metamodel/PlanningVariableMetaModel.java): Variable-level metadata
   - [`PlanningListVariableMetaModel`](core/src/main/java/ai/greycos/solver/core/preview/api/domain/metamodel/PlanningListVariableMetaModel.java): List variable metadata

#### Built-in Move Implementations
Located in [`ai.greycos.solver.core.preview.api.move.builtin`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/):

- [`ChangeMoveDefinition`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/ChangeMoveDefinition.java): Change a single variable value
- [`SwapMoveDefinition`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/SwapMoveDefinition.java): Swap two variable values
- [`ListChangeMoveDefinition`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/ListChangeMoveDefinition.java): Change list variable
- [`ListSwapMoveDefinition`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/ListSwapMoveDefinition.java): Swap list elements
- [`ListAssignMove`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/ListAssignMove.java): Assign to list
- [`ListUnassignMove`](core/src/main/java/ai/greycos/solver/core/preview/api/move/builtin/ListUnassignMove.java): Unassign from list

#### Example Usage (When Ready)
```java
// Enable preview feature (currently not recommended for production)
.withPreviewFeature(PreviewFeature.NEIGHBORHOODS)

// Define custom neighborhood
public class MyNeighborhoodProvider implements NeighborhoodProvider<MySolution> {
    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<MySolution> builder) {
        var variableMeta = builder.getSolutionMetaModel()
            .entity(MyEntity.class)
            .<MyValue>basicVariable();
        
        return builder.add(new ChangeMoveDefinition<>(variableMeta))
                   .add(new SwapMoveDefinition<>(variableMeta))
                   .build();
    }
}
```

#### Current State
- **API Status:** Incomplete and evolving
- **Documentation:** Minimal
- **Testing:** Internal tests exist ([`DefaultSolverTest.java:TestingNeighborhoodProvider`](core/src/test/java/ai/greycos/solver/core/impl/solver/DefaultSolverTest.java:2320))
- **Production Readiness:** NOT READY

#### Test Coverage
Tests demonstrate basic functionality:
- Basic variable changes ([`DefaultSolverTest.java:solveWithNeighborhoods()`](core/src/test/java/ai/greycos/solver/core/impl/solver/DefaultSolverTest.java:185))
- List variable changes ([`DefaultSolverTest.java:solveWithNeighborhoodsListVar()`](core/src/test/java/ai/greycos/solver/core/impl/solver/DefaultSolverTest.java:206))
- Integration with move selectors ([`DefaultSolverTest.java:solveWithNeighborhoodsAndMoveSelectors()`](core/src/test/java/ai/greycos/solver/core/impl/solver/DefaultSolverTest.java:253))

#### Future Goals
- Simplify custom move creation
- Eventually replace move selectors
- Provide more intuitive API
- Complete feature set

---

## Comparison with Traditional Approaches

### Move Selectors (Current/Traditional)
- Declarative configuration via XML/Java
- Pre-built move types
- Less flexible for custom moves
- Complex for advanced use cases

### Neighborhoods (Future/Preview)
- Programmatic move definition
- Type-safe API
- More control over move generation
- Easier to create custom moves
- Still in development

---

## Configuration and Usage

### Enabling Preview Features

```java
SolverConfig config = new SolverConfig()
    .withPreviewFeature(PreviewFeature.PLANNING_SOLUTION_DIFF);
    // Note: NEIGHBORHOODS not yet ready for use
```

### DIVERSIFIED_LATE_ACCEPTANCE (Standard Feature)

DIVERSIFIED_LATE_ACCEPTANCE is now a standard feature and can be used without enabling any preview features. It can be configured using the standard acceptor configuration:

```java
// Using LocalSearchType
LocalSearchPhaseConfig localSearchPhaseConfig = new LocalSearchPhaseConfig()
    .withLocalSearchType(LocalSearchType.DIVERSIFIED_LATE_ACCEPTANCE);

// Or using AcceptorType
LocalSearchAcceptorConfig acceptorConfig = new LocalSearchAcceptorConfig()
    .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE));
```

**Note:** DIVERSIFIED_LATE_ACCEPTANCE was previously a preview feature but has been promoted to standard feature status based on successful testing and production readiness.

### Validation
The solver validates preview feature usage:
```java
// Example from DefaultSolutionManager.java
public PlanningSolutionDiff<Solution_> diff(Solution_ oldSolution, Solution_ newSolution) {
    solverFactory.ensurePreviewFeature(PreviewFeature.PLANNING_SOLUTION_DIFF);
    return solverFactory.getSolutionDescriptor().diff(oldSolution, newSolution);
}
```

---

## Feedback Channels

Users are encouraged to provide feedback on preview features:
- **GitHub Discussions:** https://github.com/CameleoGrey/greycos-solver/discussions
- **Discord:** https://discord.com/channels/1413420192213631086/1414521616955605003

---

## Stability and Compatibility

### API Stability Guarantees
- **No backward compatibility guarantees**
- Classes, methods, and fields may change without notice
- Features may be added or removed without warning
- Not part of public API

### Exception: NEIGHBORHOODS
- Not finished or ready for feedback
- Active research project
- Entirely undocumented
- Can change or be removed at any time

---

## Performance Considerations

### DIVERSIFIED_LATE_ACCEPTANCE (Standard Feature)
- **Time Complexity:** O(1) per move evaluation
- **Space Complexity:** O(k) where k = lateAcceptanceSize
- **Overhead:** Minimal, mainly array operations
- **Recommended for:** Large search spaces with many local optima
- **Status:** Production-ready, enabled by default

### PLANNING_SOLUTION_DIFF
- **Time Complexity:** O(n + m) where n = entities, m = variables
- **Space Complexity:** O(n + m) for storing diff information
- **Overhead:** One-time cost per comparison
- **Recommended for:** Debugging, solution tracking, not hot paths

### NEIGHBORHOODS
- **Performance:** Not yet benchmarked
- **Overhead:** Unknown (still in development)
- **Recommendation:** Do not use in production

---

## Migration Path

### From Move Selectors to Neighborhoods (Future)
When Neighborhoods stabilizes:

1. **Current Approach:**
```java
.withMoveSelectorConfig(new ChangeMoveSelectorConfig())
```

2. **Future Approach:**
```java
.withMoveProviderClass(MyNeighborhoodProvider.class)
```

3. **Benefits:**
   - Type-safe move definitions
   - Easier custom moves
   - Better testability
   - More intuitive API

---

## Test Coverage

### DIVERSIFIED_LATE_ACCEPTANCE (Standard Feature)
- Implementation: [`DiversifiedLateAcceptanceAcceptor.java`](core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/lateacceptance/DiversifiedLateAcceptanceAcceptor.java)
- Factory integration: [`AcceptorFactory.java:267`](core/src/main/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/AcceptorFactory.java:267)
- Tests: [`AcceptorFactoryTest.java:diversifiedLateAcceptanceAcceptor()`](core/src/test/java/ai/greycos/solver/core/impl/localsearch/decider/acceptor/AcceptorFactoryTest.java:102)

### PLANNING_SOLUTION_DIFF
- Usage: [`DefaultSolutionManager.java:178`](core/src/main/java/ai/greycos/solver/core/impl/solver/DefaultSolutionManager.java:178)
- Tests: [`PlanningSolutionDiffTest.java`](core/src/test/java/ai/greycos/solver/core/impl/domain/solution/descriptor/PlanningSolutionDiffTest.java)

### NEIGHBORHOODS
- Integration tests: [`DefaultSolverTest.java:185-271`](core/src/test/java/ai/greycos/solver/core/impl/solver/DefaultSolverTest.java:185)
- Move definition tests: Multiple test files in [`core/src/test/java/ai/greycos/solver/core/preview/api/move/builtin/`](core/src/test/java/ai/greycos/solver/core/preview/api/move/builtin/)
- Local search integration: [`NeighborhoodsBasedLocalSearchTest.java`](core/src/test/java/ai/greycos/solver/core/impl/neighborhood/NeighborhoodsBasedLocalSearchTest.java)

---

## Recommendations

### For Production Use
1. **DIVERSIFIED_LATE_ACCEPTANCE (Standard Feature):** Can be used if:
   - Problem has many local optima
   - Traditional acceptors are insufficient
   - No preview feature configuration required

2. **PLANNING_SOLUTION_DIFF:** Can be used for:
   - Debugging and development
   - Solution comparison
   - Change tracking
   - Not recommended for hot paths

3. **NEIGHBORHOODS:** **DO NOT USE IN PRODUCTION**
   - Still in research phase
   - Incomplete API
   - May change significantly

### For Experimentation
- All features are available for experimentation
- Provide feedback through GitHub or Discord
- Help shape the future of the API

### For Migration Planning
- Monitor Neighborhoods development
- Prepare for eventual move selector replacement
- Start with simple use cases when stable

---

## Conclusion

Greycos Solver offers both standard features and preview features that represent the future direction of the project:

**Standard Features:**
1. **DIVERSIFIED_LATE_ACCEPTANCE** - Production-ready advanced metaheuristic (promoted from preview)

**Preview Features:**
2. **PLANNING_SOLUTION_DIFF** - Production-ready utility feature
3. **NEIGHBORHOODS** - Research project, not ready for use

The project maintains a clear distinction between standard features (stable, production-ready), preview features (ready for feedback), and research projects (not ready for feedback). Users should evaluate their needs and risk tolerance before adopting preview features.

---

## References

- **Main Source Code:** [`core/src/main/java/ai/greycos/solver/core/`](core/src/main/java/ai/greycos/solver/core/)
- **Preview Feature Enum:** [`PreviewFeature.java`](core/src/main/java/ai/greycos/solver/core/config/solver/PreviewFeature.java)
- **Tests:** [`core/src/test/java/`](core/src/test/java/)
- **Project README:** [`README.adoc`](README.adoc)
- **Contributing:** [`CONTRIBUTING.adoc`](CONTRIBUTING.adoc)

---

**Report Generated:** January 2025  
**Greycos Solver Version:** 999-SNAPSHOT  
**Java Version Required:** 17+
**Maven Version Required:** 3.9+
