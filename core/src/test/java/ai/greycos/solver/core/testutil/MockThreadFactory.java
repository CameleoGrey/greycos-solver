package ai.greycos.solver.core.testutil;

import java.util.concurrent.ThreadFactory;

/**
 * Mock {@link ThreadFactory} implementation for testing purposes. This class tracks whether it has
 * been called to verify that custom thread factories are properly used during solver execution.
 */
public class MockThreadFactory implements ThreadFactory {

  private static boolean called;

  public static boolean hasBeenCalled() {
    return called;
  }

  public static void reset() {
    called = false;
  }

  public MockThreadFactory() {
    called = false;
  }

  @Override
  public Thread newThread(Runnable r) {
    called = true;
    Thread newThread = new Thread(r, "mock-testing-thread");
    newThread.setDaemon(false);
    return newThread;
  }
}
