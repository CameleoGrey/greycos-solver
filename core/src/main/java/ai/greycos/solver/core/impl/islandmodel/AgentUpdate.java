package ai.greycos.solver.core.impl.islandmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;

/**
 * Message sent between island agents during migration in the island model. Contains the agent's
 * best solution (migrant) and status vector of all agents.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class AgentUpdate<Solution_> {

  private final int agentId;
  private final Solution_ migrant;
  private final List<AgentStatus> statusVector;

  /**
   * Creates an agent update message.
   *
   * @param agentId the ID of the agent sending this update
   * @param migrant the best solution from this agent (the migrant), never null
   * @param statusVector the status of all agents, never null
   */
  public AgentUpdate(int agentId, Solution_ migrant, List<AgentStatus> statusVector) {
    this.agentId = agentId;
    this.migrant = Objects.requireNonNull(migrant, "Migrant cannot be null");
    this.statusVector =
        new ArrayList<>(Objects.requireNonNull(statusVector, "Status vector cannot be null"));
  }

  /**
   * Returns the ID of the agent that sent this update.
   *
   * @return the agent ID
   */
  public int getAgentId() {
    return agentId;
  }

  /**
   * Returns the migrant solution (best solution from sending agent).
   *
   * @return the migrant solution, never null
   */
  public Solution_ getMigrant() {
    return migrant;
  }

  /**
   * Returns the status vector containing the status of all agents.
   *
   * @return an unmodifiable list of agent statuses, never null
   */
  public List<AgentStatus> getStatusVector() {
    return Collections.unmodifiableList(statusVector);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AgentUpdate<?> that = (AgentUpdate<?>) o;
    return agentId == that.agentId
        && Objects.equals(migrant, that.migrant)
        && Objects.equals(statusVector, that.statusVector);
  }

  @Override
  public int hashCode() {
    return Objects.hash(agentId, migrant, statusVector);
  }

  @Override
  public String toString() {
    return "AgentUpdate{"
        + "agentId="
        + agentId
        + ", migrant="
        + migrant
        + ", statusVector="
        + statusVector
        + '}';
  }
}
