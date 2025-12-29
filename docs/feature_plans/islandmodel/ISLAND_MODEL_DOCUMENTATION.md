# GreyJack Solver: Multithreading Island Model Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Core Components](#core-components)
4. [Parallel Execution Model](#parallel-execution-model)
5. [Communication Mechanism](#communication-mechanism)
6. [Migration Strategy](#migration-strategy)
7. [Score Calculation Modes](#score-calculation-modes)
8. [Termination Coordination](#termination-coordination)
9. [Observer Pattern](#observer-pattern)
10. [Logging and Monitoring](#logging-and-monitoring)
11. [Initial Solution Support](#initial-solution-support)
12. [Metaheuristic-Specific Behaviors](#metaheuristic-specific-behaviors)
13. [Java Implementation Guide](#java-implementation-guide)
14. [Key Design Decisions](#key-design-decisions)
15. [Performance Considerations](#performance-considerations)

---

## Overview

The GreyJack Solver implements a **multithreaded island model** where multiple independent optimization agents (islands) run in parallel and periodically exchange their best solutions. This approach provides:

- **Nearly linear horizontal scaling** - Adding more agents typically yields proportional performance improvements
- **Enhanced solution quality** - Migration prevents premature convergence to local optima
- **Fault tolerance** - Agents can terminate independently while others continue
- **Flexible algorithm mixing** - Different metaheuristics can run on different islands

### Key Concept: Island Model

In the island model, each agent maintains its own population and evolves it independently. Periodically, agents exchange their best individuals (migration), allowing genetic diversity to spread between islands. This mimics natural evolution across geographically separated populations.

---

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Solver                                │
│  - Orchestrates parallel execution                           │
│  - Manages shared state (global best)                        │
│  - Coordinates communication channels                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Parallel Execution (Rayon)                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Agent 0  │  │ Agent 1  │  │ Agent 2  │  │ Agent N  │    │
│  │ (Island) │  │ (Island) │  │ (Island) │  │ (Island) │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘    │
│       │             │             │             │           │
│       └─────────────┴─────────────┴─────────────┘           │
│                     Communication Ring                       │
└─────────────────────────────────────────────────────────────┘
```

### Thread Safety Strategy

The implementation uses Rust's ownership system with these synchronization primitives:

1. **Arc<Mutex<T>>** - Shared state with exclusive access
   - `global_top_individual`: Best solution across all agents
   - `global_top_json`: JSON representation of best solution
   - `observers`: Optional observer list for progress monitoring

2. **Crossbeam Channels** - Lock-free message passing
   - Bounded channels (capacity=1) for agent-to-agent updates
   - Asynchronous send/receive operations

3. **Clone-on-Write Pattern** - Minimize contention
   - Status vectors are cloned for migration messages
   - Individuals are cloned when migrating

---

## Core Components

### 1. Solver (`greyjack/src/solver/solver.rs`)

The Solver is the entry point that orchestrates the entire parallel optimization process.

#### Key Responsibilities

```rust
pub struct Solver {}

impl Solver {
    pub fn solve<DomainType, DomainBuilder, CotwinBuilder, 
                  EntityVariants, UtilityObjectVariants, ScoreType>(
        domain_builder: DomainBuilder,
        cotwin_builder: CotwinBuilder,
        agent_builder: AgentBuildersVariants<ScoreType>,
        n_jobs: usize,                          // Number of parallel agents
        score_precision: Option<Vec<u64>>,
        logging_level: SolverLoggingLevels,
        observers: Option<Vec<Box<dyn ObserverTrait + Send>>>,
        initial_solution: Option<InitialSolutionVariants<DomainType>>
    ) -> Value
```

#### Initialization Process

1. **Prepare Agent Configuration** (lines 58-90)
   ```rust
   // Create vectors of builders for each agent
   let agent_ids: Vec<usize> = (0..n_jobs).collect();
   let domain_builders: Vec<DomainBuilder> = vec![domain_builder.clone(); n_jobs];
   let cotwin_builders: Vec<CotwinBuilder> = vec![cotwin_builder.clone(); n_jobs];
   let agent_builders: Vec<AgentBuildersVariants<ScoreType>> = 
       vec![agent_builder.clone(); n_jobs];
   
   // Initialize round-robin status tracking
   let mut round_robin_status_vec: Vec<AgentStatuses> = Vec::new();
   for i in 0..n_jobs {
       round_robin_status_vec.insert(i, AgentStatuses::Alive);
   }
   ```

2. **Create Communication Channels** (lines 85-92)
   ```rust
   let mut agents_updates_senders: Vec<Sender<AgentToAgentUpdate<ScoreType>>> = Vec::new();
   let mut agents_updates_receivers: Vec<Receiver<AgentToAgentUpdate<ScoreType>>> = Vec::new();
   
   for i in 0..n_jobs {
       // Create bounded channel (capacity=1) for each agent
       let (agent_i_updates_sender, agent_i_updates_receiver) = bounded(1);
       agents_updates_senders.push(agent_i_updates_sender);
       agents_updates_receivers.push(agent_i_updates_receiver);
   }
   
   // Rotate receivers to create ring topology
   // Agent i sends to agent i, receives from agent (i-1) mod n_jobs
   agents_updates_receivers.rotate_right(1);
   ```

3. **Initialize Shared State** (lines 68-83)
   ```rust
   let global_top_individual: Individual<ScoreType> = 
       Individual::new(vec![1.0], ScoreType::get_stub_score());
   let global_top_individual = Arc::new(Mutex::new(global_top_individual));
   let global_top_json = Arc::new(Mutex::new(Value::Null));
   
   let observers_arc: Arc<Mutex<Option<Vec<Box<dyn ObserverTrait + Send>>>>>;
   match observers {
       None => observers_arc = Arc::new(Mutex::new(None)),
       Some(observers) => observers_arc = Arc::new(Mutex::new(Some(observers))),
   }
   ```

#### Parallel Execution (lines 94-143)

The solver uses Rayon's parallel iterator to spawn threads:

```rust
domain_builders.into_par_iter()
    .zip(cotwin_builders.into_par_iter())
    .zip(agent_builders.into_par_iter())
    .zip(agent_ids.into_par_iter())
    .zip(agents_round_robin_status_clones.into_par_iter())
    .zip(agents_updates_senders.into_par_iter())
    .zip(agents_updates_receivers.into_par_iter())
    .zip(score_precisions.into_par_iter())
    .zip(logging_levels.into_par_iter())
    .zip(observers_counts.into_par_iter())
    .zip(initial_solutions.into_par_iter())
    .for_each(|((((((((((db_i, cb_i), ab_i), ai_i), rrs_i), us_i), rc_i), sp_i), ll_i), oc_i), is_i)| {
        // Build domain and cotwin
        let domain_i = db_i.build_domain_from_scratch();
        let cotwin_i = cb_i.build_cotwin(domain_i, is_already_initialized);
        
        // Build agent from builder
        let mut agent_i = ab_i.build_agent(cotwin_i);
        
        // Configure agent
        agent_i.agent_id = ai_i;
        agent_i.score_precision = sp_i;
        agent_i.round_robin_status_vec = rrs_i;
        agent_i.alive_agents_count = n_jobs;
        agent_i.updates_to_agent_sender = Some(us_i);
        agent_i.updates_for_agent_receiver = Some(rc_i);
        agent_i.global_top_individual = Arc::clone(&global_top_individual);
        agent_i.global_top_json = Arc::clone(&global_top_json);
        agent_i.logging_level = ll_i;
        agent_i.observers = observers_arc.clone();
        agent_i.observers_count = oc_i;
        
        // Start solving
        agent_i.solve();
    });
```

**Critical Design Point**: The `.for_each()` blocks until all agents complete, ensuring the global state is fully updated before returning.

### 2. Agent (`greyjack/src/agents/base/agent_base.rs`)

Each Agent represents an independent island running its own metaheuristic.

#### Agent Structure

```rust
pub struct Agent<EntityVariants, UtilityObjectVariants, ScoreType> {
    // Migration parameters
    pub migration_rate: f64,              // Fraction of population to migrate
    pub migration_frequency: usize,        // Steps between migrations
    
    // Termination
    pub termination_strategy: TerminationStrategiesVariants<ScoreType>,
    
    // Identity
    pub agent_id: usize,
    pub agent_status: AgentStatuses,
    
    // Population
    pub population_size: usize,
    pub population: Vec<Individual<ScoreType>>,
    pub agent_top_individual: Individual<ScoreType>,
    
    // Shared state references
    pub global_top_individual: Arc<Mutex<Individual<ScoreType>>>,
    pub global_top_json: Arc<Mutex<Value>>,
    pub is_global_top_updated: bool,
    
    // Score calculation
    pub score_requester: OOPScoreRequester<EntityVariants, UtilityObjectVariants, ScoreType>,
    pub score_precision: Option<Vec<u64>>,
    
    // Metaheuristic implementation
    pub metaheuristic_base: MetaheuristicsBasesVariants<ScoreType>,
    
    // Communication
    pub updates_to_agent_sender: Option<Sender<AgentToAgentUpdate<ScoreType>>>,
    pub updates_for_agent_receiver: Option<Receiver<AgentToAgentUpdate<ScoreType>>>,
    
    // Coordination
    pub round_robin_status_vec: Vec<AgentStatuses>,
    pub alive_agents_count: usize,
    pub steps_to_send_updates: usize,
    
    // Logging
    pub logging_level: SolverLoggingLevels,
    pub step_id: u64,
    
    // Observers
    pub observers: Arc<Mutex<Option<Vec<Box<dyn ObserverTrait + Send>>>>>,
    pub observers_count: usize,
}
```

#### Agent Lifecycle

```rust
pub fn solve(&mut self) {
    // 1. Initialize population
    self.init_population();
    self.population.sort();
    self.update_top_individual();
    self.update_termination_strategy();
    self.update_agent_status();
    self.update_alive_agents_count();
    
    // 2. Main optimization loop
    loop {
        // Execute one step of metaheuristic
        match self.agent_status {
            AgentStatuses::Alive => {
                match &self.score_requester.cotwin.score_calculator {
                    ScoreCalculatorVariants::PSC(_) => self.step_plain(),
                    ScoreCalculatorVariants::ISC(_) => self.step_incremental(),
                    _ => panic!("Score calculator not configured")
                }
            },
            AgentStatuses::Dead => (),
        }
        
        self.step_id += 1;
        
        // Sort and update best
        if self.population_size > 1 {
            self.population.sort();
        }
        self.update_top_individual();
        self.update_termination_strategy();
        self.update_agent_status();
        self.update_alive_agents_count();
        
        // Check if all agents are done
        if self.alive_agents_count == 0 {
            break;
        }
        
        // Migration logic
        self.steps_to_send_updates -= 1;
        if self.steps_to_send_updates <= 0 {
            // Alternate send/receive order to prevent deadlock
            if self.agent_id % 2 == 0 {
                self.send_updates()?;
                self.receive_updates()?;
            } else {
                self.receive_updates()?;
                self.send_updates()?;
            }
            self.steps_to_send_updates = self.migration_frequency;
        }
        
        // Update global best
        self.update_global_top();
    }
}
```

### 3. Individual (`greyjack/src/agents/base/individual.rs`)

Represents a candidate solution with its score.

```rust
#[derive(Debug, Clone)]
pub struct Individual<ScoreType> {
    pub variable_values: Vec<f64>,
    pub score: ScoreType
}

impl<ScoreType> Ord for Individual<ScoreType> {
    fn cmp(&self, other: &Self) -> Ordering {
        self.score.cmp(&other.score)  // Higher score is better
    }
}
```

**Important**: The implementation assumes **maximization** (higher score = better).

### 4. AgentToAgentUpdate (`greyjack/src/agents/base/agent_to_agent_update.rs`)

Message structure for migration between agents.

```rust
pub struct AgentToAgentUpdate<ScoreType> {
    pub agent_id: usize,                              // Sender's ID
    pub migrants: Vec<Individual<ScoreType>>,        // Best individuals to migrate
    pub round_robin_status_vec: Vec<AgentStatuses>    // Status of all agents
}
```

### 5. AgentStatuses (`greyjack/src/agents/base/agent_statuses.rs`)

Simple enum for agent lifecycle state.

```rust
#[derive(Clone, Copy, Debug)]
pub enum AgentStatuses {
    Alive,   // Agent is actively optimizing
    Dead     // Agent has terminated but still participates in migration
}
```

---

## Parallel Execution Model

### Rayon-Based Parallelism

The solver uses Rayon's data parallelism to execute agents in parallel threads:

```rust
use rayon::prelude::*;

domain_builders.into_par_iter()
    .zip(cotwin_builders.into_par_iter())
    // ... more zips
    .for_each(|...| {
        // Each closure runs in its own thread
        agent_i.solve();
    });
```

**Key Characteristics**:

1. **Work-stealing scheduler** - Rayon automatically balances load across CPU cores
2. **Thread pool** - Default pool size equals number of logical CPUs
3. **Blocking operation** - `.for_each()` waits for all threads to complete

### Thread Safety Guarantees

All types used in parallel execution implement `Send`:

```rust
impl<EntityVariants, UtilityObjectVariants, ScoreType> 
Agent<EntityVariants, UtilityObjectVariants, ScoreType>
where
    ScoreType: ScoreTrait + Clone + AddAssign + PartialEq + 
               PartialOrd + Ord + Debug + Display + Send + Serialize

unsafe impl<EntityVariants, UtilityObjectVariants, ScoreType> Send for 
Agent<EntityVariants, UtilityObjectVariants, ScoreType>
```

---

## Communication Mechanism

### Ring Topology

Agents communicate in a unidirectional ring:

```
Agent 0 → Agent 1 → Agent 2 → ... → Agent N-1 → Agent 0
   ↑                                        ↓
   └────────────────────────────────────────┘
```

**Implementation** (solver.rs:92):
```rust
agents_updates_receivers.rotate_right(1);
```

This creates the mapping:
- Agent i sends to: `updates_to_agent_sender[i]`
- Agent i receives from: `updates_for_agent_receiver[i]`

After rotation, `updates_for_agent_receiver[i]` is actually the sender from agent `(i-1) mod n_jobs`.

### Channel Configuration

```rust
use crossbeam_channel::*;

// Bounded channel with capacity 1
let (sender, receiver): (Sender<T>, Receiver<T>) = bounded(1);
```

**Why bounded with capacity 1?**

1. **Prevents memory buildup** - If receiver is slow, sender blocks
2. **Simplifies synchronization** - No need for backpressure handling
3. **Ensures fresh data** - Each migration sends only the latest best

### Send/Receive Pattern

To prevent deadlock, agents alternate send/receive order based on agent_id:

```rust
if self.agent_id % 2 == 0 {
    // Even agents: send first, then receive
    self.send_updates()?;
    self.receive_updates()?;
} else {
    // Odd agents: receive first, then send
    self.receive_updates()?;
    self.send_updates()?;
}
```

This is a classic pattern for deadlock prevention in ring communication.

---

## Migration Strategy

### Migration Frequency

Controlled by `migration_frequency` parameter:
- **High frequency** (e.g., every 10 steps): More diversity exchange, but higher overhead
- **Low frequency** (e.g., every 1000 steps): Less overhead, but slower convergence

```rust
self.steps_to_send_updates -= 1;
if self.steps_to_send_updates <= 0 {
    // Perform migration
    self.steps_to_send_updates = self.migration_frequency;
}
```

### Migration Rate

Determines how many individuals migrate:

```rust
pub migration_rate: f64  // Fraction of population to migrate
```

For population-based algorithms (GA, LSHADE):
```rust
let migrants_count = (self.migration_rate * (self.population_size as f64)).ceil() as usize;
migrants = (0..migrants_count).map(|i| self.population[i].clone()).collect();
```

For local search algorithms (TS, LA, SA):
```rust
migrants = vec![self.population[0].clone(); 1];  // Only the best
```

### Sending Updates (`agent_base.rs:322-367`)

```rust
fn send_updates(&mut self) -> Result<(), String> {
    // 1. Clone status vector
    let round_robin_status_vec = self.round_robin_status_vec.clone();
    
    // 2. Select migrants based on algorithm type
    let migrants: Vec<Individual<ScoreType>>;
    match &mut self.metaheuristic_base {
        MetaheuristicsBasesVariants::GAB(gab) => {
            // Top migration_rate * population_size individuals
            let migrants_count = (self.migration_rate * (self.population_size as f64)).ceil() as usize;
            migrants = (0..migrants_count).map(|i| self.population[i].clone()).collect();
        },
        MetaheuristicsBasesVariants::LAB(la) => {
            // Only the best individual
            migrants = vec![self.population[0].clone(); 1];
        },
        // ... similar for other algorithms
    }
    
    // 3. Create update message
    let agent_update = AgentToAgentUpdate::new(self.agent_id, migrants, round_robin_status_vec);
    
    // 4. Send through channel
    let send_result = self.updates_to_agent_sender.as_mut().unwrap().send(agent_update);
    match send_result {
        Err(e) => return Err(format!("Failed to send: {}", e)),
        _ => Ok(())
    }
}
```

### Receiving Updates (`agent_base.rs:369-444`)

```rust
fn receive_updates(&mut self) -> Result<usize, usize> {
    // 1. Block until update is received
    let received_updates = self.updates_for_agent_receiver.as_mut().unwrap().recv()?;
    
    // 2. Update status vector (exclude self)
    for i in 0..self.round_robin_status_vec.len() {
        if i != self.agent_id {
            self.round_robin_status_vec[i] = received_updates.round_robin_status_vec[i];
        }
    }
    
    // 3. Determine which individuals to replace
    let comparison_ids: Vec<usize>;
    match current_agent_kind {
        MetaheuristicKind::Population => {
            // Replace worst individuals with migrants
            let migrants_count = received_updates.migrants.len();
            comparison_ids = ((self.population_size - migrants_count)..self.population_size).collect();
        },
        MetaheuristicKind::LocalSearch => {
            // Replace only the current best
            comparison_ids = vec![0; 1]
        }
    }
    
    // 4. Replace individuals if migrants are better
    for i in 0..received_updates.migrants.len() {
        if received_updates.migrants[i] <= self.population[comparison_ids[i]] {
            self.population[comparison_ids[i]] = received_updates.migrants[i].clone();
        }
    }
    
    Ok(0)
}
```

### Migration Strategy by Algorithm

| Algorithm | Migration Count | Replacement Strategy |
|-----------|----------------|----------------------|
| Genetic Algorithm | `migration_rate * population_size` | Replace worst individuals |
| LSHADE | `migration_rate * population_size` | Replace worst individuals |
| Tabu Search | 1 (best only) | Replace current best if better |
| Late Acceptance | 1 (best only) | Replace if better than late_scores.back() |
| Simulated Annealing | 1 (best only) | Replace current best if better |

---

## Score Calculation Modes

The GreyJack Solver supports two score calculation modes, which significantly impact performance:

### Plain Score Calculation

Calculates scores from scratch for all variables in each individual.

**When to use:**
- Problems with small to medium number of variables
- When constraints are simple and fast to evaluate
- When memory is not a constraint

**Implementation** (`agent_base.rs:273-298`):
```rust
fn step_plain(&mut self) {
    let me_base = self.metaheuristic_base.as_trait();
    let mut new_population: Vec<Individual<ScoreType>> = Vec::new();
    
    // Sample candidate solutions
    let samples: Vec<Vec<f64>> = me_base.sample_candidates_plain(
        &mut self.population,
        &self.agent_top_individual,
        &mut self.score_requester.variables_manager
    );
    
    // Calculate scores from scratch
    let mut scores = self.score_requester.request_score_plain(&samples);
    
    // Apply score precision if specified
    match &self.score_precision {
        Some(precision) => scores.iter_mut().for_each(|score| score.round(&precision)),
        None => ()
    }
    
    // Create individuals
    let mut candidates: Vec<Individual<ScoreType>> = Vec::new();
    for i in 0..samples.len() {
        candidates.push(Individual::new(samples[i].to_owned(), scores[i].to_owned()));
    }
    
    // Build new population
    new_population = me_base.build_updated_population(&self.population, &mut candidates);
    self.population = new_population;
}
```

### Incremental Score Calculation

Calculates only delta (change) in score for modified variables, then updates the total score.

**When to use:**
- Problems with large number of variables
- When only a few variables change between iterations
- When full score calculation is expensive
- Local search algorithms (TS, LA, SA) that modify one solution at a time

**Key advantages:**
- **Significant performance improvement** for problems with many variables
- **O(k) complexity** where k = number of changed variables
- **Enables efficient local search** by tracking changes

**Implementation** (`agent_base.rs:300-320`):
```rust
fn step_incremental(&mut self) {
    let me_base = self.metaheuristic_base.as_trait();
    let mut new_population: Vec<Individual<ScoreType>> = Vec::new();
    
    // Sample candidate with delta tracking
    let (mut sample, deltas) = me_base.sample_candidates_incremental(
        &mut self.population,
        &self.agent_top_individual,
        &mut self.score_requester.variables_manager
    );
    
    // Calculate score deltas (not full scores)
    let mut scores = self.score_requester.request_score_incremental(&sample, &deltas);
    
    // Apply score precision if specified
    match &self.score_precision {
        Some(precision) => scores.iter_mut().for_each(|score| score.round(&precision)),
        None => ()
    }
    
    // Build new population using deltas
    new_population = me_base.build_updated_population_incremental(
        &self.population,
        &mut sample,
        deltas,
        scores
    );
    
    self.population = new_population;
}
```

### Delta Representation

Incremental mode uses delta representation to track changes:

```rust
// Delta: (variable_index, new_value)
type Delta = (usize, f64);

// Deltas for a candidate: list of changed variables
type Deltas = Vec<Delta>;

// Deltas for multiple candidates
type AllDeltas = Vec<Deltas>;
```

**Example from Tabu Search** (`tabu_search_base.rs:124-132`):
```rust
let mut deltas: Vec<Vec<(usize, f64)>> = (0..self.neighbours_count).into_iter().map(|i| {
    // do_move returns: (candidate, changed_columns, candidate_deltas)
    let (_, changed_columns, candidate_deltas) =
        self.mover.do_move(&current_best_candidate, variables_manager, true);
    
    let mut candidate_deltas = candidate_deltas.unwrap();
    variables_manager.fix_deltas(&mut candidate_deltas, changed_columns.clone());
    let changed_columns = changed_columns.unwrap();
    
    // Convert to (index, value) pairs
    let candidate_deltas: Vec<(usize, f64)> = changed_columns.iter()
        .zip(candidate_deltas.iter())
        .map(|(col_id, delta_value)| (*col_id, *delta_value))
        .collect();
    
    candidate_deltas
}).collect();
```

### Choosing the Right Mode

**Use Plain when:**
- Population-based algorithms (GA, LSHADE) with many candidates
- Variables are not related (no incremental optimization possible)
- Problem size is small (< 100 variables)

**Use Incremental when:**
- Local search algorithms (TS, LA, SA) with single candidate
- Large problems (> 100 variables)
- Constraints allow efficient delta calculation
- Most variables remain unchanged between iterations

### Java Implementation Considerations

```java
public enum ScoreCalculationMode {
    PLAIN,
    INCREMENTAL
}

public interface ScoreCalculator<ScoreType extends Score> {
    // Plain mode: calculate full score
    List<ScoreType> requestScorePlain(List<List<Double>> samples);
    
    // Incremental mode: calculate score deltas
    List<ScoreType> requestScoreIncremental(
        List<Double> sample,
        List<List<Pair<Integer, Double>>> deltas
    );
}

// Example: Incremental score calculation
public List<ScoreType> requestScoreIncremental(
        List<Double> currentSolution,
        List<List<Pair<Integer, Double>>> allDeltas) {
    
    List<ScoreType> scores = new ArrayList<>();
    
    for (List<Pair<Integer, Double>> deltas : allDeltas) {
        // Start from current solution's score
        ScoreType currentScore = getCurrentScore();
        ScoreType deltaScore = calculateDelta(currentSolution, deltas);
        
        // Apply delta to get new score
        ScoreType newScore = currentScore.add(deltaScore);
        scores.add(newScore);
        
        // Update current solution with deltas
        applyDeltas(currentSolution, deltas);
    }
    
    return scores;
}
```

---

## Observer Pattern

The Observer pattern allows external components to monitor solver progress and receive updates when better solutions are found.

### Observer Interface

```rust
pub trait ObserverTrait {
    fn update(&mut self, solution: Value);
}
```

### Observable Interface

```rust
pub trait ObservableTrait {
    fn register_observer(&mut self, observer: Box<dyn ObserverTrait>);
    fn notify_observers(&self, solution: Value);
}
```

### Agent as Observable

The Agent implements ObservableTrait to notify observers of improvements:

```rust
impl<EntityVariants, UtilityObjectVariants, ScoreType> ObservableTrait
for Agent<EntityVariants, UtilityObjectVariants, ScoreType> {
    
    fn register_observer(&mut self, observer: Box<dyn ObserverTrait>){
        // Stub implementation - observers are managed by Solver
    }
    
    fn notify_observers(&self, solution: Value) {
        match &mut (*self.observers.lock().unwrap()) {
            None => (),
            Some(observers) => {
                for observer in observers {
                    observer.update(solution.clone());
                }
            }
        }
    }
}
```

### Observer Notification Trigger

Observers are notified when the global best is updated:

```rust
fn update_global_top(&mut self) {
    self.is_global_top_updated = false;
    let mut global_top_individual = self.global_top_individual.lock().unwrap();
    let mut global_top_json = self.global_top_json.lock().unwrap();
    
    if self.agent_top_individual.score > global_top_individual.score {
        *global_top_individual = self.agent_top_individual.clone();
        *global_top_json = self.convert_to_json(self.agent_top_individual.clone());
        self.is_global_top_updated = true;
        
        // Notify observers when global best improves
        if self.observers_count > 0 {
            self.notify_observers(global_top_json.clone());
        }
    }
}
```

### Thread-Safe Observer Management

Observers are shared across all agents using Arc<Mutex<>>:

```rust
pub observers: Arc<Mutex<Option<Vec<Box<dyn ObserverTrait + Send>>>>>,
pub observers_count: usize,
```

**In Solver initialization**:
```rust
let observers_arc: Arc<Mutex<Option<Vec<Box<dyn ObserverTrait + Send>>>>>;
match observers {
    None => {
        observers_arc = Arc::new(Mutex::new(None));
    }
    Some(observers) => {
        observers_arc = Arc::new(Mutex::new(Some(observers)));
    }
}
```

### Java Implementation

```java
public interface Observer {
    void update(String solutionJson);
}

public class ObservableAgent<ScoreType extends Score> {
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    
    public void addObserver(Observer observer) {
        observers.add(observer);
    }
    
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }
    
    protected void notifyObservers(String solutionJson) {
        for (Observer observer : observers) {
            observer.update(solutionJson);
        }
    }
}

// Example Observer
public class ProgressLogger implements Observer {
    @Override
    public void update(String solutionJson) {
        System.out.println("New best solution: " + solutionJson);
    }
}

// Example: File Writer Observer
public class SolutionFileWriter implements Observer {
    private final String filePath;
    
    public SolutionFileWriter(String filePath) {
        this.filePath = filePath;
    }
    
    @Override
    public void update(String solutionJson) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write(solutionJson + "\n");
        } catch (IOException e) {
            System.err.println("Failed to write solution: " + e.getMessage());
        }
    }
}
```

---

## Logging and Monitoring

### Logging Levels

The solver supports multiple logging levels for different verbosity needs:

```rust
#[derive(Clone)]
pub enum SolverLoggingLevels {
    Info,       // Log every step
    FreshOnly,   // Log only when global best improves
    Trace,       // Log detailed information (current + agent best)
    Warn,        // Log warnings only
    Silent       // No logging
}
```

### Logging Implementation

```rust
pub fn log_solving_info(&self) {
    match self.agent_status {
        AgentStatuses::Alive => {
            match self.logging_level {
                SolverLoggingLevels::Info => {
                    let solving_time = ((Utc::now().timestamp_millis() - self.solving_start) as f64) / 1000.0;
                    let info_message = format!("{}, Agent: {:3}, Steps: {:10}, Global best score: {}, Solving time: {}",
                        Local::now().format("%Y-%m-%d %H:%M:%S"),
                        self.agent_id,
                        self.step_id,
                        self.global_top_individual.lock().unwrap().score,
                        solving_time);
                    println!("{}", info_message);
                },
                SolverLoggingLevels::FreshOnly => {
                    if self.is_global_top_updated {
                        let solving_time = ((Utc::now().timestamp_millis() - self.solving_start) as f64) / 1000.0;
                        let info_message = format!("{}, Agent: {:3}, Steps: {:10}, Global best: {}, Solving time: {}",
                            Local::now().format("%Y-%m-%d %H:%M:%S"),
                            self.agent_id,
                            self.step_id,
                            self.global_top_individual.lock().unwrap().score,
                            solving_time);
                        println!("{}", info_message);
                    }
                },
                SolverLoggingLevels::Trace => {
                    let solving_time = ((Utc::now().timestamp_millis() - self.solving_start) as f64) / 1000.0;
                    let info_message = format!("{}, Agent: {:3}, Steps: {:10}, Global best: {}, Agent's best/current: {} / {}, Solving time: {}",
                        Local::now().format("%Y-%m-%d %H:%M:%S"),
                        self.agent_id,
                        self.step_id,
                        self.global_top_individual.lock().unwrap().score,
                        self.agent_top_individual.score,
                        self.population[0].score,
                        solving_time);
                    println!("{}", info_message);
                },
                _ => (),
            }
        },
        _ => ()
    }
}
```

### Logging Recommendations

| Level | When to Use | Output Frequency |
|-------|--------------|------------------|
| `Info` | Development and debugging | Every step |
| `FreshOnly` | Production monitoring | Only when global best improves |
| `Trace` | Deep debugging | Every step with detailed info |
| `Silent` | Maximum performance | None |

**Performance impact**:
- `Silent`: Best performance (no I/O overhead)
- `FreshOnly`: Minimal overhead (only on improvements)
- `Info` / `Trace`: Significant overhead (I/O on every step)

### Java Implementation

```java
public enum SolverLoggingLevel {
    INFO,
    FRESH_ONLY,
    TRACE,
    WARN,
    SILENT
}

public class Agent<ScoreType extends Score> {
    protected final SolverLoggingLevel loggingLevel;
    protected boolean isGlobalTopUpdated;
    protected long solvingStart;
    
    protected void logSolvingInfo() {
        if (agentStatus == AgentStatus.ALIVE) {
            switch (loggingLevel) {
                case INFO:
                    logInfo();
                    break;
                case FRESH_ONLY:
                    if (isGlobalTopUpdated) {
                        logFreshOnly();
                    }
                    break;
                case TRACE:
                    logTrace();
                    break;
                default:
                    break;
            }
        }
    }
    
    private void logInfo() {
        double solvingTime = (System.currentTimeMillis() - solvingStart) / 1000.0;
        System.out.printf("%s, Agent: %3d, Steps: %10d, Global best: %s, Solving time: %.2f%n",
            LocalDateTime.now(),
            agentId,
            stepId,
            globalState.getGlobalTop().getScore(),
            solvingTime);
    }
    
    private void logFreshOnly() {
        if (isGlobalTopUpdated) {
            double solvingTime = (System.currentTimeMillis() - solvingStart) / 1000.0;
            System.out.printf("%s, Agent: %3d, Steps: %10d, Global best: %s, Solving time: %.2f%n",
                LocalDateTime.now(),
                agentId,
                stepId,
                globalState.getGlobalTop().getScore(),
                solvingTime);
        }
    }
    
    private void logTrace() {
        double solvingTime = (System.currentTimeMillis() - solvingStart) / 1000.0;
        System.out.printf("%s, Agent: %3d, Steps: %10d, Global best: %s, Agent's best/current: %s / %s, Solving time: %.2f%n",
            LocalDateTime.now(),
            agentId,
            stepId,
            globalState.getGlobalTop().getScore(),
            agentTopIndividual.getScore(),
            population.get(0).getScore(),
            solvingTime);
    }
}
```

---

## Initial Solution Support

The solver supports warm-starting from an existing solution, which can significantly speed up convergence.

### Initial Solution Variants

```rust
#[derive(Clone, Debug)]
pub enum InitialSolutionVariants<DomainType>
where DomainType: Clone + Send {
    CotwinValuesVector(Value),      // Raw solution as JSON
    DomainObject(DomainType)        // Pre-built domain object
}
```

### Solver Initialization with Initial Solution

```rust
let initial_solutions: Vec<Option<InitialSolutionVariants<DomainType>>> =
    vec![initial_solution.clone(); n_jobs];

// In parallel agent initialization
match is_i {
    None => {
        is_already_initialized = false;
        domain_i = db_i.build_domain_from_scratch();
    },
    Some(isv) => {
        match isv {
            InitialSolutionVariants::CotwinValuesVector(raw_solution) =>
                domain_i = db_i.build_from_solution(&raw_solution, None),
            InitialSolutionVariants::DomainObject(existing_domain) =>
                domain_i = db_i.build_from_domain(&existing_domain),
        }
    }
}
```

### Use Cases

1. **Warm-starting from previous runs**
   ```rust
   let previous_solution = load_solution_from_file("best_solution.json");
   let solution = Solver::solve(
       domain_builder,
       cotwin_builder,
       agent_builder,
       n_jobs,
       None,
       SolverLoggingLevels::FreshOnly,
       None,
       Some(InitialSolutionVariants::CotwinValuesVector(previous_solution))
   );
   ```

2. **Multi-stage solving**
   - Stage 1: Solve with relaxed constraints
   - Stage 2: Use stage 1 solution as initial point for full problem

3. **Replanning scenarios**
   - When problem parameters change, use previous solution as starting point

### Java Implementation

```java
public enum InitialSolution<DomainType> {
    COTWIN_VALUES_VECTOR(String rawSolution),
    DOMAIN_OBJECT(DomainType domainObject)
}

public class Solver<ScoreType extends Score> {
    public static <ScoreType extends Score, DomainType> String solve(
            DomainBuilder<DomainType> domainBuilder,
            CotwinBuilder cotwinBuilder,
            AgentBuilder<ScoreType> agentBuilder,
            int nJobs,
            List<Long> scorePrecision,
            SolverLoggingLevel loggingLevel,
            List<Observer> observers,
            InitialSolution<DomainType> initialSolution) {
        
        // Create domains based on initial solution
        List<DomainType> domains = new ArrayList<>(nJobs);
        for (int i = 0; i < nJobs; i++) {
            DomainType domain;
            if (initialSolution == null) {
                domain = domainBuilder.buildDomainFromScratch();
            } else {
                switch (initialSolution) {
                    case COTWIN_VALUES_VECTOR(String rawSolution):
                        domain = domainBuilder.buildFromSolution(rawSolution, null);
                        break;
                    case DOMAIN_OBJECT(DomainType existingDomain):
                        domain = domainBuilder.buildFromDomain(existingDomain);
                        break;
                }
            }
            domains.add(domain);
        }
        
        // Continue with solver logic...
    }
}
```

---

## Metaheuristic-Specific Behaviors

Each metaheuristic has unique behaviors that affect how it interacts with migration and global best updates.

### Genetic Algorithm (GA)

**Characteristics:**
- Population-based: Maintains multiple individuals
- Migration: Sends top `migration_rate * population_size` individuals
- Replacement: Replaces worst individuals with migrants

**Key parameters:**
```rust
pub crossover_probability: f64,  // Probability of crossover vs. mutation
pub p_best_rate: f64,           // Fraction of top individuals to select from
```

**Selection strategy:**
- **P-best selection**: Randomly select from top `p_best_rate * population_size` individuals
- **P-worst selection**: Randomly select from worst `p_best_rate * population_size` individuals

**Crossover:** Arithmetic crossover with random weights
```rust
let weight = random.nextDouble();
offspring1[i] = parent1[i] * weight + parent2[i] * (1.0 - weight);
offspring2[i] = parent2[i] * weight + parent1[i] * (1.0 - weight);
```

### Tabu Search (TS)

**Characteristics:**
- Local search: Maintains single solution
- Migration: Sends only the best individual
- Global best comparison: Optional via `compare_to_global` flag

**Key parameters:**
```rust
pub neighbours_count: usize,    // Number of neighbours to evaluate
pub compare_to_global: bool,     // Whether to accept global best
```

**Search strategy:**
- Generate `neighbours_count` candidates by applying moves
- Select best candidate from neighbours
- Accept if better than current best

**Global best integration** (`agent_base.rs:475-481`):
```rust
MetaheuristicsBasesVariants::TSB(tsb) => {
    if global_top_individual.score > self.agent_top_individual.score {
        if tsb.compare_to_global {
            self.population[0] = global_top_individual.clone();
        }
    }
}
```

### Late Acceptance (LA)

**Characteristics:**
- Local search: Maintains single solution
- Migration: Sends only the best individual
- Accepts candidates if better than recent scores

**Key parameters:**
```rust
pub late_acceptance_size: usize,  // Size of score history (VecDeque)
```

**Acceptance criterion:**
```rust
let candidate_to_compare_score = self.late_scores.back().unwrap().clone();
if (candidate_score <= candidate_to_compare_score) ||
   (candidate_score <= current_population[0].score) {
    // Accept candidate
    self.late_scores.push_front(candidate_score);
    if self.late_scores.len() > self.late_acceptance_size {
        self.late_scores.pop_back();
    }
}
```

**Global best integration** (`agent_base.rs:466-474`):
```rust
MetaheuristicsBasesVariants::LAB(la) => {
    if global_top_individual.score > self.agent_top_individual.score {
        la.late_scores.push_front(self.population[0].score.clone());
        if la.late_scores.len() > la.late_acceptance_size {
            la.late_scores.pop_back();
        }
        self.population[0] = global_top_individual.clone();
    }
}
```

**Note**: Frequent migration works poorly for Late Acceptance, but sharing global best works well.

### Simulated Annealing (SA)

**Characteristics:**
- Local search: Maintains single solution
- Migration: Sends only the best individual
- Accepts worse candidates with probability based on temperature

**Key parameters:**
```rust
pub initial_temperature: Vec<f64>,  // Initial temperature for each score component
pub cooling_rate: Option<f64>,       // Temperature decay rate (or None for adaptive)
```

**Temperature update:**
```rust
match self.cooling_rate {
    Some(c_r) => {
        // Exponential cooling
        self.current_temperature = self.current_temperature.iter()
            .map(|ct| (ct * c_r).max(0.0000001))
            .collect();
    }
    None => {
        // Adaptive: based on termination progress
        let accomplish_rate = self.termination_strategy.as_trait().get_accomplish_rate();
        self.current_temperature = self.current_temperature.iter()
            .map(|_| 1.0 - accomplish_rate)
            .collect();
    }
}
```

**Acceptance probability:**
```rust
let current_energy = current_population[0].score.as_vec();
let candidate_energy = candidates[0].score.as_vec();

let accept_proba: f64 = current_energy.iter()
    .zip(candidate_energy.iter())
    .enumerate()
    .map(|(i, (cur_e, can_e))| {
        exp.powf(-((can_e - cur_e) / self.current_temperature[i]))
    })
    .product();

let random_value = random_sampler.sample(&mut random_generator);

if (candidate_score <= current_score) || (random_value < accept_proba) {
    // Accept candidate
}
```

**Global best integration** (`agent_base.rs:483-487`):
```rust
MetaheuristicsBasesVariants::SAB(sab) => {
    if global_top_individual.score > self.agent_top_individual.score {
        self.population[0] = global_top_individual.clone();
    }
}
```

**Note**: Often gets stuck if comparing to global, but common performance increases greatly.

### LSHADE

**Characteristics:**
- Population-based: Maintains multiple individuals
- Migration: Sends top `migration_rate * population_size` individuals
- Replacement: Replaces worst individuals with migrants
- Adaptive mutation and crossover rates

**Key parameters:**
```rust
pub history_archive_size: usize,          // Size of successful parameter history
pub guarantee_of_change_size: usize,       // Minimum size for diversity
```

**Adaptive parameters:**
- Mutation rate (F) and crossover rate (CR) are stored in history archive
- Successful parameters are more likely to be selected
- Adaptive mutation probability based on population size

### Migration Strategy by Algorithm

| Algorithm | Population Type | Migration Count | Replacement | Global Best Integration |
|-----------|-----------------|------------------|--------------|------------------------|
| Genetic Algorithm | Population | `rate * pop_size` | Replace worst | None (population handles diversity) |
| LSHADE | Population | `rate * pop_size` | Replace worst | None (population handles diversity) |
| Tabu Search | Local Search | 1 (best) | Replace if better | Optional (`compare_to_global` flag) |
| Late Acceptance | Local Search | 1 (best) | Replace if better | Yes (updates late_scores) |
| Simulated Annealing | Local Search | 1 (best) | Replace if better | Yes (but may cause stucks) |

### Java Implementation Considerations

```java
public enum MetaheuristicKind {
    POPULATION,
    LOCAL_SEARCH
}

public abstract class MetaheuristicBase<ScoreType extends Score> {
    public abstract MetaheuristicKind getMetaheuristicKind();
    public abstract String getMetaheuristicName();
}

// Example: Tabu Search with global best flag
public class TabuSearchBase<ScoreType extends Score> extends MetaheuristicBase<ScoreType> {
    private final int neighboursCount;
    private final boolean compareGlobal;
    
    @Override
    public MetaheuristicKind getMetaheuristicKind() {
        return MetaheuristicKind.LOCAL_SEARCH;
    }
    
    public boolean shouldAcceptGlobalBest(Individual<ScoreType> globalBest,
                                        Individual<ScoreType> currentBest) {
        if (!compareGlobal) {
            return false;
        }
        return globalBest.compareTo(currentBest) > 0;
    }
}
```

---

## Termination Coordination

### Individual Termination

Each agent has its own termination strategy:

```rust
pub enum TerminationStrategiesVariants<ScoreType> {
    StL(StepsLimit),                    // Maximum number of steps
    SNI(ScoreNoImprovement<ScoreType>), // No improvement for time period
    TSL(TimeSpentLimit),               // Maximum time elapsed
    ScL(ScoreLimit<ScoreType>)          // Target score reached
}
```

### Status Propagation

Agents share their status through migration messages:

```rust
// When sending updates
let agent_update = AgentToAgentUpdate::new(
    self.agent_id, 
    migrants, 
    self.round_robin_status_vec.clone()  // Include all agents' status
);

// When receiving updates
for i in 0..self.round_robin_status_vec.len() {
    if i != self.agent_id {
        self.round_robin_status_vec[i] = received_updates.round_robin_status_vec[i];
    }
}
```

### Global Termination

All agents terminate when no alive agents remain:

```rust
fn update_alive_agents_count(&mut self) {
    self.alive_agents_count = self.round_robin_status_vec.iter()
        .filter(|x| matches!(x, AgentStatuses::Alive))
        .count();
}

// In main loop
if self.alive_agents_count == 0 {
    break;  // Exit optimization loop
}
```

### Dead Agent Behavior

Dead agents continue to:
1. Participate in migration (forward messages)
2. Update global best if they receive better solutions
3. Maintain status propagation

This ensures that:
- Communication ring remains intact
- Best solutions are still discovered and shared
- No agent becomes isolated

---

## Java Implementation Guide

### 1. Core Interfaces

#### Score Interface

```java
public interface Score extends Comparable<Score>, Serializable {
    Score getStubScore();
    void round(List<Long> precision);
    int precisionLen();
}
```

#### Individual Class

```java
public class Individual<ScoreType extends Score> 
    implements Comparable<Individual<ScoreType>>, Serializable {
    
    private List<Double> variableValues;
    private ScoreType score;
    
    public Individual(List<Double> variableValues, ScoreType score) {
        this.variableValues = variableValues;
        this.score = score;
    }
    
    @Override
    public int compareTo(Individual<ScoreType> other) {
        return this.score.compareTo(other.score);  // Minimization
    }
    
    // Getters and setters
}
```

#### Agent Status Enum

```java
public enum AgentStatus {
    ALIVE,
    DEAD
}
```

#### Agent Update Message

```java
public class AgentToAgentUpdate<ScoreType extends Score> implements Serializable {
    private final int agentId;
    private final List<Individual<ScoreType>> migrants;
    private final List<AgentStatus> roundRobinStatusVec;
    
    public AgentToAgentUpdate(int agentId, 
                              List<Individual<ScoreType>> migrants,
                              List<AgentStatus> roundRobinStatusVec) {
        this.agentId = agentId;
        this.migrants = migrants;
        this.roundRobinStatusVec = roundRobinStatusVec;
    }
    
    // Getters
}
```

### 2. Shared State Management

#### Thread-Safe Global State

```java
public class GlobalState<ScoreType extends Score> {
    private final Object lock = new Object();
    private Individual<ScoreType> globalTopIndividual;
    private String globalTopJson;
    
    public GlobalState(Individual<ScoreType> initialIndividual) {
        this.globalTopIndividual = initialIndividual;
        this.globalTopJson = "null";
    }
    
    public boolean tryUpdate(Individual<ScoreType> candidate) {
        synchronized (lock) {
            if (candidate.compareTo(globalTopIndividual) > 0) {
                globalTopIndividual = candidate;
                globalTopJson = convertToJson(candidate);
                return true;
            }
            return false;
        }
    }
    
    public Individual<ScoreType> getGlobalTop() {
        synchronized (lock) {
            return globalTopIndividual;
        }
    }
    
    private String convertToJson(Individual<ScoreType> individual) {
        // JSON serialization logic
        return "";  // Implement based on your needs
    }
}
```

### 3. Communication Channels

#### Bounded Channel Implementation

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class BoundedChannel<T> {
    private final BlockingQueue<T> queue;
    
    public BoundedChannel(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }
    
    public void send(T message) throws InterruptedException {
        queue.put(message);  // Blocks if queue is full
    }
    
    public T receive() throws InterruptedException {
        return queue.take();  // Blocks until message available
    }
    
    public boolean trySend(T message, long timeout, TimeUnit unit) 
        throws InterruptedException {
        return queue.offer(message, timeout, unit);
    }
    
    public T tryReceive(long timeout, TimeUnit unit) 
        throws InterruptedException {
        return queue.poll(timeout, unit);
    }
}
```

### 4. Agent Implementation

#### Agent Base Class

```java
public abstract class Agent<ScoreType extends Score> implements Runnable {
    
    // Configuration
    protected final double migrationRate;
    protected final int migrationFrequency;
    protected final TerminationStrategy<ScoreType> terminationStrategy;
    
    // Identity
    protected final int agentId;
    protected volatile AgentStatus agentStatus;
    
    // Population
    protected final int populationSize;
    protected List<Individual<ScoreType>> population;
    protected Individual<ScoreType> agentTopIndividual;
    
    // Shared state
    protected final GlobalState<ScoreType> globalState;
    
    // Communication
    protected final BoundedChannel<AgentToAgentUpdate<ScoreType>> sender;
    protected final BoundedChannel<AgentToAgentUpdate<ScoreType>> receiver;
    
    // Coordination
    protected final List<AgentStatus> roundRobinStatusVec;
    protected volatile int aliveAgentsCount;
    private int stepsToSendUpdates;
    
    // Logging
    protected final SolverLoggingLevel loggingLevel;
    protected long stepId;
    
    public Agent(int agentId,
                 double migrationRate,
                 int migrationFrequency,
                 TerminationStrategy<ScoreType> terminationStrategy,
                 int populationSize,
                 GlobalState<ScoreType> globalState,
                 BoundedChannel<AgentToAgentUpdate<ScoreType>> sender,
                 BoundedChannel<AgentToAgentUpdate<ScoreType>> receiver,
                 List<AgentStatus> roundRobinStatusVec,
                 SolverLoggingLevel loggingLevel) {
        
        this.agentId = agentId;
        this.migrationRate = migrationRate;
        this.migrationFrequency = migrationFrequency;
        this.terminationStrategy = terminationStrategy;
        this.populationSize = populationSize;
        this.globalState = globalState;
        this.sender = sender;
        this.receiver = receiver;
        this.roundRobinStatusVec = new ArrayList<>(roundRobinStatusVec);
        this.loggingLevel = loggingLevel;
        
        this.agentStatus = AgentStatus.ALIVE;
        this.stepsToSendUpdates = migrationFrequency;
        this.stepId = 0;
    }
    
    @Override
    public void run() {
        // Initialize population
        initPopulation();
        Collections.sort(population);
        updateTopIndividual();
        
        // Main optimization loop
        while (true) {
            // Execute optimization step
            if (agentStatus == AgentStatus.ALIVE) {
                step();
            }
            
            stepId++;
            
            // Sort and update best
            if (populationSize > 1) {
                Collections.sort(population);
            }
            updateTopIndividual();
            
            // Update termination status
            terminationStrategy.update(agentTopIndividual);
            if (terminationStrategy.isAccomplished()) {
                agentStatus = AgentStatus.DEAD;
                roundRobinStatusVec.set(agentId, AgentStatus.DEAD);
                logTermination();
            }
            
            // Update alive count
            updateAliveAgentsCount();
            
            // Check global termination
            if (aliveAgentsCount == 0) {
                break;
            }
            
            // Migration
            stepsToSendUpdates--;
            if (stepsToSendUpdates <= 0) {
                // Alternate send/receive to prevent deadlock
                if (agentId % 2 == 0) {
                    sendUpdates();
                    receiveUpdates();
                } else {
                    receiveUpdates();
                    sendUpdates();
                }
                stepsToSendUpdates = migrationFrequency;
            }
            
            // Update global best
            updateGlobalTop();
        }
    }
    
    // Abstract methods to be implemented by concrete algorithms
    protected abstract void initPopulation();
    protected abstract void step();
    protected abstract List<Individual<ScoreType>> selectMigrants();
    
    // Helper methods
    private void updateTopIndividual() {
        if (population.get(0).compareTo(agentTopIndividual) > 0) {
            agentTopIndividual = population.get(0);
        }
    }
    
    private void updateAliveAgentsCount() {
        aliveAgentsCount = (int) roundRobinStatusVec.stream()
            .filter(status -> status == AgentStatus.ALIVE)
            .count();
    }
    
    private void updateGlobalTop() {
        globalState.tryUpdate(agentTopIndividual);
    }
    
    private void sendUpdates() {
        try {
            List<Individual<ScoreType>> migrants = selectMigrants();
            AgentToAgentUpdate<ScoreType> update = new AgentToAgentUpdate<>(
                agentId,
                migrants,
                new ArrayList<>(roundRobinStatusVec)
            );
            sender.send(update);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Send interrupted", e);
        }
    }
    
    private void receiveUpdates() {
        try {
            AgentToAgentUpdate<ScoreType> update = receiver.receive();
            
            // Update status vector
            for (int i = 0; i < roundRobinStatusVec.size(); i++) {
                if (i != agentId) {
                    roundRobinStatusVec.set(i, update.getRoundRobinStatusVec().get(i));
                }
            }
            
            // Integrate migrants
            integrateMigrants(update.getMigrants());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Receive interrupted", e);
        }
    }
    
    protected abstract void integrateMigrants(List<Individual<ScoreType>> migrants);
    
    private void logTermination() {
        if (loggingLevel != SolverLoggingLevel.SILENT) {
            System.out.printf("Agent %d has terminated. Now forwarding updates.%n", agentId);
        }
    }
}
```

### 5. Solver Implementation

#### Main Solver Class

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Solver<ScoreType extends Score> {
    
    public static <ScoreType extends Score> String solve(
            DomainBuilder domainBuilder,
            CotwinBuilder cotwinBuilder,
            AgentBuilder<ScoreType> agentBuilder,
            int nJobs,
            List<Long> scorePrecision,
            SolverLoggingLevel loggingLevel,
            List<Observer> observers,
            Object initialSolution) {
        
        // Validate score precision
        if (scorePrecision != null) {
            // Validate precision length based on score type
        }
        
        // Create communication channels
        List<BoundedChannel<AgentToAgentUpdate<ScoreType>>> senders = 
            new ArrayList<>(nJobs);
        List<BoundedChannel<AgentToAgentUpdate<ScoreType>>> receivers = 
            new ArrayList<>(nJobs);
        
        for (int i = 0; i < nJobs; i++) {
            BoundedChannel<AgentToAgentUpdate<ScoreType>> channel = 
                new BoundedChannel<>(1);
            senders.add(channel);
            receivers.add(channel);
        }
        
        // Rotate receivers to create ring topology
        // Agent i receives from agent (i-1) mod nJobs
        Collections.rotate(receivers, 1);
        
        // Initialize status vector
        List<AgentStatus> roundRobinStatusVec = new ArrayList<>(nJobs);
        for (int i = 0; i < nJobs; i++) {
            roundRobinStatusVec.add(AgentStatus.ALIVE);
        }
        
        // Create shared global state
        Individual<ScoreType> stubIndividual = createStubIndividual();
        GlobalState<ScoreType> globalState = new GlobalState<>(stubIndividual);
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(nJobs);
        
        // Create and launch agents
        for (int i = 0; i < nJobs; i++) {
            Agent<ScoreType> agent = agentBuilder.buildAgent(
                i,
                domainBuilder.clone(),
                cotwinBuilder.clone(),
                globalState,
                senders.get(i),
                receivers.get(i),
                new ArrayList<>(roundRobinStatusVec),
                loggingLevel,
                scorePrecision,
                initialSolution
            );
            
            executor.submit(agent);
        }
        
        // Wait for all agents to complete
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Solver interrupted", e);
        }
        
        // Return best solution
        return globalState.getGlobalTopJson();
    }
    
    private static <ScoreType extends Score> Individual<ScoreType> createStubIndividual() {
        // Create stub individual based on score type
        return null;  // Implement based on your score type
    }
}
```

### 6. Termination Strategies

#### Termination Strategy Interface

```java
public interface TerminationStrategy<ScoreType extends Score> {
    boolean isAccomplished();
    void update(Individual<ScoreType> currentBest);
    double getAccomplishRate();
}
```

#### Example: Steps Limit

```java
public class StepsLimit<ScoreType extends Score> implements TerminationStrategy<ScoreType> {
    private final long maxSteps;
    private long currentSteps;
    
    public StepsLimit(long maxSteps) {
        this.maxSteps = maxSteps;
        this.currentSteps = 0;
    }
    
    @Override
    public boolean isAccomplished() {
        return currentSteps >= maxSteps;
    }
    
    @Override
    public void update(Individual<ScoreType> currentBest) {
        currentSteps++;
    }
    
    @Override
    public double getAccomplishRate() {
        return (double) currentSteps / maxSteps;
    }
}
```

#### Example: Score No Improvement

```java
public class ScoreNoImprovement<ScoreType extends Score> implements TerminationStrategy<ScoreType> {
    private final long noImprovementTimeMs;
    private final long startTime;
    private long lastImprovementTime;
    private Individual<ScoreType> bestSoFar;
    
    public ScoreNoImprovement(long noImprovementTimeMs) {
        this.noImprovementTimeMs = noImprovementTimeMs;
        this.startTime = System.currentTimeMillis();
        this.lastImprovementTime = startTime;
    }
    
    @Override
    public boolean isAccomplished() {
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastImprovementTime) >= noImprovementTimeMs;
    }
    
    @Override
    public void update(Individual<ScoreType> currentBest) {
        if (bestSoFar == null || currentBest.compareTo(bestSoFar) > 0) {
            bestSoFar = currentBest;
            lastImprovementTime = System.currentTimeMillis();
        }
    }
    
    @Override
    public double getAccomplishRate() {
        long currentTime = System.currentTimeMillis();
        long timeSinceImprovement = currentTime - lastImprovementTime;
        return (double) timeSinceImprovement / noImprovementTimeMs;
    }
}
```

### 7. Example: Genetic Algorithm Agent

```java
public class GeneticAlgorithmAgent<ScoreType extends Score> extends Agent<ScoreType> {
    
    private final double crossoverProbability;
    private final double pBestRate;
    private final Random random;
    
    public GeneticAlgorithmAgent(int agentId,
                                   double migrationRate,
                                   int migrationFrequency,
                                   TerminationStrategy<ScoreType> terminationStrategy,
                                   int populationSize,
                                   GlobalState<ScoreType> globalState,
                                   BoundedChannel<AgentToAgentUpdate<ScoreType>> sender,
                                   BoundedChannel<AgentToAgentUpdate<ScoreType>> receiver,
                                   List<AgentStatus> roundRobinStatusVec,
                                   SolverLoggingLevel loggingLevel,
                                   double crossoverProbability,
                                   double pBestRate) {
        super(agentId, migrationRate, migrationFrequency, terminationStrategy,
              populationSize, globalState, sender, receiver, 
              roundRobinStatusVec, loggingLevel);
        
        this.crossoverProbability = crossoverProbability;
        this.pBestRate = pBestRate;
        this.random = new Random(agentId);  // Seeded for reproducibility
    }
    
    @Override
    protected void initPopulation() {
        population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            List<Double> variables = sampleVariables();
            ScoreType score = evaluateScore(variables);
            population.add(new Individual<>(variables, score));
        }
    }
    
    @Override
    protected void step() {
        List<Individual<ScoreType>> candidates = new ArrayList<>();
        
        // Generate candidates
        for (int i = 0; i < populationSize / 2; i++) {
            List<Double> parent1 = selectPBest().getVariableValues();
            List<Double> parent2 = selectPBest().getVariableValues();
            
            // Crossover
            if (random.nextDouble() <= crossoverProbability) {
                List<List<Double>> offspring = crossover(parent1, parent2);
                parent1 = offspring.get(0);
                parent2 = offspring.get(1);
            }
            
            // Mutation
            mutate(parent1);
            mutate(parent2);
            
            // Evaluate
            ScoreType score1 = evaluateScore(parent1);
            ScoreType score2 = evaluateScore(parent2);
            
            candidates.add(new Individual<>(parent1, score1));
            candidates.add(new Individual<>(parent2, score2));
        }
        
        // Selection: replace worst with better candidates
        for (int i = 0; i < populationSize; i++) {
            Individual<ScoreType> worst = selectPWorst();
            Individual<ScoreType> candidate = candidates.get(i);
            
            if (candidate.compareTo(worst) > 0) {
                population.set(population.indexOf(worst), candidate);
            }
        }
    }
    
    @Override
    protected List<Individual<ScoreType>> selectMigrants() {
        // Select top migrationRate * populationSize individuals
        int migrantCount = (int) Math.ceil(migrationRate * populationSize);
        return new ArrayList<>(population.subList(0, migrantCount));
    }
    
    @Override
    protected void integrateMigrants(List<Individual<ScoreType>> migrants) {
        // Replace worst individuals with migrants
        for (int i = 0; i < migrants.size(); i++) {
            int replaceIndex = populationSize - migrants.size() + i;
            if (migrants.get(i).compareTo(population.get(replaceIndex)) > 0) {
                population.set(replaceIndex, migrants.get(i));
            }
        }
    }
    
    private Individual<ScoreType> selectPBest() {
        int pBestLimit = (int) Math.ceil(pBestRate * populationSize);
        int index = random.nextInt(pBestLimit);
        return population.get(index);
    }
    
    private Individual<ScoreType> selectPWorst() {
        int pBestLimit = (int) Math.ceil(pBestRate * populationSize);
        int index = populationSize - pBestLimit + random.nextInt(pBestLimit);
        return population.get(index);
    }
    
    private List<List<Double>> crossover(List<Double> parent1, List<Double> parent2) {
        List<Double> offspring1 = new ArrayList<>();
        List<Double> offspring2 = new ArrayList<>();
        
        for (int i = 0; i < parent1.size(); i++) {
            double weight = random.nextDouble();
            offspring1.add(parent1.get(i) * weight + parent2.get(i) * (1 - weight));
            offspring2.add(parent2.get(i) * weight + parent1.get(i) * (1 - weight));
        }
        
        return List.of(offspring1, offspring2);
    }
    
    private void mutate(List<Double> variables) {
        // Implement mutation logic
        // E.g., Gaussian mutation, swap mutation, etc.
    }
    
    private List<Double> sampleVariables() {
        // Implement variable sampling based on problem constraints
        return new ArrayList<>();
    }
    
    private ScoreType evaluateScore(List<Double> variables) {
        // Implement score evaluation
        return null;
    }
}
```

### 8. Java-Specific Considerations

#### Thread Pool vs Rayon

Rust's Rayon uses work-stealing, while Java's `ExecutorService` uses a fixed thread pool:

```java
// Equivalent to Rayon's parallel iterator
ExecutorService executor = Executors.newFixedThreadPool(nJobs);
for (int i = 0; i < nJobs; i++) {
    executor.submit(agent);
}
executor.shutdown();
executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
```

#### Memory Model

Java's memory model provides different guarantees than Rust's:

1. **Volatile fields**: Ensure visibility across threads
   ```java
   private volatile AgentStatus agentStatus;
   private volatile int aliveAgentsCount;
   ```

2. **Synchronized blocks**: Provide mutual exclusion
   ```java
   synchronized (lock) {
       // Critical section
   }
   ```

3. **Immutable objects**: Thread-safe by default
   ```java
   public final class Individual<ScoreType> {
       private final List<Double> variableValues;
       private final ScoreType score;
       // No setters after construction
   }
   ```

#### Exception Handling

Java requires explicit exception handling:

```java
try {
    sender.send(update);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException("Send interrupted", e);
}
```

#### Generics and Type Erasure

Java generics have type erasure, so runtime type checks require additional handling:

```java
@SuppressWarnings("unchecked")
public <T extends Score> T getScoreAs(Class<T> scoreClass) {
    return (T) this.score;
}
```

---

## Key Design Decisions

### 1. Ring Topology vs. Fully Connected

**Choice**: Ring topology (unidirectional)

**Rationale**:
- **O(n) channels** vs O(n²) for fully connected
- **Simpler coordination** - no broadcast needed
- **Sufficient diversity** - migration propagates through ring
- **Lower contention** - each agent only communicates with two neighbors

**Trade-off**: Slower propagation of best solutions across all agents

### 2. Bounded Channels (Capacity=1)

**Choice**: Bounded with capacity 1

**Rationale**:
- **Prevents memory buildup** - if receiver is slow, sender blocks
- **Ensures fresh data** - only latest migration is sent
- **Simplifies backpressure** - no need for complex buffering logic

**Trade-off**: May cause slight delays if sender must wait

### 3. Dead Agent Participation in Migration

**Choice**: Dead agents continue to forward migration messages

**Rationale**:
- **Maintains ring integrity** - no broken links
- **Allows discovery** - better solutions can still be found and shared
- **Simpler implementation** - no complex topology changes

**Trade-off**: Minimal overhead from inactive agents

### 4. Alternate Send/Receive Order

**Choice**: Even agents send first, odd agents receive first

**Rationale**:
- **Prevents deadlock** in synchronous ring communication
- **Classic pattern** for deadlock prevention
- **Minimal impact** on performance

**Trade-off**: Slightly asymmetric communication pattern

### 5. Global Best via Arc<Mutex<>> vs. Channels

**Choice**: Shared mutable state with mutex

**Rationale**:
- **Low latency** - direct access without message passing
- **Simple implementation** - no additional channels needed
- **Fine-grained locking** - only locked during updates

**Trade-off**: Potential contention if many agents update simultaneously

### 6. Status Propagation via Migration Messages

**Choice**: Include status vector in every migration message

**Rationale**:
- **No extra synchronization** - piggybacks on existing communication
- **Eventual consistency** - status propagates through ring
- **Low overhead** - status vector is small

**Trade-off**: Slight delay in status propagation

---

## Performance Considerations

### 1. Scalability

The island model provides **nearly linear horizontal scaling**:

```
Speedup ≈ n_jobs * efficiency
```

Where efficiency depends on:
- Migration frequency (lower = better)
- Communication overhead (channels, serialization)
- Contention on shared state (global best)

**Empirical observations** (from GreyJack README):
- Adding more agents typically yields proportional performance improvements
- Depends on problem characteristics and algorithm settings

### 2. Migration Frequency Tuning

**High frequency** (e.g., every 10 steps):
- Pros: Faster diversity exchange, quicker convergence
- Cons: Higher overhead, potential premature convergence

**Low frequency** (e.g., every 1000 steps):
- Pros: Lower overhead, more independent exploration
- Cons: Slower convergence, risk of getting stuck

**Recommendation**: Start with `migration_frequency = 100` and tune based on problem.

### 3. Migration Rate Tuning

**High rate** (e.g., 0.5 = 50%):
- Pros: More diversity, faster propagation
- Cons: Less independent exploration, potential homogenization

**Low rate** (e.g., 0.05 = 5%):
- Pros: More independent exploration
- Cons: Slower propagation, less diversity exchange

**Recommendation**: Start with `migration_rate = 0.1` and tune based on problem.

### 4. Population Size vs. Number of Agents

**Trade-off**: 
- **More agents** with smaller populations: Better parallelism, less diversity per island
- **Fewer agents** with larger populations: Less parallelism, more diversity per island

**Guideline**: 
```
total_population = n_jobs * population_size
```

Keep `total_population` constant while varying `n_jobs` for fair comparison.

### 5. Thread Pool Configuration

**Rust (Rayon)**:
- Default: Number of logical CPUs
- Can be overridden: `rayon::ThreadPoolBuilder::new().num_threads(n).build()`

**Java**:
```java
int nThreads = Runtime.getRuntime().availableProcessors();
ExecutorService executor = Executors.newFixedThreadPool(nThreads);
```

**Recommendation**: Use number of physical cores for CPU-bound tasks.

### 6. Memory Usage

**Per-agent memory**:
- Population: `population_size * individual_size`
- Score calculator: Depends on problem
- Domain model: Depends on problem

**Shared memory**:
- Global best: `individual_size`
- Observers: Depends on implementation

**Total memory**:
```
total_memory ≈ n_jobs * per_agent_memory + shared_memory
```

### 7. Contention Points

**Potential bottlenecks**:

1. **Global best updates** (Arc<Mutex<>>)
   - Mitigation: Update only when agent finds better solution
   - Consider lock-free atomic operations for simple scores

2. **Channel operations** (send/receive)
   - Mitigation: Use bounded channels to prevent buildup
   - Consider asynchronous operations for non-blocking behavior

3. **Observer notifications**
   - Mitigation: Use separate thread for observers
   - Batch notifications to reduce frequency

### 8. Load Balancing

**Rust (Rayon)**:
- Work-stealing scheduler automatically balances load
- No manual intervention needed

**Java**:
- Fixed thread pool may have imbalance if agents terminate early
- Consider using `ForkJoinPool` for work-stealing:
```java
ForkJoinPool pool = new ForkJoinPool(nJobs);
pool.invoke(agentTask);
```

---

## Summary

The GreyJack Solver's multithreading island model provides an elegant and efficient approach to parallel optimization:

### Key Strengths

1. **Simple yet effective** - Ring topology with minimal coordination
2. **Scalable** - Nearly linear horizontal scaling
3. **Flexible** - Supports multiple metaheuristics and termination strategies
4. **Robust** - Dead agents don't break the system
5. **Efficient** - Low overhead communication and shared state

### Critical Implementation Details

1. **Ring topology** with unidirectional communication
2. **Bounded channels** (capacity=1) for backpressure
3. **Alternate send/receive** to prevent deadlock
4. **Status propagation** through migration messages
5. **Shared global best** via mutex-protected state
6. **Dead agent participation** to maintain ring integrity

### Java Implementation Checklist

- [ ] Implement core interfaces (Score, Individual, AgentStatus)
- [ ] Create thread-safe shared state (GlobalState)
- [ ] Implement bounded channels with blocking operations
- [ ] Build Agent base class with migration logic
- [ ] Implement concrete metaheuristic agents (GA, TS, LA, SA, LSHADE)
- [ ] Create Solver class with thread pool management
- [ ] Implement termination strategies
- [ ] Add logging and observability
- [ ] Tune migration parameters for your problem
- [ ] Profile and optimize for your specific use case

### References

- **GreyJack Rust Implementation**: `/greyjack/src/solver/solver.rs`
- **Agent Implementation**: `/greyjack/src/agents/base/agent_base.rs`
- **Communication Types**: `/greyjack/src/agents/base/agent_to_agent_update.rs`
- **Examples**: `/examples/nqueens/src/main.rs`

---

**Document Version**: 1.0  
**Last Updated**: 2025-12-29  
**GreyJack Version**: Rust edition  
**Target Implementation**: Java (SE 11+)
