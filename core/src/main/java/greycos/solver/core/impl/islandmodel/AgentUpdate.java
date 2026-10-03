package greycos.solver.core.impl.islandmodel;

import java.util.BitSet;
import java.util.Objects;

import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.score.director.InnerScore;

/**
 * Message sent between island agents during migration. Contains agent's best solution (migrant) and
 * status vector of all agents.
 */
public class AgentUpdate<Solution_> {

  private final int agentId;
  private final Solution_ migrant;
  private final InnerScore<?> migrantScore;
  private final BitSet aliveBits;
  private final AcceptorMigrationState acceptorState;

  public AgentUpdate(int agentId, Solution_ migrant, InnerScore<?> migrantScore, BitSet aliveBits) {
    this(agentId, migrant, migrantScore, aliveBits, AcceptorMigrationState.Empty.INSTANCE);
  }

  public AgentUpdate(
      int agentId,
      Solution_ migrant,
      InnerScore<?> migrantScore,
      BitSet aliveBits,
      AcceptorMigrationState acceptorState) {
    this.agentId = agentId;
    this.migrant = Objects.requireNonNull(migrant, "Migrant cannot be null");
    this.migrantScore = Objects.requireNonNull(migrantScore, "Migrant score cannot be null");
    this.aliveBits =
        (BitSet) Objects.requireNonNull(aliveBits, "Alive bits cannot be null").clone();
    this.acceptorState = Objects.requireNonNull(acceptorState);
  }

  public AcceptorMigrationState getAcceptorState() {
    return acceptorState;
  }

  public int getAgentId() {
    return agentId;
  }

  public Solution_ getMigrant() {
    return migrant;
  }

  public InnerScore<?> getMigrantScore() {
    return migrantScore;
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
        && Objects.equals(migrantScore, that.migrantScore)
        && Objects.equals(acceptorState, that.acceptorState)
        && Objects.equals(aliveBits, that.aliveBits);
  }

  @Override
  public int hashCode() {
    return Objects.hash(agentId, migrant, migrantScore, aliveBits, acceptorState);
  }

  @Override
  public String toString() {
    return "AgentUpdate{"
        + "agentId="
        + agentId
        + ", migrant="
        + migrant
        + ", migrantScore="
        + migrantScore
        + ", aliveBits="
        + aliveBits
        + '}';
  }
}
