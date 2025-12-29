package ai.greycos.solver.core.impl.islandmodel;

/**
 * Status of an island agent in the island model. Agents are initially ALIVE and become DEAD when
 * their phases complete.
 */
public enum AgentStatus {
  /** Agent is actively running its phases. */
  ALIVE,

  /**
   * Agent has completed all its phases but continues to participate in migration by forwarding
   * messages to maintain ring topology.
   */
  DEAD
}
