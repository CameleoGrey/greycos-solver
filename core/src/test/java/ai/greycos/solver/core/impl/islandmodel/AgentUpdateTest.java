package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.BitSet;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link AgentUpdate}. */
class AgentUpdateTest {

  @Test
  void constructorAndGetters() {
    String migrant = "test solution";
    BitSet aliveBits = new BitSet();
    aliveBits.set(0);
    aliveBits.set(1);
    int agentId = 2;

    AgentUpdate<String> update = new AgentUpdate<>(agentId, migrant, aliveBits);

    assertEquals(agentId, update.getAgentId());
    assertEquals(migrant, update.getMigrant());
    assertEquals(aliveBits, update.getAliveBits());
  }

  @Test
  void constructorWithNullMigrantThrowsException() {
    BitSet aliveBits = new BitSet();

    assertThrows(
        NullPointerException.class,
        () -> {
          new AgentUpdate<>(0, null, aliveBits);
        });
  }

  @Test
  void constructorWithNullAliveBitsThrowsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new AgentUpdate<>(0, "solution", null);
        });
  }

  @Test
  void aliveBitsIsImmutable() {
    BitSet originalBits = new BitSet();
    originalBits.set(0);

    AgentUpdate<String> update = new AgentUpdate<>(0, "solution", originalBits);

    // Modify original bits
    originalBits.clear(0);

    // Update's bits should not be affected
    assertNotEquals(originalBits, update.getAliveBits());
  }

  @Test
  void equalityBasedOnContent() {
    BitSet aliveBits = new BitSet();
    aliveBits.set(0);
    aliveBits.set(1);

    AgentUpdate<String> update1 = new AgentUpdate<>(0, "solution", aliveBits);
    AgentUpdate<String> update2 = new AgentUpdate<>(0, "solution", aliveBits);
    AgentUpdate<String> update3 = new AgentUpdate<>(1, "solution", aliveBits);

    assertEquals(update1, update2);
    assertNotEquals(update1, update3);
  }
}
