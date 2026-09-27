package greycos.solver.core.impl.islandmodel;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded channel for agent-to-agent communication in island model. Closing discards migrations and
 * wakes both receivers and senders when no island can use further migrations.
 */
public class BoundedChannel<T> {

  private final ArrayDeque<T> queue = new ArrayDeque<>();
  private final int capacity;
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition notEmpty = lock.newCondition();
  private final Condition notFull = lock.newCondition();
  private boolean closed;

  public BoundedChannel(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("Channel capacity (" + capacity + ") must be positive.");
    }
    this.capacity = capacity;
  }

  public void send(T message) throws InterruptedException {
    Objects.requireNonNull(message);
    lock.lockInterruptibly();
    try {
      while (!closed && queue.size() == capacity) {
        notFull.await();
      }
      if (closed) {
        throw new IllegalStateException("Cannot send a migration to a closed island channel.");
      }
      queue.addLast(message);
      notEmpty.signal();
    } finally {
      lock.unlock();
    }
  }

  public boolean send(T message, long timeout, TimeUnit unit) throws InterruptedException {
    Objects.requireNonNull(message);
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      while (!closed && queue.size() == capacity) {
        if (nanos <= 0L) {
          return false;
        }
        nanos = notFull.awaitNanos(nanos);
      }
      if (closed) {
        return false;
      }
      queue.addLast(message);
      notEmpty.signal();
      return true;
    } finally {
      lock.unlock();
    }
  }

  public T receive() throws InterruptedException {
    lock.lockInterruptibly();
    try {
      while (!closed && queue.isEmpty()) {
        notEmpty.await();
      }
      return removeFirst();
    } finally {
      lock.unlock();
    }
  }

  public boolean trySend(T message) {
    Objects.requireNonNull(message);
    lock.lock();
    try {
      if (closed || queue.size() == capacity) {
        return false;
      }
      queue.addLast(message);
      notEmpty.signal();
      return true;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Latest-value send for bounded channels. If full, evicts one stale message. Returns false after
   * closure.
   */
  public boolean replace(T message) {
    Objects.requireNonNull(message);
    lock.lock();
    try {
      if (closed) {
        return false;
      }
      if (queue.size() == capacity) {
        queue.removeFirst();
      }
      queue.addLast(message);
      notEmpty.signal();
      return true;
    } finally {
      lock.unlock();
    }
  }

  public T tryReceive() {
    lock.lock();
    try {
      return removeFirst();
    } finally {
      lock.unlock();
    }
  }

  public T tryReceive(long timeout, TimeUnit unit) throws InterruptedException {
    long nanos = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
      while (!closed && queue.isEmpty()) {
        if (nanos <= 0L) {
          return null;
        }
        nanos = notEmpty.awaitNanos(nanos);
      }
      return removeFirst();
    } finally {
      lock.unlock();
    }
  }

  private T removeFirst() {
    var message = queue.pollFirst();
    if (message != null) {
      notFull.signal();
    }
    return message;
  }

  public void close() {
    lock.lock();
    try {
      closed = true;
      queue.clear();
      notEmpty.signalAll();
      notFull.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public int size() {
    lock.lock();
    try {
      return queue.size();
    } finally {
      lock.unlock();
    }
  }

  public int capacity() {
    return capacity;
  }

  public boolean isEmpty() {
    return size() == 0;
  }
}
