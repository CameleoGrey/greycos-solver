package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link BoundedChannel}. */
class BoundedChannelTest {

  @Test
  void sendAndReceiveBasic() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    String message = "test message";
    channel.send(message);

    String received = channel.receive();
    assertEquals(message, received);
    assertTrue(channel.isEmpty());
  }

  @Test
  void sendBlocksWhenFull() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    // Fill the channel
    channel.send("message1");

    // Try to send another message - should block
    Thread senderThread =
        new Thread(
            () -> {
              try {
                channel.send("message2");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    senderThread.start();

    // Give sender thread time to block
    Thread.yield();
    assertTrue(senderThread.isAlive());

    // Receive the first message to unblock sender
    assertEquals("message1", channel.receive());

    // Wait for sender to complete
    senderThread.join(1000);
    assertFalse(senderThread.isAlive());

    // Now the second message should be in the channel
    assertEquals("message2", channel.receive());
  }

  @Test
  void receiveBlocksWhenEmpty() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    Thread receiverThread =
        new Thread(
            () -> {
              try {
                channel.receive();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    receiverThread.start();

    // Give receiver thread time to block
    Thread.yield();
    assertTrue(receiverThread.isAlive());

    // Send a message to unblock receiver
    channel.send("test");

    // Wait for receiver to complete
    receiverThread.join(1000);
    assertFalse(receiverThread.isAlive());
  }

  @Test
  void trySendReturnsFalseWhenFull() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    // Fill the channel
    assertTrue(channel.trySend("message1"));

    // Try to send another message - should return false
    assertFalse(channel.trySend("message2"));

    // Receive the first message
    assertEquals("message1", channel.receive());

    // Now trySend should succeed
    assertTrue(channel.trySend("message2"));
  }

  @Test
  void tryReceiveReturnsNullWhenEmpty() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    assertNull(channel.tryReceive());

    channel.send("message");
    assertEquals("message", channel.tryReceive());
    assertNull(channel.tryReceive());
  }

  @Test
  void tryReceiveWithTimeout() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    // Should return null immediately when empty
    assertNull(channel.tryReceive(100, TimeUnit.MILLISECONDS));

    // Send a message and receive it
    Thread senderThread =
        new Thread(
            () -> {
              try {
                Thread.sleep(50);
                channel.send("message");
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    senderThread.start();

    // Should receive the message within timeout
    String received = channel.tryReceive(1, TimeUnit.SECONDS);
    assertEquals("message", received);

    senderThread.join();
  }

  @Test
  void sizeAndCapacity() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(3);

    assertEquals(3, channel.capacity());
    assertEquals(0, channel.size());
    assertTrue(channel.isEmpty());

    channel.send("message1");
    assertEquals(1, channel.size());
    assertFalse(channel.isEmpty());

    channel.send("message2");
    channel.send("message3");
    assertEquals(3, channel.size());
    assertFalse(channel.isEmpty());

    channel.receive();
    assertEquals(2, channel.size());
  }

  @Test
  void multipleMessagesWithCapacityGreaterThanOne() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(3);

    channel.send("message1");
    channel.send("message2");
    channel.send("message3");

    assertEquals("message1", channel.receive());
    assertEquals("message2", channel.receive());
    assertEquals("message3", channel.receive());
    assertTrue(channel.isEmpty());
  }

  @Test
  void concurrentSendReceive() throws InterruptedException {
    BoundedChannel<Integer> channel = new BoundedChannel<>(10);

    int messageCount = 100;
    Thread senderThread =
        new Thread(
            () -> {
              try {
                for (int i = 0; i < messageCount; i++) {
                  channel.send(i);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    Thread receiverThread =
        new Thread(
            () -> {
              try {
                for (int i = 0; i < messageCount; i++) {
                  Integer received = channel.receive();
                  assertEquals(i, received);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    senderThread.start();
    receiverThread.start();

    senderThread.join(5000);
    receiverThread.join(5000);

    assertFalse(senderThread.isAlive());
    assertFalse(receiverThread.isAlive());
    assertTrue(channel.isEmpty());
  }
}
