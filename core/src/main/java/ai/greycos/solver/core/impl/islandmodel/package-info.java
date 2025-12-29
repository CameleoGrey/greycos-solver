/**
 * Island model implementation for Greycos.
 *
 * <p>The island model is a parallel evolutionary computation paradigm where multiple independent
 * "islands" (agents) run separate searches and periodically exchange their best solutions
 * (migrants) through migration.
 *
 * <p>This package contains the core infrastructure for the island model:
 *
 * <ul>
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.IslandModelPhase} - Coordinator phase that
 *       manages multiple islands
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.IslandAgent} - Individual island agent
 *       running phases independently
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.SharedGlobalState} - Thread-safe global best
 *       tracking
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.BoundedChannel} - Agent-to-agent
 *       communication channel
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.AgentUpdate} - Migration message structure
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.AgentStatus} - Agent lifecycle status
 *   <li>{@link ai.greycos.solver.core.impl.islandmodel.IslandModelConfig} - Configuration for
 *       island model
 * </ul>
 *
 * <h2>Key Design Principles</h2>
 *
 * <ul>
 *   <li><b>Opt-in Feature:</b> Island model is disabled by default to maintain backward
 *       compatibility
 *   <li><b>Phase Integration:</b> Island model is implemented as a phase type, preserving Greycos's
 *       phase architecture
 *   <li><b>Ring Topology:</b> Agents communicate in a ring, each sending to the next agent
 *   <li><b>Periodic Migration:</b> Best solutions are exchanged between islands at configurable
 *       intervals
 *   <li><b>Independent Searches:</b> Each island maintains its own solution state and runs phases
 *       independently
 *   <li><b>Thread Safety:</b> Global best is tracked with minimal contention using volatile +
 *       synchronized
 * </ul>
 *
 * <h2>Migration Algorithm</h2>
 *
 * <p>Migration occurs periodically based on the configured frequency:
 *
 * <ol>
 *   <li>Each agent sends its current best solution as a migrant
 *   <li>Each agent receives a migrant from the previous agent in the ring
 *   <li>If the migrant is better than the agent's current best, the agent replaces its solution
 *   <li>Status vector is updated to reflect which agents are still alive
 *   <li>Dead agents continue to forward messages to maintain ring topology
 * </ol>
 *
 * <h2>Termination</h2>
 *
 * <p>An agent terminates when:
 *
 * <ol>
 *   <li>All its configured phases have completed
 *   <li>AND no other agents remain alive (status vector shows all DEAD)
 * </ol>
 *
 * <h2>Performance Characteristics</h2>
 *
 * <ul>
 *   <li><b>Scaling:</b> Near-linear horizontal scaling with number of islands (≥70% efficiency
 *       target)
 *   <li><b>Memory:</b> O(n) where n is number of islands (each island has its own solution state)
 *   <li><b>Communication:</b> O(n) per migration (ring topology)
 *   <li><b>Contention:</b> Low contention on global best (updates only on improvements)
 * </ul>
 *
 * @see ai.greycos.solver.core.impl.islandmodel.IslandModelConfig
 * @see ai.greycos.solver.core.impl.islandmodel.IslandModelPhase
 * @see ai.greycos.solver.core.impl.islandmodel.IslandAgent
 */
package ai.greycos.solver.core.impl.islandmodel;
