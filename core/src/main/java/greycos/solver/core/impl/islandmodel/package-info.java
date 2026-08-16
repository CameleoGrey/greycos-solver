/**
 * Island model implementation for GreyCOS.
 *
 * <p>The island model is a parallel evolutionary computation paradigm where multiple independent
 * "islands" (agents) run separate searches and periodically exchange their best solutions
 * (migrants) through migration.
 *
 * <p>This package contains core infrastructure for island model:
 *
 * <ul>
 *   <li>{@link greycos.solver.core.impl.islandmodel.IslandModelPhase} - Coordinator phase that
 *       manages multiple islands
 *   <li>{@link greycos.solver.core.impl.islandmodel.IslandAgent} - Individual island agent running
 *       phases independently
 *   <li>{@link greycos.solver.core.impl.islandmodel.SharedGlobalState} - Thread-safe global best
 *       tracking
 *   <li>{@link greycos.solver.core.impl.islandmodel.BoundedChannel} - Agent-to-agent communication
 *       channel
 *   <li>{@link greycos.solver.core.impl.islandmodel.AgentUpdate} - Migration message structure
 *   <li>{@link greycos.solver.core.impl.islandmodel.AgentStatus} - Agent lifecycle status
 *   <li>{@link greycos.solver.core.impl.islandmodel.IslandModelConfig} - Configuration for island
 *       model
 * </ul>
 */
package greycos.solver.core.impl.islandmodel;
