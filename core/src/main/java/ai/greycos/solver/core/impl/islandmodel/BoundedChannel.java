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

  public BoundedChannel(int capacity) {
    this.queue = new ArrayBlockingQueue<>(capacity);
  }

  public void send(T message) throws InterruptedException {
    queue.put(message);
  }

  public boolean send(T message, long timeout, TimeUnit unit) throws InterruptedException {
    return queue.offer(message, timeout, unit);
  }

  public T receive() throws InterruptedException {
    return queue.take();
  }

  public boolean trySend(T message) {
    return queue.offer(message);
  }

  public T tryReceive() {
    return queue.poll();
  }

  public T tryReceive(long timeout, TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);
  }

  public int size() {
    return queue.size();
  }

  public int capacity() {
    return queue.remainingCapacity() + queue.size();
  }

  public boolean isEmpty() {
    return queue.isEmpty();
  }
}
