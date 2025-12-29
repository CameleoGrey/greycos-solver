package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AgentUpdate}. */
class AgentUpdateTest {

  @Test
  void constructorAndGetters() {
    String migrant = "test solution";
    List<AgentStatus> statusVector =
        List.of(AgentStatus.ALIVE, AgentStatus.ALIVE, AgentStatus.DEAD);
    int agentId = 2;

    AgentUpdate<String> update = new AgentUpdate<>(agentId, migrant, statusVector);

    assertEquals(agentId, update.getAgentId());
    assertEquals(migrant, update.getMigrant());
    assertEquals(statusVector, update.getStatusVector());
  }

  @Test
  void constructorWithNullMigrantThrowsException() {
    List<AgentStatus> statusVector = new ArrayList<>();

    assertThrows(
        NullPointerException.class,
        () -> {
          new AgentUpdate<>(0, null, statusVector);
        });
  }

  @Test
  void constructorWithNullStatusVectorThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new AgentUpdate<>(0, "solution", null);
        });
  }

  @Test
  void statusVectorIsImmutable() {
    List<AgentStatus> originalVector = new ArrayList<>();
    originalVector.add(AgentStatus.ALIVE);

    AgentUpdate<String> update = new AgentUpdate<>(0, "solution", originalVector);

    // Modify original vector
    originalVector.add(AgentStatus.DEAD);

    // Update's status vector should not be affected
    assertNotEquals(originalVector, update.getStatusVector());
  }

  @Test
  void equalityBasedOnContent() {
    List<AgentStatus> statusVector = List.of(AgentStatus.ALIVE, AgentStatus.ALIVE);

    AgentUpdate<String> update1 = new AgentUpdate<>(0, "solution", statusVector);
    AgentUpdate<String> update2 = new AgentUpdate<>(0, "solution", statusVector);
    AgentUpdate<String> update3 = new AgentUpdate<>(1, "solution", statusVector);

    assertEquals(update1, update2);
    assertNotEquals(update1, update3);
  }
}
