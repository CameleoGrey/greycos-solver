package ai.greycos.solver.core.impl.islandmodel;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A bounded channel for agent-to-agent communication in the island model. Wraps a BlockingQueue
 * with capacity 1 (as per GreyJack design).
 *
 * <p>Capacity of 1 ensures only the latest migration data is retained, preventing memory buildup
 * and ensuring fresh data exchange.
 *
 * @param <T> the type of messages sent through this channel
 */
public class BoundedChannel<T> {

  private final BlockingQueue<T> queue;

  /**
   * Creates a bounded channel with the specified capacity.
   *
   * @param capacity the maximum number of messages that can be held in the channel
   */
  public BoundedChannel(int capacity) {
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  /**
   * Sends a message through the channel, blocking if the channel is full.
   *
   * @param message the message to send, never null
   * @throws InterruptedException if interrupted while waiting
   */
  public void send(T message) throws InterruptedException {
    queue.put(message);
  }

  /**
   * Receives a message from the channel, blocking until a message is available.
   *
   * @return the received message, never null
   * @throws InterruptedException if interrupted while waiting
   */
  public T receive() throws InterruptedException {
    return queue.take();
  }

  /**
   * Attempts to send a message through the channel without blocking.
   *
   * @param message the message to send, never null
   * @return true if the message was sent, false if the channel is full
   */
  public boolean trySend(T message) {
    return queue.offer(message);
  }

  /**
   * Attempts to receive a message from the channel without blocking.
   *
   * @return the received message, or null if no message is available
   */
  public T tryReceive() {
    return queue.poll();
  }

  /**
   * Attempts to receive a message from the channel, waiting up to the specified time.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit of the timeout argument
   * @return the received message, or null if no message is available within the timeout
   * @throws InterruptedException if interrupted while waiting
   */
  public T tryReceive(long timeout, TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);
  }

  /**
   * Returns the number of messages currently in the channel.
   *
   * @return the number of messages in the channel
   */
  public int size() {
    return queue.size();
  }

  /**
   * Returns the capacity of the channel.
   *
   * @return the channel capacity
   */
  public int capacity() {
    return queue.remainingCapacity() + queue.size();
  }

  /**
   * Returns whether the channel is empty.
   *
   * @return true if the channel has no messages
   */
  public boolean isEmpty() {
    return queue.isEmpty();
  }
}
