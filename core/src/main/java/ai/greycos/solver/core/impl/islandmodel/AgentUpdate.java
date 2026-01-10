package ai.greycos.solver.core.impl.islandmodel;

import java.util.BitSet;
import java.util.Objects;

/**
 * Message sent between island agents during migration. Contains agent's best solution (migrant) and
 * status vector of all agents.
 */
public class AgentUpdate<Solution_> {

  private final int agentId;
  private final Solution_ migrant;
  private final BitSet aliveBits;

  public AgentUpdate(int agentId, Solution_ migrant, BitSet aliveBits) {
    this.agentId = agentId;
    this.migrant = Objects.requireNonNull(migrant, "Migrant cannot be null");
    this.aliveBits =
        (BitSet) Objects.requireNonNull(aliveBits, "Alive bits cannot be null").clone();
  }

  public int getAgentId() {
    return agentId;
  }

  public Solution_ getMigrant() {
    return migrant;
  }

  public BitSet getAliveBits() {
    return (BitSet) aliveBits.clone();
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
        && Objects.equals(aliveBits, that.aliveBits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(agentId, migrant, aliveBits);
  }

  @Override
  public String toString() {
    return "AgentUpdate{"
        + "agentId="
        + agentId
        + ", migrant="
        + migrant
        + ", aliveBits="
        + aliveBits
        + '}';
  }
}
